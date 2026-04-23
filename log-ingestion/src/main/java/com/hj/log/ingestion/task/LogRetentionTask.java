package com.hj.log.ingestion.task;

import com.hj.log.ingestion.config.RetentionProperties;
import com.hj.log.ingestion.mapper.LogEntryMapper;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 保留期清理任务（detailed-design §1.4）。
 *
 * <p>每次触发循环：分批 DELETE 至本轮无更多旧数据；批与批之间 sleep 避免长事务。
 *
 * <p>由 {@link Scheduled} 触发；为方便单测把核心循环抽到 {@link #runOnce()} 公共方法。
 */
@Component
public class LogRetentionTask {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionTask.class);

    private final LogEntryMapper mapper;
    private final RetentionProperties props;

    public LogRetentionTask(LogEntryMapper mapper, RetentionProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    @Scheduled(cron = "${log.platform.retention.cron:0 17 * * * ?}")
    public void scheduledRun() {
        runOnce();
    }

    /**
     * 执行一轮分批清理；返回总删除行数。
     *
     * @throws InterruptedException 若线程在 sleep 中被中断（任务调度器会重试）
     */
    public int runOnce() {
        int batchSize = props.getBatchSize();
        Instant cutoff = Instant.now().minus(Duration.ofDays(props.getDays()));
        int total = 0;
        int sleepCount = 0;
        while (true) {
            int affected;
            try {
                affected = mapper.deleteOldest(cutoff, batchSize);
            } catch (RuntimeException ex) {
                log.warn("[retention] DELETE 失败 cutoff={} totalSoFar={} cause={}", cutoff, total, ex.toString());
                break;
            }
            total += affected;
            if (affected < batchSize) {
                break;
            }
            // 还有数据，先暂停再继续
            try {
                if (props.getBatchPauseMs() > 0) {
                    Thread.sleep(props.getBatchPauseMs());
                    sleepCount++;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[retention] 中断退出 totalSoFar={}", total);
                break;
            }
        }
        log.info("[retention] deleted={} days={} sleepCount={}", total, props.getDays(), sleepCount);
        return total;
    }
}
