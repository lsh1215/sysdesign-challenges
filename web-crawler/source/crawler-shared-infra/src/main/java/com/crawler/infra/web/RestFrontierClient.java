package com.crawler.infra.web;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import com.crawler.worker.domain.Url;
import com.crawler.worker.domain.repository.FrontierClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

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

    @Override
    public Optional<Url> pollNext() {
        try {
            ResponseEntity<DequeueResponseDto> response = webClient.get()
                    .uri(baseUrl + "/urls/next")
                    .retrieve()
                    .toEntity(DequeueResponseDto.class)
                    .block();
            if (response == null || response.getStatusCode() == HttpStatus.NO_CONTENT) {
                return Optional.empty();
            }
            DequeueResponseDto body = response.getBody();
            if (body == null || body.url() == null) {
                return Optional.empty();
            }
            Instant discoveredAt = parseInstant(body.discoveredAt());
            return Optional.of(Url.of(body.url(), body.priority(), discoveredAt));
        } catch (WebClientResponseException e) {
            log.warn("frontier pollNext failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(CrawlerErrorCode.FRONTIER_UNAVAILABLE);
        }
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private record EnqueueRequestDto(String url) {
    }

    private record DequeueResponseDto(String url, String domain, String priority, String discoveredAt) {
    }
}
