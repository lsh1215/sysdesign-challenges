package com.crawler.worker.domain;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;

import java.time.Instant;

/**
 * Worker-local Url value type. Distinct from frontier-service's {@code Url} aggregate — this VO
 * carries just enough state for the pipeline (raw URL string, derived {@link Domain}, priority
 * string, discoveredAt timestamp).
 */
public record Url(String url, Domain domain, String priority, Instant discoveredAt) {

    public Url {
        if (url == null || url.isBlank()) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
        if (domain == null) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
    }

    public static Url of(String url) {
        return new Url(url, Domain.from(url), "MEDIUM", Instant.now());
    }

    public static Url of(String url, String priority, Instant discoveredAt) {
        Domain d = Domain.from(url);
        String p = (priority == null || priority.isBlank()) ? "MEDIUM" : priority;
        Instant ts = (discoveredAt == null) ? Instant.now() : discoveredAt;
        return new Url(url, d, p, ts);
    }
}
