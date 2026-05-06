package com.crawler.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrawlerErrorCodeTest {

    @Test
    void everyCrawlerErrorCodeHasNonNullCodeMessageStatus() {
        for (CrawlerErrorCode value : CrawlerErrorCode.values()) {
            assertThat(value.getCode()).as("code for %s", value).isNotBlank();
            assertThat(value.getMessage()).as("message for %s", value).isNotBlank();
            assertThat(value.getStatus()).as("status for %s", value).isNotNull();
        }
    }

    @Test
    void containsExpectedConstants() {
        assertThat(CrawlerErrorCode.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "URL_NOT_FOUND",
                        "INVALID_URL",
                        "BLOOM_FILTER_UNAVAILABLE",
                        "CONTENT_STORE_UNAVAILABLE",
                        "DOWNLOAD_FAILED",
                        "PARSE_FAILED",
                        "FRONTIER_UNAVAILABLE");
    }
}
