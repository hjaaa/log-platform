package com.hj.log.query.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.common.base.ResponseResult;
import com.hj.log.common.enums.LogKind;
import com.hj.log.common.enums.LogLevel;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import com.hj.log.query.dto.LogSearchCriteria;
import com.hj.log.query.dto.LogView;
import com.hj.log.query.dto.Page;
import com.hj.log.query.dto.TracePage;
import com.hj.log.query.service.LogQueryService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志查询 Controller。三端点：
 * <ul>
 *   <li>{@code GET /api/v1/logs/search}</li>
 *   <li>{@code GET /api/v1/logs/{id}}</li>
 *   <li>{@code GET /api/v1/logs/trace/{traceId}}</li>
 * </ul>
 *
 * <p>{@code from} / {@code to} 用 String 接收并由本类解析，避开 standalone MockMvc
 * 缺少 {@code Instant} 转换器的麻烦；同时 {@code level=ERROR,WARN} 走 Spring 默认 CSV→List 转换。
 */
@RestController
@RequestMapping("/api/v1/logs")
public class LogQueryController {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private final LogQueryService service;
    private final ObjectMapper objectMapper;

    public LogQueryController(LogQueryService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/search")
    public ResponseResult<?> search(
            @RequestParam(required = false) String appCode,
            @RequestParam(value = "level", required = false) List<LogLevel> levels,
            @RequestParam(required = false) LogKind kind,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) List<String> fields) {

        LogSearchCriteria c = new LogSearchCriteria();
        c.setAppCode(appCode);
        c.setLevels(levels);
        c.setKind(kind);
        c.setFrom(parseInstant(from));
        c.setTo(parseInstant(to));
        c.setKeyword(keyword);
        c.setTraceId(traceId);
        c.setRequestId(requestId);
        c.setCursor(cursor);
        c.setPageSize(pageSize);
        c.setFields(fields);

        Page<LogView> page = service.search(c);
        if (fields == null || fields.isEmpty()) {
            return ResponseResult.ok(page);
        }
        return ResponseResult.ok(cropPage(page, fields));
    }

    @GetMapping("/{id}")
    public ResponseResult<?> getById(
            @PathVariable("id") long id,
            @RequestParam(required = false) List<String> fields) {
        LogView v = service.getById(id);
        if (fields == null || fields.isEmpty()) {
            return ResponseResult.ok(v);
        }
        return ResponseResult.ok(crop(v, fields));
    }

    @GetMapping("/trace/{traceId}")
    public ResponseResult<?> getByTrace(
            @PathVariable("traceId") String traceId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) List<String> fields) {
        TracePage<LogView> tp = service.searchByTrace(traceId, limit);
        if (fields == null || fields.isEmpty()) {
            return ResponseResult.ok(tp);
        }
        List<Map<String, Object>> cropped = cropList(tp.getItems(), fields);
        return ResponseResult.ok(new TracePage<>(cropped, tp.isTruncated()));
    }

    private Instant parseInstant(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST, "时间参数格式不合法（需 ISO8601）", 400);
        }
    }

    private Page<Map<String, Object>> cropPage(Page<LogView> raw, List<String> fields) {
        return new Page<>(cropList(raw.getItems(), fields), raw.getNextCursor(), raw.getPageSize());
    }

    private List<Map<String, Object>> cropList(List<LogView> items, List<String> fields) {
        return items.stream().map(v -> crop(v, fields)).toList();
    }

    private Map<String, Object> crop(LogView v, List<String> fields) {
        // 转 Map 后按 fields 过滤；保留 Map 顺序与 fields 顺序一致便于阅读
        Map<String, Object> all = objectMapper.convertValue(v, MAP_TYPE);
        Map<String, Object> picked = new LinkedHashMap<>();
        for (String f : fields) {
            if (all.containsKey(f)) {
                picked.put(f, all.get(f));
            }
        }
        return picked;
    }
}
