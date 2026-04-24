package com.hj.log.sdk.fallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 磁盘上限守护（detailed-design §6.7）。写 fallback 前调用；超过 {@code maxDiskMb} 按
 * 字典序删最旧文件（文件名包含时间），直至低于阈值。
 *
 * <p>只扫目录顶层文件；不递归子目录——SDK 自身不创建子目录。
 */
public class DiskGuard {

    private static final Logger log = LoggerFactory.getLogger(DiskGuard.class);
    private static final String FILE_SUFFIX = ".ndjson";

    private final Path dir;
    private final long maxBytes;

    public DiskGuard(Path dir, long maxDiskMb) {
        this.dir = dir;
        this.maxBytes = maxDiskMb * 1024L * 1024L;
    }

    /** 若目录总大小超阈值，按字典序删最旧文件至 ≤ 阈值。 */
    public void ensureUnderLimit() throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        List<Path> files = listNdjsonFilesSorted();
        long total = totalBytes(files);
        if (total <= maxBytes) {
            return;
        }
        for (Path f : files) {
            if (total <= maxBytes) {
                break;
            }
            long size;
            try {
                size = Files.size(f);
            } catch (IOException e) {
                continue;
            }
            try {
                Files.deleteIfExists(f);
                total -= size;
                log.warn(
                        "[log-sdk] fallback dir exceeds {} bytes, evicted oldest: {}",
                        maxBytes,
                        f.getFileName());
            } catch (IOException e) {
                log.error("[log-sdk] evict fallback file failed: {}", f, e);
            }
        }
    }

    private List<Path> listNdjsonFilesSorted() throws IOException {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(FILE_SUFFIX))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(files::add);
        }
        return files;
    }

    private long totalBytes(List<Path> files) {
        long sum = 0L;
        for (Path p : files) {
            try {
                sum += Files.size(p);
            } catch (IOException ignore) {
            }
        }
        return sum;
    }
}
