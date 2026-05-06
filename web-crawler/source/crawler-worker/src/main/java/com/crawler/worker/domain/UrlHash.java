package com.crawler.worker.domain;

import java.util.Objects;

public final class UrlHash {

    private final String normalizedUrl;

    private UrlHash(String normalizedUrl) {
        this.normalizedUrl = normalizedUrl;
    }

    public static UrlHash of(String url) {
        return new UrlHash(UrlNormalizer.normalize(url));
    }

    public String value() {
        return normalizedUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UrlHash other)) return false;
        return Objects.equals(normalizedUrl, other.normalizedUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedUrl);
    }

    @Override
    public String toString() {
        return normalizedUrl;
    }
}
