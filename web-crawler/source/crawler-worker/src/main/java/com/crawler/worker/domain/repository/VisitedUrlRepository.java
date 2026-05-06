package com.crawler.worker.domain.repository;

import com.crawler.worker.domain.VisitedUrl;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

public interface VisitedUrlRepository {

    void save(VisitedUrl visitedUrl);

    Optional<VisitedUrl> findByUrl(String url);

    Stream<VisitedUrl> findStaleSince(Instant cutoff, int batchSize);
}
