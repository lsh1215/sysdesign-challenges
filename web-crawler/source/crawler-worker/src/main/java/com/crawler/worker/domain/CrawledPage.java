package com.crawler.worker.domain;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import lombok.Getter;

import java.util.List;

@Getter
public final class CrawledPage {

    private final String url;
    private final Domain domain;
    private final byte[] body;
    private final ContentHash contentHash;
    private final List<String> extractedLinks;

    private CrawledPage(String url, Domain domain, byte[] body, ContentHash contentHash, List<String> extractedLinks) {
        this.url = url;
        this.domain = domain;
        this.body = body;
        this.contentHash = contentHash;
        this.extractedLinks = extractedLinks;
    }

    public static CrawledPage of(String url, byte[] body, ContentHash contentHash, List<String> extractedLinks) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
        if (body == null) {
            throw new BusinessException(CrawlerErrorCode.DOWNLOAD_FAILED);
        }
        if (contentHash == null) {
            throw new BusinessException(CrawlerErrorCode.INVALID_CONTENT_HASH);
        }
        Domain domain = Domain.from(url);
        return new CrawledPage(url, domain, body,
                contentHash,
                extractedLinks == null ? List.of() : List.copyOf(extractedLinks));
    }
}
