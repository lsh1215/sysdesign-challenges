package com.crawler.worker.domain.repository;

import com.crawler.worker.domain.ContentHash;

public interface ContentSeenRepository {

    boolean exists(ContentHash hash);

    void record(ContentHash hash, String storageKey);
}
