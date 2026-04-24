package com.hj.log.sdk.fallback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.sdk.model.LogEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 失败降级本地文件写入（detailed-design §6.4 / §6.7）。
 *
 * <p>单行 NDJSON：{@code {"event": <LogEvent JSON>, "ts": "<ISO8601>"}}。文件名按小时滚动：
 * {@code log-sdk-yyyyMMdd-HH.ndjson}；时区 UTC（与服务端 {@code server_ts} 一致）。
 *
 * <p>并发：{@link FileChannel#tryLock()} 获取独占锁；在单 JVM 单 FlushWorker 场景下基本不冲突，
 * 多 JVM 共享目录时可防止 replay reader 与写入错位。
 *
 * <p>磁盘守护：每次写入前调用 {@link DiskGuard#ensureUnderLimit} 按字典序删最旧直到总量 ≤ 阈值。
 */
public class FallbackWriter {

    private static final Logger log = LoggerFactory.getLogger(FallbackWriter.class);
    private static final DateTimeFormatter HOUR_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH").withZone(ZoneId.of("UTC"));
    private static final String FILE_PREFIX = "log-sdk-";
    private static final String FILE_SUFFIX = ".ndjson";

    private final Path dir;
    private final ObjectMapper mapper;
    private final DiskGuard diskGuard;
    private final Object writeMutex = new Object();

    public FallbackWriter(Path dir, long maxDiskMb, ObjectMapper mapper) {
        this.dir = dir;
        this.mapper = mapper;
        this.diskGuard = new DiskGuard(dir, maxDiskMb);
    }

    /** 把一批事件写入当前小时文件；目录不存在会自动创建。返回成功写入的条数。 */
    public int write(List<LogEvent> events) {
        if (events.isEmpty()) {
            return 0;
        }
        synchronized (writeMutex) {
            try {
                Files.createDirectories(dir);
                diskGuard.ensureUnderLimit();
            } catch (IOException e) {
                log.error("[log-sdk] prepare fallback dir failed: {}", dir, e);
                return 0;
            }
            Path target = resolveCurrentHourFile();
            return writeToFile(target, events);
        }
    }

    Path resolveCurrentHourFile() {
        return resolveHourFile(Instant.now());
    }

    Path resolveHourFile(Instant at) {
        String hour = HOUR_FMT.format(ZonedDateTime.ofInstant(at, ZoneId.of("UTC")));
        return dir.resolve(FILE_PREFIX + hour + FILE_SUFFIX);
    }

    private int writeToFile(Path target, List<LogEvent> events) {
        try (FileChannel ch =
                FileChannel.open(
                        target,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND)) {
            FileLock lock = acquireLockWithRetry(ch);
            if (lock == null) {
                log.error(
                        "[log-sdk] failed to lock fallback file after retries, losing {} events: {}",
                        events.size(),
                        target.getFileName());
                return 0;
            }
            try {
                int written = 0;
                for (LogEvent e : events) {
                    byte[] line = buildLine(e);
                    if (line != null) {
                        ch.write(ByteBuffer.wrap(line));
                        written++;
                    }
                }
                ch.force(false);
                if (written > 0) {
                    log.info(
                            "[log-sdk] fallback wrote {} events to {}",
                            written,
                            target.getFileName());
                }
                return written;
            } finally {
                lock.release();
            }
        } catch (IOException e) {
            log.error("[log-sdk] write fallback file failed: {}", target, e);
            return 0;
        }
    }

    private FileLock acquireLockWithRetry(FileChannel ch) throws IOException {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                FileLock lock = ch.tryLock();
                if (lock != null) {
                    return lock;
                }
            } catch (java.nio.channels.OverlappingFileLockException ignore) {
                // 同一 JVM 重叠持锁——重试一会儿
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private byte[] buildLine(LogEvent event) {
        // LinkedHashMap 固定字段顺序，方便人工 tail/vim 阅读
        Map<String, Object> line = new LinkedHashMap<>(2);
        line.put("event", event);
        line.put("ts", Instant.now().toString());
        try {
            String json = mapper.writeValueAsString(line);
            return (json + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (JsonProcessingException ex) {
            // 丢这一条，保其他条不受影响
            log.error("[log-sdk] serialize fallback line failed, dropping", ex);
            return null;
        }
    }

    /** 测试钩子：返回目录下全部 NDJSON 文件的简单统计。 */
    public Map<String, Long> inspect() throws IOException {
        Map<String, Long> out = new HashMap<>();
        if (!Files.exists(dir)) {
            return out;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(FILE_SUFFIX))
                    .forEach(
                            p -> {
                                try {
                                    out.put(p.getFileName().toString(), Files.size(p));
                                } catch (IOException ignore) {
                                }
                            });
        }
        return out;
    }
}
