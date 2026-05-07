package com.crawler.freshness.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreshnessSchedulerComponent {

    private final FreshnessService freshnessService;

    @Scheduled(cron = "${freshness.cron:0 0 */6 * * *}")
    public void runScheduled() {
        int enqueued = freshnessService.rescanStale();
        log.info("scheduled freshness run complete enqueued={}", enqueued);
    }
}
