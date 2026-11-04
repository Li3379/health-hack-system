package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.MetricDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Public REST controller exposing authoritative metric metadata and clinical ranges.
 * Implements HTTP caching contracts with strong Cache-Control and ETag headers.
 */
@Tag(name = "Metric Metadata", description = "Authoritative health and wellness metric metadata registry")
@RestController
@RequestMapping("/api/metrics/metadata")
@RequiredArgsConstructor
public class MetricMetadataController {

    private final HealthMetricMetaRegistry registry;

    /**
     * Get all metric metadata definitions.
     * Accessible publicly without authentication for client-side stores and validation.
     *
     * @return 200 OK with map of canonicalKey -> MetricDefinition and caching headers
     */
    @Operation(summary = "Get full metric metadata dictionary with 24h HTTP caching")
    @GetMapping
    public ResponseEntity<Result<Map<String, MetricDefinition>>> getMetadata() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic())
                .eTag(registry.getVersionTag())
                .body(Result.success(registry.getAllDefinitions()));
    }
}
