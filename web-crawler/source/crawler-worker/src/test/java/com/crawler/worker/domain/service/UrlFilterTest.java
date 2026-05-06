package com.crawler.worker.domain.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlFilterTest {

    private final UrlFilter filter = new UrlFilter();

    @Test
    void allowsHttpAndHttps() {
        assertTrue(filter.accept("http://example.com/page"));
        assertTrue(filter.accept("https://example.com/page"));
    }

    @Test
    void rejectsMailto() {
        assertFalse(filter.accept("mailto:user@example.com"));
    }

    @Test
    void rejectsJavascript() {
        assertFalse(filter.accept("javascript:alert(1)"));
    }

    @Test
    void rejectsNonHttpScheme() {
        assertFalse(filter.accept("ftp://example.com/file"));
        assertFalse(filter.accept("file:///etc/passwd"));
    }

    @Test
    void rejectsBinaryExtensions() {
        assertFalse(filter.accept("https://example.com/doc.pdf"));
        assertFalse(filter.accept("https://example.com/archive.zip"));
        assertFalse(filter.accept("https://example.com/image.png"));
        assertFalse(filter.accept("https://example.com/style.css"));
    }

    @Test
    void rejectsBlankAndNull() {
        assertFalse(filter.accept(""));
        assertFalse(filter.accept(null));
    }
}
