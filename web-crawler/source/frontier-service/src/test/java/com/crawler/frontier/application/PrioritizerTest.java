package com.crawler.frontier.application;

import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrioritizerTest {

    private final Prioritizer prioritizer = new Prioritizer();

    @Test
    void explicitHintIsPreserved() {
        Url url = Url.newUrl("https://example.com/", CrawlPriority.MEDIUM);

        CrawlPriority refined = prioritizer.refine(url, CrawlPriority.HIGH);

        assertThat(refined).isEqualTo(CrawlPriority.HIGH);
    }

    @Test
    void nullHintFallsBackToMedium() {
        Url url = Url.newUrl("https://example.com/", CrawlPriority.LOW);

        CrawlPriority refined = prioritizer.refine(url, null);

        assertThat(refined).isEqualTo(CrawlPriority.MEDIUM);
    }
}
