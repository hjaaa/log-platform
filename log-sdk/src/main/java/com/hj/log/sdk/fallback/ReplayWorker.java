package com.hj.log.sdk.fallback;

import com.hj.log.sdk.model.LogEvent;
import com.hj.log.sdk.transport.RetryingSender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 定时扫描 fallback 目录，把最早的文件重新 POST 到服务端（detailed-design §6.2 / §9.3）。
 *
 * <p>语义约定：
 * <ul>
 *   <li>锁未取得（writer 正在写）→ 跳过，下轮再试</li>
 *   <li>文件全部批次 SENT 或 DROPPED（4xx）→ 删除</li>
 *   <li>任一批次 EXHAUSTED / INTERRUPTED → 保留文件，下轮再试</li>
 * </ul>
 */
public class ReplayWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplayWorker.class);

    private final FallbackReader reader;
    private final RetryingSender sender;
    private final long intervalMs;
    private final int batchSize;

    private volatile boolean running = true;

    public ReplayWorker(
            FallbackReader reader, RetryingSender sender, long intervalMs, int batchSize) {
        this.reader = reader;
        this.sender = sender;
        this.intervalMs = intervalMs;
        this.batchSize = batchSize;
    }

    @Override
    public void run() {
        log.info("[log-sdk] replay worker started (intervalMs={})", intervalMs);
        while (running) {
            try {
                replayOnce();
            } catch (Throwable t) {
                log.error("[log-sdk] replay cycle failed", t);
            }
            if (!running) {
                break;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("[log-sdk] replay worker stopped");
    }

    public void replayOnce() throws IOException {
        List<Path> files = reader.listPendingFiles();
        for (Path f : files) {
            if (!running) {
                return;
            }
            List<LogEvent> events = reader.readAll(f);
            if (events == null) {
                // 拿不到锁，跳过
                continue;
            }
            if (events.isEmpty()) {
                // 空文件：直接清理
                safeDelete(f);
                continue;
            }
            if (trySendAll(events)) {
                safeDelete(f);
                log.info(
                        "[log-sdk] replay ok, removed fallback file: {} ({} events)",
                        f.getFileName(),
                        events.size());
            } else {
                // 保留文件，下轮重试
                return;
            }
        }
    }

    private boolean trySendAll(List<LogEvent> events) {
        for (int i = 0; i < events.size(); i += batchSize) {
            List<LogEvent> batch = events.subList(i, Math.min(i + batchSize, events.size()));
            RetryingSender.Result r = sender.send(batch);
            switch (r) {
                case SENT, DROPPED -> {
                    // DROPPED：replay 时 4xx 说明这批无法被服务端接受（可能事件格式错），
                    // 继续往下走，允许整个文件被删除（避免永远堆积）。
                }
                case EXHAUSTED, INTERRUPTED -> {
                    return false;
                }
            }
        }
        return true;
    }

    private void safeDelete(Path f) {
        try {
            Files.deleteIfExists(f);
        } catch (IOException e) {
            log.error("[log-sdk] delete replayed fallback file failed: {}", f, e);
        }
    }

    public void stop() {
        running = false;
    }
}
