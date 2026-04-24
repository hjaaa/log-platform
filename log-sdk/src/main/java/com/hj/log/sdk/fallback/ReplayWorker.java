package com.hj.log.sdk.fallback;

import com.hj.log.sdk.model.LogEvent;
import com.hj.log.sdk.transport.RetryingSender;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 定时扫描 fallback 目录，把最早的文件重新 POST 到服务端（detailed-design §6.2 / §9.3）。
 *
 * <p>竞争规避策略："rename-before-send"：
 * <pre>
 *   log-sdk-YYYYMMDD-HH.ndjson   ←  FlushWorker 追加写入
 *           │ atomic rename
 *           ▼
 *   log-sdk-YYYYMMDD-HH.ndjson.replay ← ReplayWorker 读取 → HTTP POST → 成功 delete
 * </pre>
 *
 * <p>为什么需要：若直接在原文件上 read→send→delete，send 期间（最长几十秒重试）FallbackWriter
 * 可能继续追加新事件；delete 会把这些未 replay 的新事件一并删除，造成日志永久丢失。rename 后
 * writer 的下一次 write 会以 CREATE 方式打开原文件名，落到新文件；旧 {@code .replay} 文件孤立
 * 属于 replay 线程，删除安全。
 *
 * <p>语义约定：
 * <ul>
 *   <li>锁未取得（偶发，writer 与 reader 竞争同一 .replay 文件）→ 跳过，下轮再试</li>
 *   <li>文件全部批次 SENT 或 DROPPED（4xx）→ 删除</li>
 *   <li>任一批次 EXHAUSTED / INTERRUPTED → 保留 .replay 文件，下轮再试</li>
 *   <li>rename 失败（NoSuchFileException：文件被其他进程移走）→ 跳过</li>
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
            Path replayFile = claimForReplay(f);
            if (replayFile == null) {
                // rename 未成功（文件被移走）或其他错误——跳过本轮
                continue;
            }
            if (!processReplayFile(replayFile)) {
                // 任一批次失败 → 保留 .replay 下轮重试，**不再继续处理后续文件**（保持字典序顺序消费）
                return;
            }
        }
    }

    /**
     * 把新鲜 {@code *.ndjson} 原子改名为 {@code *.ndjson.replay} 以独占该批事件。
     *
     * <p>若入参已是 .replay 文件（来自上一轮未完成的 replay 或本进程重启前的残留），直接返回即可，
     * 不再 rename。
     */
    private Path claimForReplay(Path f) {
        String name = f.getFileName().toString();
        if (name.endsWith(FallbackReader.REPLAY_SUFFIX)) {
            return f;
        }
        Path target = f.resolveSibling(name + FallbackReader.REPLAY_SUFFIX);
        try {
            return Files.move(f, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                return Files.move(f, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ioe) {
                log.warn(
                        "[log-sdk] non-atomic rename fallback for replay failed: {}",
                        f.getFileName(),
                        ioe);
                return null;
            }
        } catch (NoSuchFileException e) {
            // 文件已不存在（可能被其他进程移走）
            return null;
        } catch (IOException e) {
            log.warn("[log-sdk] rename fallback for replay failed: {}", f.getFileName(), e);
            return null;
        }
    }

    /**
     * 处理一个 {@code .replay} 文件：读、分批发送、成功则删除。
     *
     * @return {@code true} 文件已处理完毕（送达或空文件已清理），可继续处理下一个文件；
     *         {@code false} 批次未完成，需保留文件到下一轮
     */
    private boolean processReplayFile(Path replayFile) {
        List<LogEvent> events = reader.readAll(replayFile);
        if (events == null) {
            // 锁未取得或 IO 故障——跳过，下一轮 listPendingFiles 仍会拾起
            return true;
        }
        if (events.isEmpty()) {
            // 合法的空文件（例如 buildLine 全部序列化失败而留下的壳）：直接清理
            safeDelete(replayFile);
            return true;
        }
        if (trySendAll(events)) {
            safeDelete(replayFile);
            log.info(
                    "[log-sdk] replay ok, removed fallback file: {} ({} events)",
                    replayFile.getFileName(),
                    events.size());
            return true;
        }
        return false;
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
