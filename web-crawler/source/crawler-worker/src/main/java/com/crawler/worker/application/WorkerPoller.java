package com.crawler.worker.application;

import com.crawler.worker.domain.repository.FrontierClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkerPoller {

    private final FrontierClient frontierClient;
    private final CrawlPipelineService pipelineService;

    @Scheduled(fixedDelayString = "${worker.poll.delay-ms:100}")
    public void pollOnce() {
        frontierClient.pollNext().ifPresent(pipelineService::process);
    }
}
