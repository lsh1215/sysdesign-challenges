package com.crawler.freshness.application;

import com.crawler.worker.domain.ContentHash;
import com.crawler.worker.domain.VisitedUrl;
import com.crawler.worker.domain.repository.FrontierClient;
import com.crawler.worker.domain.repository.VisitedUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreshnessServiceTest {

    @Mock
    private VisitedUrlRepository visitedUrlRepository;

    @Mock
    private FrontierClient frontierClient;

    @InjectMocks
    private FreshnessService freshnessService;

    @BeforeEach
    void injectProps() {
        ReflectionTestUtils.setField(freshnessService, "staleThresholdDays", 7);
        ReflectionTestUtils.setField(freshnessService, "batchSize", 1000);
    }

    @Test
    void threeStaleUrls_enqueuedThreeTimes() {
        VisitedUrl v1 = staleUrl("https://example.com/a");
        VisitedUrl v2 = staleUrl("https://example.com/b");
        VisitedUrl v3 = staleUrl("https://example.com/c");

        when(visitedUrlRepository.findStaleSince(any(), eq(1000)))
                .thenReturn(Stream.of(v1, v2, v3));

        int count = freshnessService.rescanStale();

        assertThat(count).isEqualTo(3);
        verify(frontierClient, times(1)).enqueue("https://example.com/a");
        verify(frontierClient, times(1)).enqueue("https://example.com/b");
        verify(frontierClient, times(1)).enqueue("https://example.com/c");
    }

    @Test
    void emptyStream_returnsZeroAndNoEnqueue() {
        when(visitedUrlRepository.findStaleSince(any(), eq(1000)))
                .thenReturn(Stream.empty());

        int count = freshnessService.rescanStale();

        assertThat(count).isZero();
        verify(frontierClient, never()).enqueue(any());
    }

    private VisitedUrl staleUrl(String url) {
        return VisitedUrl.record(url, Instant.now().minusSeconds(86400 * 8), ContentHash.fromHex("aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"));
    }
}
