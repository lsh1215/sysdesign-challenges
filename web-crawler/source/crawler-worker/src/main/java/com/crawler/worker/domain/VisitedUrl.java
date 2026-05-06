package com.crawler.worker.domain;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public final class VisitedUrl {

    private final String url;
    private final Domain domain;
    private final Instant lastCrawledAt;
    private final ContentHash lastContentHash;

    private VisitedUrl(String url, Domain domain, Instant lastCrawledAt, ContentHash lastContentHash) {
        this.url = url;
        this.domain = domain;
        this.lastCrawledAt = lastCrawledAt;
        this.lastContentHash = lastContentHash;
    }

    public static VisitedUrl record(String url, Instant lastCrawledAt, ContentHash lastContentHash) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
        if (lastCrawledAt == null) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
        if (lastContentHash == null) {
            throw new BusinessException(CrawlerErrorCode.INVALID_CONTENT_HASH);
        }
        Domain domain = Domain.from(url);
        return new VisitedUrl(url, domain, lastCrawledAt, lastContentHash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VisitedUrl other)) return false;
        return Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
