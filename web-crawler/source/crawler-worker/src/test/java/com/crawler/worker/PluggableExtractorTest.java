package com.crawler.worker;

import com.crawler.infra.parser.ImageLinkExtractor;
import com.crawler.infra.parser.JsoupContentParser;
import com.crawler.infra.parser.JsoupLinkExtractor;
import com.crawler.worker.application.CrawlPipelineService;
import com.crawler.worker.domain.Url;
import com.crawler.worker.domain.repository.ContentSeenRepository;
import com.crawler.worker.domain.repository.ContentStore;
import com.crawler.worker.domain.repository.FrontierClient;
import com.crawler.worker.domain.repository.HtmlSource;
import com.crawler.worker.domain.repository.VisitedUrlRepository;
import com.crawler.worker.domain.service.UrlFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Asserts G-5 plug-in extensibility: pipeline aggregates output from every Spring-injected
 * {@link com.crawler.worker.domain.service.LinkExtractor} bean.
 */
class PluggableExtractorTest {

    @Test
    void pipelineAggregatesAnchorAndImageExtractors() throws Exception {
        HtmlSource htmlSource = mock(HtmlSource.class);
        ContentStore contentStore = mock(ContentStore.class);
        ContentSeenRepository contentSeenRepository = mock(ContentSeenRepository.class);
        VisitedUrlRepository visitedUrlRepository = mock(VisitedUrlRepository.class);
        FrontierClient frontierClient = mock(FrontierClient.class);

        byte[] body = ("<html><body>"
                + "<a href='/page-a'>a</a>"
                + "<img src='/photo-a'>"
                + "</body></html>").getBytes();

        when(htmlSource.download(any())).thenReturn(body);
        when(contentSeenRepository.exists(any())).thenReturn(false);
        when(contentStore.put(any(), any())).thenReturn("k1");

        CrawlPipelineService svc = new CrawlPipelineService(
                htmlSource,
                new JsoupContentParser(),
                List.of(new JsoupLinkExtractor(), new ImageLinkExtractor()),
                new UrlFilter(),
                contentStore,
                contentSeenRepository,
                visitedUrlRepository,
                frontierClient,
                new SimpleMeterRegistry());

        Url seed = Url.of("http://example.com/seed");
        svc.process(seed);

        verify(frontierClient).enqueue("http://example.com/page-a");
        verify(frontierClient).enqueue("http://example.com/photo-a");
    }
}
