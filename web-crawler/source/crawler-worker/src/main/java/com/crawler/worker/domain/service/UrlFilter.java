package com.crawler.worker.domain.service;

import java.util.Locale;
import java.util.Set;

public final class UrlFilter {

    private static final Set<String> DEFAULT_REJECTED_EXTENSIONS = Set.of(
            ".pdf", ".zip", ".jpg", ".jpeg", ".png", ".gif",
            ".css", ".js", ".ico", ".svg", ".woff", ".woff2", ".ttf"
    );

    private final Set<String> rejectedExtensions;

    public UrlFilter() {
        this(DEFAULT_REJECTED_EXTENSIONS);
    }

    public UrlFilter(Set<String> rejectedExtensions) {
        this.rejectedExtensions = Set.copyOf(rejectedExtensions);
    }

    public boolean accept(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("mailto:") || lower.startsWith("javascript:")) {
            return false;
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        int queryIdx = lower.indexOf('?');
        String pathPart = queryIdx < 0 ? lower : lower.substring(0, queryIdx);
        for (String ext : rejectedExtensions) {
            if (pathPart.endsWith(ext)) {
                return false;
            }
        }
        return true;
    }
}
