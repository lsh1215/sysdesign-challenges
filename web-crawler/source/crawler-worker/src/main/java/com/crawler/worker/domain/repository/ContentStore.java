package com.crawler.worker.domain.repository;

import com.crawler.worker.domain.ContentHash;

public interface ContentStore {

    String put(ContentHash hash, byte[] body);
}
