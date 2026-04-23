package com.hj.log.common.exception;

/**
 * 错误码字典常量。命名规范 {@code <MODULE>_<KIND>}。
 * 完整定义见 requirements/REQ-2026-001/artifacts/detailed-design.md §3。
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String OK = "OK";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String BAD_REQUEST = "BAD_REQUEST";

    public static final String AUTH_MISSING_TOKEN = "AUTH_MISSING_TOKEN";
    public static final String AUTH_INVALID_KEY = "AUTH_INVALID_KEY";
    public static final String AUTH_INVALID_ADMIN_TOKEN = "AUTH_INVALID_ADMIN_TOKEN";
    public static final String AUTH_SCOPE_MISMATCH = "AUTH_SCOPE_MISMATCH";

    public static final String APP_CODE_DUPLICATE = "APP_CODE_DUPLICATE";
    public static final String APP_INVALID_ENV = "APP_INVALID_ENV";
    public static final String APP_NOT_FOUND = "APP_NOT_FOUND";

    public static final String KEY_INVALID_SCOPE = "KEY_INVALID_SCOPE";
    public static final String KEY_NOT_FOUND = "KEY_NOT_FOUND";

    public static final String INGEST_BATCH_TOO_LARGE = "INGEST_BATCH_TOO_LARGE";
    public static final String INGEST_MESSAGE_BLANK = "INGEST_MESSAGE_BLANK";
    public static final String INGEST_INVALID_LEVEL = "INGEST_INVALID_LEVEL";
    public static final String INGEST_INVALID_KIND = "INGEST_INVALID_KIND";
    public static final String INGEST_MESSAGE_TOO_LONG = "INGEST_MESSAGE_TOO_LONG";

    public static final String QUERY_MISSING_APP_FILTER = "QUERY_MISSING_APP_FILTER";
    public static final String QUERY_KEYWORD_PATTERN_INVALID = "QUERY_KEYWORD_PATTERN_INVALID";
    public static final String QUERY_PAGE_SIZE_TOO_LARGE = "QUERY_PAGE_SIZE_TOO_LARGE";
    public static final String QUERY_LIMIT_TOO_LARGE = "QUERY_LIMIT_TOO_LARGE";
    public static final String QUERY_LOG_NOT_FOUND = "QUERY_LOG_NOT_FOUND";
    public static final String QUERY_INVALID_CURSOR = "QUERY_INVALID_CURSOR";
}
