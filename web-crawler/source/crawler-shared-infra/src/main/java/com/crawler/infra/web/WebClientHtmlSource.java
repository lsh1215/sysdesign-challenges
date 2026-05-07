package com.crawler.infra.web;

import com.crawler.worker.domain.exception.DownloadException;
import com.crawler.worker.domain.repository.HtmlSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebClientHtmlSource implements HtmlSource {

    private final WebClient webClient;

    @Override
    public byte[] download(String url) throws DownloadException {
        try {
            byte[] body = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            if (body == null) {
                throw new DownloadException(url, "empty_response", null);
            }
            return body;
        } catch (WebClientResponseException e) {
            // When the body decoder overflows the configured maxInMemorySize on a 2xx response,
            // WebClient wraps the DataBufferLimitException inside a WebClientResponseException
            // carrying the original status (e.g. 200). Surface that as size_exceeded so callers
            // can treat it as a giant-page drop, not a transport/HTTP error.
            if (hasCause(e, DataBufferLimitException.class)) {
                log.warn("download size exceeded (wrapped in WebClientResponseException) url={}", url);
                throw new DownloadException(url, "size_exceeded", e);
            }
            log.warn("download http error url={} status={}", url, e.getStatusCode());
            throw new DownloadException(url, "http_" + e.getStatusCode().value(), e);
        } catch (DataBufferLimitException e) {
            log.warn("download size exceeded url={}", url);
            throw new DownloadException(url, "size_exceeded", e);
        } catch (WebClientRequestException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DataBufferLimitException) {
                throw new DownloadException(url, "size_exceeded", cause);
            }
            if (cause instanceof IOException) {
                throw new DownloadException(url, "io_error", cause);
            }
            throw new DownloadException(url, "transport_error", e);
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> target) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (target.isInstance(c)) {
                return true;
            }
            if (c.getCause() == c) {
                return false;
            }
        }
        return false;
    }
}
