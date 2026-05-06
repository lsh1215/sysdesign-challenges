package com.crawler.frontier.domain;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public final class Domain {

    private final String host;

    private Domain(String host) {
        this.host = host;
    }

    public static Domain from(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new BusinessException(CrawlerErrorCode.INVALID_URL);
            }
            return new Domain(host.toLowerCase());
        } catch (URISyntaxException e) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
    }

    public String host() {
        return host;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Domain other)) return false;
        return Objects.equals(host, other.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host);
    }

    @Override
    public String toString() {
        return host;
    }
}
