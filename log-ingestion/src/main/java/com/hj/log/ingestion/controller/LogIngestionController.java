package com.hj.log.ingestion.controller;

import com.hj.log.common.base.ResponseResult;
import com.hj.log.ingestion.dto.IngestRequest;
import com.hj.log.ingestion.dto.IngestResponse;
import com.hj.log.ingestion.service.LogIngestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logs")
public class LogIngestionController {

    private final LogIngestionService service;

    public LogIngestionController(LogIngestionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseResult<IngestResponse> ingest(@RequestBody @Valid IngestRequest req) {
        return ResponseResult.ok(service.ingest(req));
    }
}
