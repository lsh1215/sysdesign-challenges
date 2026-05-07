package com.crawler.worker.application;

import com.crawler.worker.domain.ContentHash;
import com.crawler.worker.domain.Url;
import com.crawler.worker.domain.VisitedUrl;
import com.crawler.worker.domain.exception.DownloadException;
import com.crawler.worker.domain.exception.ParseException;
import com.crawler.worker.domain.repository.ContentSeenRepository;
import com.crawler.worker.domain.repository.ContentStore;
import com.crawler.worker.domain.repository.FrontierClient;
import com.crawler.worker.domain.repository.HtmlSource;
import com.crawler.worker.domain.repository.VisitedUrlRepository;
import com.crawler.worker.domain.service.ContentParser;
import com.crawler.worker.domain.service.LinkExtractor;
import com.crawler.worker.domain.service.ParsedDoc;
import com.crawler.worker.domain.service.UrlFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CrawlPipelineServiceTest {

    private HtmlSource htmlSource;
    private ContentParser contentParser;
    private LinkExtractor linkExtractor;
    private UrlFilter urlFilter;
    private ContentStore contentStore;
    private ContentSeenRepository contentSeenRepository;
    private VisitedUrlRepository visitedUrlRepository;
    private FrontierClient frontierClient;
    private SimpleMeterRegistry meterRegistry;
    private CrawlPipelineService service;

    private final Url seed = Url.of("http://example.com/seed");

    @BeforeEach
    void setUp() {
        htmlSource = mock(HtmlSource.class);
        contentParser = mock(ContentParser.class);
        linkExtractor = mock(LinkExtractor.class);
        urlFilter = new UrlFilter();
        contentStore = mock(ContentStore.class);
        contentSeenRepository = mock(ContentSeenRepository.class);
        visitedUrlRepository = mock(VisitedUrlRepository.class);
        frontierClient = mock(FrontierClient.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new CrawlPipelineService(
                htmlSource, contentParser, List.of(linkExtractor), urlFilter,
                contentStore, contentSeenRepository, visitedUrlRepository,
                frontierClient, meterRegistry);
    }

    @Test
    void hitPathSavesVisitedUrlButNotMinio() throws Exception {
        byte[] body = "<html></html>".getBytes();
        when(htmlSource.download(seed.url())).thenReturn(body);
        when(contentSeenRepository.exists(any(ContentHash.class))).thenReturn(true);

        service.process(seed);

        verify(visitedUrlRepository, times(1)).save(any(VisitedUrl.class));
        verifyNoInteractions(contentStore, contentParser, frontierClient);
    }

    @Test
    void missPathFullPipeline() throws Exception {
        byte[] body = "<html><a href='/x'>x</a></html>".getBytes();
        when(htmlSource.download(seed.url())).thenReturn(body);
        when(contentSeenRepository.exists(any(ContentHash.class))).thenReturn(false);
        when(contentStore.put(any(ContentHash.class), any())).thenReturn("k1");
        when(contentParser.parse(any(), any())).thenReturn(new ParsedDoc("<html></html>", "text/html"));
        when(linkExtractor.extract(any(), any())).thenReturn(List.of("http://example.com/x"));

        service.process(seed);

        verify(contentStore, times(1)).put(any(), any());
        verify(contentSeenRepository, times(1)).record(any(), any());
        verify(visitedUrlRepository, times(1)).save(any(VisitedUrl.class));
        verify(contentParser, times(1)).parse(any(), any());
        verify(linkExtractor, times(1)).extract(any(), any());
        verify(frontierClient, times(1)).enqueue("http://example.com/x");
    }

    @Test
    void downloadFailureDropsAndIncrementsCounter() throws Exception {
        when(htmlSource.download(seed.url()))
                .thenThrow(new DownloadException(seed.url(), "http_500", null));

        service.process(seed);

        double v = meterRegistry.find("crawler.pipeline.dropped").tag("reason", "download").counter().count();
        assertThat(v).isEqualTo(1.0);
        verifyNoInteractions(contentStore, contentSeenRepository, visitedUrlRepository, frontierClient);
    }

    @Test
    void parseFailureDropsAfterStorage() throws Exception {
        byte[] body = "<html></html>".getBytes();
        when(htmlSource.download(seed.url())).thenReturn(body);
        when(contentSeenRepository.exists(any(ContentHash.class))).thenReturn(false);
        when(contentStore.put(any(), any())).thenReturn("k1");
        when(contentParser.parse(any(), any()))
                .thenThrow(new ParseException(seed.url(), "bad", null));

        service.process(seed);

        double v = meterRegistry.find("crawler.pipeline.dropped").tag("reason", "parse").counter().count();
        assertThat(v).isEqualTo(1.0);
        verify(contentStore, times(1)).put(any(), any());
        verify(visitedUrlRepository, times(1)).save(any(VisitedUrl.class));
        verify(linkExtractor, never()).extract(any(), any());
        verify(frontierClient, never()).enqueue(any());
    }

    @Test
    void multipleExtractorsAggregate() throws Exception {
        LinkExtractor e1 = mock(LinkExtractor.class);
        LinkExtractor e2 = mock(LinkExtractor.class);
        CrawlPipelineService svc = new CrawlPipelineService(
                htmlSource, contentParser, List.of(e1, e2), urlFilter,
                contentStore, contentSeenRepository, visitedUrlRepository,
                frontierClient, meterRegistry);

        byte[] body = "<html></html>".getBytes();
        when(htmlSource.download(seed.url())).thenReturn(body);
        when(contentSeenRepository.exists(any(ContentHash.class))).thenReturn(false);
        when(contentStore.put(any(), any())).thenReturn("k1");
        when(contentParser.parse(any(), any())).thenReturn(new ParsedDoc("<html></html>", "text/html"));
        when(e1.extract(any(), any())).thenReturn(List.of("http://example.com/a"));
        when(e2.extract(any(), any())).thenReturn(List.of("http://example.com/b"));

        svc.process(seed);

        verify(frontierClient, times(1)).enqueue("http://example.com/a");
        verify(frontierClient, times(1)).enqueue("http://example.com/b");
    }
}
