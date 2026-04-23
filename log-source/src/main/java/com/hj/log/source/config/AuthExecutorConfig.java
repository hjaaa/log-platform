package com.hj.log.source.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AuthFilter 异步执行器（写 last_used_at）。
 *
 * <p>线程池规模刻意小（core=2 / max=2 / queue=200）：业务侧请求是 fire-and-forget，
 * 真正的 UPDATE 频率受 Caffeine 节流约束，2 个线程足以吞下偶尔的并发尖峰；
 * 队列满时静默丢弃（{@link DiscardPolicy} 由 ThreadPoolTaskExecutor 默认 abort 策略接管），
 * 避免拖死业务线程。
 */
@Configuration
public class AuthExecutorConfig {

    public static final String AUTH_EXECUTOR_BEAN = "authExecutor";

    @Bean(AUTH_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor authExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(2);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("auth-async-");
        exec.setDaemon(true);
        // 队列满 / 池关闭时丢弃任务并打 WARN，业务线程不阻塞
        exec.setRejectedExecutionHandler((r, executor) -> {
            // 静默丢弃即可；下个 60s TTL 周期会有新机会触发 UPDATE
        });
        exec.initialize();
        return exec;
    }

    /** 占位注释，便于读源码的人理解策略选择；运行期不引用。 */
    private interface DiscardPolicy {
    }
}
