package com.crawler.worker.domain.repository;

public interface FrontierClient {

    void enqueue(String url);
}
