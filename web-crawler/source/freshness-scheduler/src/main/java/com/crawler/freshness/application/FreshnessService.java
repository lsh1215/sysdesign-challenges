package com.crawler.freshness.application;

import com.crawler.worker.domain.VisitedUrl;
import com.crawler.worker.domain.repository.FrontierClient;
import com.crawler.worker.domain.repository.VisitedUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreshnessService {

    private final VisitedUrlRepository visitedUrlRepository;
    private final FrontierClient frontierClient;

    @Value("${freshness.stale-threshold-days:7}")
    private int staleThresholdDays;

    @Value("${freshness.batch-size:1000}")
    private int batchSize;

    public int rescanStale() {
        Instant cutoff = Instant.now().minus(staleThresholdDays, ChronoUnit.DAYS);
        log.info("rescanStale start cutoff={} batchSize={}", cutoff, batchSize);

        int count = 0;
        try (Stream<VisitedUrl> stale = visitedUrlRepository.findStaleSince(cutoff, batchSize)) {
            for (VisitedUrl visited : (Iterable<VisitedUrl>) stale::iterator) {
                frontierClient.enqueue(visited.getUrl());
                count++;
            }
        }

        log.info("rescanStale complete enqueued={}", count);
        return count;
    }
}
