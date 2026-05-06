package com.crawler.frontier.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrlNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiterString = "|", value = {
            "https://Example.com/             | https://example.com",
            "HTTPS://EXAMPLE.COM              | https://example.com",
            "https://example.com/path/        | https://example.com/path/",
            "https://example.com/page#section | https://example.com/page",
            "https://example.com/?b=2&a=1     | https://example.com?a=1&b=2",
            "https://example.com/p?z=9&a=1&m=5| https://example.com/p?a=1&m=5&z=9",
            "https://Example.com:8080/X       | https://example.com:8080/X"
    })
    void normalizesUrl(String raw, String expected) {
        assertEquals(expected, UrlNormalizer.normalize(raw));
    }
}
