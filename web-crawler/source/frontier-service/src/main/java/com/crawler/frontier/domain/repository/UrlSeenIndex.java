package com.crawler.frontier.domain.repository;

/**
 * Domain port — frontier-service owns its bloom semantics. The crawler-worker module has a
 * separate, equivalent interface in {@code crawler-shared-infra}; intentionally NOT shared so the
 * frontier module can stay free of any {@code crawler-shared-infra} dependency.
 */
public interface UrlSeenIndex {

    /**
     * Atomically records the URL as seen. Returns {@code true} if newly added, {@code false} if
     * the URL was already present.
     */
    boolean markIfAbsent(String url);
}
