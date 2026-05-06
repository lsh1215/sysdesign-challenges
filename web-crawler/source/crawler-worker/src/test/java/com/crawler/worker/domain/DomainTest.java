package com.crawler.worker.domain;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainTest {

    @Test
    void extractsHostFromValidUrl() {
        Domain domain = Domain.from("https://news.example.com/article");

        assertEquals("news.example.com", domain.host());
    }

    @Test
    void rejectsMalformedUrl() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> Domain.from("not a url"));
        assertEquals(CrawlerErrorCode.INVALID_URL, ex.getErrorCode());
    }
}
