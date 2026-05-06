package com.crawler.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CrawlerErrorCode implements ErrorCodeBase {

    URL_NOT_FOUND(HttpStatus.NOT_FOUND, "CRAWLER_001", "URL not found"),
    INVALID_URL(HttpStatus.BAD_REQUEST, "CRAWLER_002", "Invalid URL"),
    BLOOM_FILTER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "CRAWLER_003", "Bloom filter unavailable"),
    CONTENT_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "CRAWLER_004", "Content store unavailable"),
    DOWNLOAD_FAILED(HttpStatus.BAD_GATEWAY, "CRAWLER_005", "Download failed"),
    PARSE_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "CRAWLER_006", "Parse failed"),
    FRONTIER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "CRAWLER_007", "Frontier unavailable"),
    INVALID_CONTENT_HASH(HttpStatus.BAD_REQUEST, "CRAWLER_008", "Invalid content hash");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
