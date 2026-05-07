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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class CrawlPipelineService {

    private final HtmlSource htmlSource;
    private final ContentParser contentParser;
    private final List<LinkExtractor> linkExtractors;
    private final UrlFilter urlFilter;
    private final ContentStore contentStore;
    private final ContentSeenRepository contentSeenRepository;
    private final VisitedUrlRepository visitedUrlRepository;
    private final FrontierClient frontierClient;

    private final Counter dropDownloadCounter;
    private final Counter dropParseCounter;

    public CrawlPipelineService(HtmlSource htmlSource,
                                ContentParser contentParser,
                                List<LinkExtractor> linkExtractors,
                                UrlFilter urlFilter,
                                ContentStore contentStore,
                                ContentSeenRepository contentSeenRepository,
                                VisitedUrlRepository visitedUrlRepository,
                                FrontierClient frontierClient,
                                MeterRegistry meterRegistry) {
        this.htmlSource = htmlSource;
        this.contentParser = contentParser;
        this.linkExtractors = linkExtractors;
        this.urlFilter = urlFilter;
        this.contentStore = contentStore;
        this.contentSeenRepository = contentSeenRepository;
        this.visitedUrlRepository = visitedUrlRepository;
        this.frontierClient = frontierClient;
        this.dropDownloadCounter = Counter.builder("crawler.pipeline.dropped")
                .tag("reason", "download")
                .register(meterRegistry);
        this.dropParseCounter = Counter.builder("crawler.pipeline.dropped")
                .tag("reason", "parse")
                .register(meterRegistry);
    }

    public void process(Url url) {
        try {
            runPipeline(url);
        } catch (DownloadException e) {
            log.warn("Pipeline drop: download failed url={} reason={}", url.url(), e.getReason());
            dropDownloadCounter.increment();
            return;
        } catch (ParseException e) {
            log.warn("Pipeline drop: parse failed url={} reason={}", url.url(), e.getReason());
            dropParseCounter.increment();
            return;
        }
    }

    private void runPipeline(Url url) throws DownloadException, ParseException {
        byte[] body = htmlSource.download(url.url());
        ContentHash contentHash = ContentHash.of(body);

        if (contentSeenRepository.exists(contentHash)) {
            visitedUrlRepository.save(VisitedUrl.record(url.url(), Instant.now(), contentHash));
            return;
        }

        String storageKey = contentStore.put(contentHash, body);
        contentSeenRepository.record(contentHash, storageKey);
        visitedUrlRepository.save(VisitedUrl.record(url.url(), Instant.now(), contentHash));

        ParsedDoc doc = contentParser.parse(body, url.url());
        List<String> links = linkExtractors.stream()
                .flatMap(e -> e.extract(doc, url.url()).stream())
                .toList();
        for (String link : links) {
            if (urlFilter.accept(link)) {
                frontierClient.enqueue(link);
            }
        }
    }
}
