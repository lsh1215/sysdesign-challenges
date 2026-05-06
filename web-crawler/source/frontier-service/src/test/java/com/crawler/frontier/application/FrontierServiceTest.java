package com.crawler.frontier.application;

import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import com.crawler.frontier.domain.repository.FrontierRepository;
import com.crawler.frontier.domain.repository.UrlSeenIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FrontierServiceTest {

    @Mock
    private FrontierRepository frontierRepository;

    @Mock
    private UrlSeenIndex urlSeenIndex;

    @Mock
    private Prioritizer prioritizer;

    @InjectMocks
    private FrontierService frontierService;

    @Test
    void enqueueReturnsQueuedWhenNew() {
        Url url = Url.newUrl("https://example.com/a", CrawlPriority.MEDIUM);
        given(urlSeenIndex.markIfAbsent("https://example.com/a")).willReturn(true);
        given(prioritizer.refine(any(Url.class), any())).willReturn(CrawlPriority.MEDIUM);

        EnqueueResult result = frontierService.enqueue(url);

        assertThat(result).isEqualTo(EnqueueResult.QUEUED);
        verify(frontierRepository, times(1)).enqueue(any(Url.class));
    }

    @Test
    void enqueueReturnsDuplicateWhenSeen() {
        Url url = Url.newUrl("https://example.com/a", CrawlPriority.MEDIUM);
        given(urlSeenIndex.markIfAbsent("https://example.com/a")).willReturn(false);

        EnqueueResult result = frontierService.enqueue(url);

        assertThat(result).isEqualTo(EnqueueResult.DUPLICATE);
        verify(frontierRepository, never()).enqueue(any(Url.class));
        verify(prioritizer, never()).refine(any(Url.class), any());
    }
}
