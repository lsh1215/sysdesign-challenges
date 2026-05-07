package com.crawler.worker.application;

import com.crawler.worker.domain.Url;
import com.crawler.worker.domain.repository.FrontierClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerPollerTest {

    @Test
    void emptyQueueDoesNotCallPipeline() {
        FrontierClient frontier = mock(FrontierClient.class);
        CrawlPipelineService pipeline = mock(CrawlPipelineService.class);
        when(frontier.pollNext()).thenReturn(Optional.empty());

        new WorkerPoller(frontier, pipeline).pollOnce();

        verify(pipeline, never()).process(any());
    }

    @Test
    void presentUrlDelegatesToPipeline() {
        FrontierClient frontier = mock(FrontierClient.class);
        CrawlPipelineService pipeline = mock(CrawlPipelineService.class);
        Url url = Url.of("http://example.com/p");
        when(frontier.pollNext()).thenReturn(Optional.of(url));

        new WorkerPoller(frontier, pipeline).pollOnce();

        verify(pipeline, times(1)).process(url);
    }
}
