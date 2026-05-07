package com.crawler.freshness.application;

import com.crawler.freshness.FreshnessSchedulerApplication;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = FreshnessSchedulerApplication.class,
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                "minio.endpoint=http://localhost:9000",
                "minio.access-key=minioadmin",
                "minio.secret-key=minioadmin",
                "minio.bucket=crawler-html",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "freshness.stale-threshold-days=7",
                "freshness.batch-size=100"
        }
)
class FreshnessServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("crawler")
            .withUsername("crawler")
            .withPassword("crawler");

    private static WireMockServer wiremock;

    @BeforeAll
    static void startWiremock() {
        wiremock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wiremock.start();
    }

    @AfterAll
    static void stopWiremock() {
        wiremock.stop();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("frontier.base-url", () -> "http://localhost:" + wiremock.port());
    }

    @Autowired
    private FreshnessService freshnessService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleUrl_isEnqueuedToFrontier() {
        wiremock.resetAll();
        wiremock.stubFor(post(urlEqualTo("/urls"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"queued\"}")));

        String staleUrl = "https://stale.example.com/old-page";
        String freshUrl = "https://fresh.example.com/recent-page";
        String staleHash = "0000000000000000000000000000000000000000000000000000000000000001";
        String freshHash = "0000000000000000000000000000000000000000000000000000000000000002";

        Timestamp staleTime = Timestamp.from(Instant.now().minusSeconds(86400L * 8));
        Timestamp freshTime = Timestamp.from(Instant.now().minusSeconds(3600));

        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO visited_url (url, domain, last_crawled_at, last_content_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (url) DO UPDATE SET last_crawled_at = EXCLUDED.last_crawled_at",
                staleUrl, "stale.example.com", staleTime, staleHash, now, now);
        jdbcTemplate.update(
                "INSERT INTO visited_url (url, domain, last_crawled_at, last_content_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (url) DO UPDATE SET last_crawled_at = EXCLUDED.last_crawled_at",
                freshUrl, "fresh.example.com", freshTime, freshHash, now, now);

        int count = freshnessService.rescanStale();

        assertThat(count).isEqualTo(1);
        wiremock.verify(1, postRequestedFor(urlEqualTo("/urls")));
    }
}
