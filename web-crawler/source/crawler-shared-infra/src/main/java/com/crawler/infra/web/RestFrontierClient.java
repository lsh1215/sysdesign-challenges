package com.crawler.infra.web;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import com.crawler.worker.domain.repository.FrontierClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestFrontierClient implements FrontierClient {

    private final WebClient webClient;

    @Value("${frontier.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public void enqueue(String url) {
        try {
            webClient.post()
                    .uri(baseUrl + "/urls")
                    .bodyValue(new EnqueueRequestDto(url))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            log.warn("frontier enqueue failed url={} status={} body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(CrawlerErrorCode.FRONTIER_UNAVAILABLE);
        }
    }

    private record EnqueueRequestDto(String url) {
    }
}
