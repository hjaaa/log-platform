package com.hj.log.sdk.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.sdk.config.LogSdkProperties;
import com.hj.log.sdk.model.LogEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单次 HTTP POST 的薄封装：负责序列化 + 发送 + 分类响应码，不含重试。
 *
 * <p>重试策略（指数退避 / 耗尽后 fallback）由 {@link com.hj.log.sdk.transport.RetryingSender}
 * 组合本类实现。
 */
public class HttpTransport {

    private static final Logger log = LoggerFactory.getLogger(HttpTransport.class);

    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    public HttpTransport(LogSdkProperties props, ObjectMapper mapper) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(props.httpConnectTimeoutMs()))
                        .build(),
                URI.create(props.endpoint()),
                props.apiKey(),
                Duration.ofMillis(props.httpRequestTimeoutMs()),
                mapper);
    }

    HttpTransport(
            HttpClient client,
            URI endpoint,
            String apiKey,
            Duration requestTimeout,
            ObjectMapper mapper) {
        this.client = client;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.requestTimeout = requestTimeout;
        this.mapper = mapper;
    }

    /** 序列化并发送一批；单次调用不重试。 */
    public TransportOutcome send(List<LogEvent> events) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(Map.of("events", events));
        } catch (JsonProcessingException e) {
            log.error("[log-sdk] serialize batch failed (size={}), dropping", events.size(), e);
            return TransportOutcome.CLIENT_ERROR;
        }
        HttpRequest req =
                HttpRequest.newBuilder(endpoint)
                        .timeout(requestTimeout)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
        try {
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                return TransportOutcome.SUCCESS;
            }
            if (code >= 400 && code < 500) {
                log.warn(
                        "[log-sdk] server rejected batch size={} with HTTP {}; dropping",
                        events.size(),
                        code);
                return TransportOutcome.CLIENT_ERROR;
            }
            return TransportOutcome.TRANSIENT_ERROR;
        } catch (IOException e) {
            // 不打堆栈避免高频故障期间日志洪水；由 RetryingSender 在耗尽时升级决策
            log.warn(
                    "[log-sdk] http send failed (batch={}): {}: {}",
                    events.size(),
                    e.getClass().getSimpleName(),
                    e.getMessage());
            return TransportOutcome.TRANSIENT_ERROR;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[log-sdk] http send interrupted (batch={})", events.size());
            return TransportOutcome.TRANSIENT_ERROR;
        }
    }
}
