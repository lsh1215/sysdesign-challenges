package com.crawler.worker.domain.repository;

import com.crawler.worker.domain.Url;

import java.util.Optional;

public interface FrontierClient {

    void enqueue(String url);

    /**
     * Polls the frontier for the next URL to crawl. Returns {@link Optional#empty()} when the
     * frontier has nothing to dequeue (HTTP 204).
     */
    Optional<Url> pollNext();
}
