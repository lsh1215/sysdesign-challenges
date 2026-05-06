package com.crawler.frontier.api;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import com.crawler.frontier.api.dto.DequeueResponse;
import com.crawler.frontier.api.dto.EnqueueRequest;
import com.crawler.frontier.application.EnqueueResult;
import com.crawler.frontier.application.FrontierService;
import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/urls")
@RequiredArgsConstructor
public class FrontierController {

    private final FrontierService frontierService;

    @PostMapping
    public ResponseEntity<Map<String, String>> enqueue(@Valid @RequestBody EnqueueRequest request) {
        Url url = Url.newUrl(request.url(), parsePriority(request.priority()));
        EnqueueResult result = frontierService.enqueue(url);
        if (result == EnqueueResult.DUPLICATE) {
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "queued"));
    }

    @GetMapping("/next")
    public ResponseEntity<DequeueResponse> next() {
        Optional<Url> next = frontierService.pollNext();
        return next.map(url -> ResponseEntity.ok(DequeueResponse.from(url)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private CrawlPriority parsePriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return CrawlPriority.MEDIUM;
        }
        String normalized = raw.trim().toUpperCase();
        for (CrawlPriority p : CrawlPriority.values()) {
            if (p.name().equals(normalized)) {
                return p;
            }
        }
        throw new BusinessException(CrawlerErrorCode.INVALID_URL);
    }
}
