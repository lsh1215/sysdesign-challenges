package com.crawler.worker.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UrlHashTest {

    @Test
    void equalityByNormalizedUrl() {
        UrlHash a = UrlHash.of("HTTPS://Example.com/page?b=2&a=1");
        UrlHash b = UrlHash.of("https://example.com/page?a=1&b=2");

        assertEquals(a, b);
    }

    @Test
    void differentUrlsAreNotEqual() {
        UrlHash a = UrlHash.of("https://example.com/a");
        UrlHash b = UrlHash.of("https://example.com/b");

        assertNotEquals(a, b);
    }

    @Test
    void distinctTypeFromContentHash() {
        UrlHash urlHash = UrlHash.of("https://example.com");
        ContentHash contentHash = ContentHash.of("https://example.com".getBytes());

        // values differ — UrlHash is the normalized URL, ContentHash is the SHA-256 hex
        assertNotEquals(urlHash.value(), contentHash.hex());
    }
}
