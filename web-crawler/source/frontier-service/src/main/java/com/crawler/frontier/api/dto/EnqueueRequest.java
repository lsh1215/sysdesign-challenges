package com.crawler.frontier.api.dto;

import jakarta.validation.constraints.NotBlank;

public record EnqueueRequest(
        @NotBlank(message = "url must not be blank") String url,
        String priority
) {
}
