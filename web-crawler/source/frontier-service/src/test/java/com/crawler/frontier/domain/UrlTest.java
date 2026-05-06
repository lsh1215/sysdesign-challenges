package com.crawler.frontier.domain;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlTest {

    @Test
    void factoryNormalizesAndDerivesDomain() {
        Url url = Url.newUrl("HTTPS://Example.com/Path/?b=2&a=1", CrawlPriority.HIGH);

        assertEquals("https://example.com/Path/?a=1&b=2", url.getUrl());
        assertEquals("example.com", url.getDomain().host());
        assertEquals(CrawlPriority.HIGH, url.getPriority());
        assertNotNull(url.getDiscoveredAt());
    }

    @Test
    void rejectsBlankUrl() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> Url.newUrl("", CrawlPriority.LOW));
        assertEquals(CrawlerErrorCode.INVALID_URL, ex.getErrorCode());
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThrows(BusinessException.class,
                () -> Url.newUrl("ftp://example.com/file", CrawlPriority.LOW));
        assertThrows(BusinessException.class,
                () -> Url.newUrl("javascript:alert(1)", CrawlPriority.LOW));
    }

    @Test
    void rejectsNullPriority() {
        assertThrows(BusinessException.class,
                () -> Url.newUrl("https://example.com", null));
    }
}
