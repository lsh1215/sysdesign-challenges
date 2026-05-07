package com.crawler.infra.persistence;

import com.crawler.infra.config.JpaConfig;
import com.crawler.worker.domain.ContentHash;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
@SpringBootTest(classes = ContentSeenRepositoryImplIT.TestApp.class)
class ContentSeenRepositoryImplIT {

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
    private ContentSeenRepositoryImpl repository;

    @Test
    void record_then_exists_returns_true() {
        ContentHash hash = ContentHash.of("body-1".getBytes());
        repository.record(hash, "2025/01/01/" + hash.hex() + ".html");

        assertThat(repository.exists(hash)).isTrue();
    }

    @Test
    void record_is_idempotent() {
        ContentHash hash = ContentHash.of("body-2".getBytes());
        repository.record(hash, "key1");

        assertThatCode(() -> repository.record(hash, "key2")).doesNotThrowAnyException();
        assertThat(repository.exists(hash)).isTrue();
    }

    @SpringBootApplication(exclude = {
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            com.crawler.infra.CrawlerInfraAutoConfiguration.class
    })
    @Import({JpaConfig.class, ContentSeenRepositoryImpl.class, VisitedUrlRepositoryImpl.class})
    static class TestApp {
    }
}
