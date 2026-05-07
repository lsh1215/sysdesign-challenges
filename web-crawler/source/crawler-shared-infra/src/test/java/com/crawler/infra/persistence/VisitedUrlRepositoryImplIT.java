package com.crawler.infra.persistence;

import com.crawler.infra.config.JpaConfig;
import com.crawler.worker.domain.ContentHash;
import com.crawler.worker.domain.VisitedUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = VisitedUrlRepositoryImplIT.TestApp.class)
class VisitedUrlRepositoryImplIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("crawler")
            .withUsername("crawler")
            .withPassword("crawler");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private VisitedUrlRepositoryImpl repository;

    @Test
    void save_then_findByUrl_returns_same_visitedUrl() {
        Instant now = Instant.now();
        ContentHash hash = ContentHash.of("hello".getBytes());
        VisitedUrl visited = VisitedUrl.record("https://news.example.com/a", now, hash);

        repository.save(visited);
        Optional<VisitedUrl> found = repository.findByUrl("https://news.example.com/a");

        assertThat(found).isPresent();
        assertThat(found.get().getUrl()).isEqualTo("https://news.example.com/a");
        assertThat(found.get().getLastContentHash().hex()).isEqualTo(hash.hex());
    }

    @Test
    void findStaleSince_returns_only_rows_before_cutoff() {
        Instant now = Instant.now();
        repository.save(VisitedUrl.record("https://stale.example.com/x",
                now.minus(2, ChronoUnit.DAYS), ContentHash.of("x".getBytes())));
        repository.save(VisitedUrl.record("https://fresh.example.com/y",
                now, ContentHash.of("y".getBytes())));

        try (Stream<VisitedUrl> stream = repository.findStaleSince(now.minus(1, ChronoUnit.DAYS), 100)) {
            List<VisitedUrl> stale = stream.toList();
            assertThat(stale).extracting(VisitedUrl::getUrl)
                    .contains("https://stale.example.com/x")
                    .doesNotContain("https://fresh.example.com/y");
        }
    }

    @SpringBootApplication(exclude = {
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            com.crawler.infra.CrawlerInfraAutoConfiguration.class
    })
    @Import({JpaConfig.class, VisitedUrlRepositoryImpl.class, ContentSeenRepositoryImpl.class})
    static class TestApp {
    }
}
