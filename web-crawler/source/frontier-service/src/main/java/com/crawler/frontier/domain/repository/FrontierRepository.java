package com.crawler.frontier.domain.repository;

import com.crawler.frontier.domain.Url;

import java.util.Optional;

public interface FrontierRepository {

    void enqueue(Url url);

    Optional<Url> dequeueNext();
}
