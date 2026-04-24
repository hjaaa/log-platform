package com.hj.log.sdk.fallback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.sdk.model.LogEvent;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 读取 fallback NDJSON 文件（detailed-design §6.2 replay 路径）。
 *
 * <p>并发：{@link FileChannel#tryLock}。拿不到锁即视作他人正在写入，调用方应跳过本轮。
 */
public class FallbackReader {

    private static final Logger log = LoggerFactory.getLogger(FallbackReader.class);
    private static final String FILE_SUFFIX = ".ndjson";
    /** "正在 replay 中"标记后缀——文件已被 ReplayWorker 原子 rename，writer 不会再追加。 */
    static final String REPLAY_SUFFIX = ".replay";

    private final Path dir;
    private final ObjectMapper mapper;

    public FallbackReader(Path dir, ObjectMapper mapper) {
        this.dir = dir;
        this.mapper = mapper;
    }

    /**
     * 列出目录下待 replay 的文件，按文件名字典序（最早在前）。
     *
     * <p>同时包含两种状态的文件：
     * <ul>
     *   <li>{@code *.ndjson} — 新鲜写入的 fallback，尚未被 replay
     *   <li>{@code *.ndjson.replay} — 已被 ReplayWorker 原子 rename，处于"读取+发送中"状态；
     *       可能上一轮失败残留，需继续处理</li>
     * </ul>
     */
    public List<Path> listPendingFiles() throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> isFallbackFile(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(files::add);
        }
        return files;
    }

    private static boolean isFallbackFile(String name) {
        return name.endsWith(FILE_SUFFIX) || name.endsWith(FILE_SUFFIX + REPLAY_SUFFIX);
    }

    /**
     * 加锁读取文件全部事件。返回 {@code null} 表示锁未取得（他人占用）；调用方应跳过。
     *
     * <p>锁在本方法返回前释放——后续 replay 发送、成功删文件等操作不再持有锁。这样即便某批发送
     * 很慢，也不会阻塞 writer 写新事件。
     */
    public List<LogEvent> readAll(Path file) {
        try (FileChannel ch =
                FileChannel.open(
                        file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileLock lock;
            try {
                lock = ch.tryLock();
            } catch (java.nio.channels.OverlappingFileLockException e) {
                return null;
            }
            if (lock == null) {
                return null;
            }
            try {
                return parseLines(file);
            } finally {
                lock.release();
            }
        } catch (IOException e) {
            // 返回 null 而非空列表——语义与"锁未取得"对齐：本轮跳过，留给下一个 replay 周期。
            // 若返回 List.of()，ReplayWorker 会把 IO 故障文件当作空文件 safeDelete 直接删除，导致数据永久丢失。
            log.error("[log-sdk] open fallback file failed, will retry next cycle: {}", file, e);
            return null;
        }
    }

    private List<LogEvent> parseLines(Path file) {
        List<LogEvent> events = new ArrayList<>();
        int lineNo = 0;
        try {
            for (String line : Files.readAllLines(file)) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    NdjsonLine parsed = mapper.readValue(line, NdjsonLine.class);
                    if (parsed.event() != null) {
                        events.add(parsed.event());
                    }
                } catch (Exception ex) {
                    log.error(
                            "[log-sdk] parse fallback line failed at {}:{} (skipping)",
                            file.getFileName(),
                            lineNo,
                            ex);
                }
            }
        } catch (IOException e) {
            log.error("[log-sdk] read fallback file failed: {}", file, e);
        }
        return events;
    }
}
