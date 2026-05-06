package com.crawler.frontier.api.dto;

import com.crawler.frontier.domain.Url;

import java.time.Instant;

public record DequeueResponse(
        String url,
        String domain,
        String priority,
        Instant discoveredAt
) {
    public static DequeueResponse from(Url url) {
        return new DequeueResponse(
                url.getUrl(),
                url.getDomain().host(),
                url.getPriority().name(),
                url.getDiscoveredAt()
        );
    }
}
