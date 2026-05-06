package com.crawler.frontier.api;

import com.crawler.common.exception.GlobalExceptionHandler;
import com.crawler.frontier.application.EnqueueResult;
import com.crawler.frontier.application.FrontierService;
import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FrontierController.class)
@Import(GlobalExceptionHandler.class)
class FrontierControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FrontierService frontierService;

    @Test
    void postNewUrlReturnsQueued201() throws Exception {
        given(frontierService.enqueue(any(Url.class))).willReturn(EnqueueResult.QUEUED);

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("url", "https://example.com/a", "priority", "MEDIUM"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("queued"));
    }

    @Test
    void postDuplicateUrlReturnsDuplicate200() throws Exception {
        given(frontierService.enqueue(any(Url.class))).willReturn(EnqueueResult.DUPLICATE);

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("url", "https://example.com/a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"));
    }

    @Test
    void postBlankUrlReturns400ViaBeanValidation() throws Exception {
        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("url", "", "priority", "MEDIUM"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postMalformedUrlReturns400ViaBusinessException() throws Exception {
        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("url", "not a url"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNextWhenEmptyReturns204() throws Exception {
        given(frontierService.pollNext()).willReturn(Optional.empty());

        mockMvc.perform(get("/urls/next"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getNextReturns200WithDequeueResponse() throws Exception {
        Instant now = Instant.parse("2026-05-07T10:00:00Z");
        Url url = Url.newUrl("https://example.com/x", CrawlPriority.HIGH, now);
        given(frontierService.pollNext()).willReturn(Optional.of(url));

        mockMvc.perform(get("/urls/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com/x"))
                .andExpect(jsonPath("$.domain").value("example.com"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.discoveredAt").exists());
    }
}
