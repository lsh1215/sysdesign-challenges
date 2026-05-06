package com.crawler.frontier.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlPriorityTest {

    @Test
    void weightsAreOrdered() {
        assertTrue(CrawlPriority.HIGH.weight() > CrawlPriority.MEDIUM.weight());
        assertTrue(CrawlPriority.MEDIUM.weight() > CrawlPriority.LOW.weight());
    }
}
