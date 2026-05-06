package com.crawler.frontier.domain;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public final class Url {

    private final String url;
    private final Domain domain;
    private final CrawlPriority priority;
    private final Instant discoveredAt;

    private Url(String url, Domain domain, CrawlPriority priority, Instant discoveredAt) {
        this.url = url;
        this.domain = domain;
        this.priority = priority;
        this.discoveredAt = discoveredAt;
    }

    public static Url newUrl(String raw, CrawlPriority priority) {
        return newUrl(raw, priority, Instant.now());
    }

    public static Url newUrl(String raw, CrawlPriority priority, Instant discoveredAt) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
        if (priority == null) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
        String trimmed = raw.trim();
        String lowerScheme = trimmed.toLowerCase();
        if (!lowerScheme.startsWith("http://") && !lowerScheme.startsWith("https://")) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
        String normalized = UrlNormalizer.normalize(trimmed);
        Domain domain = Domain.from(normalized);
        return new Url(normalized, domain, priority, discoveredAt == null ? Instant.now() : discoveredAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Url other)) return false;
        return Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
