package com.crawler.frontier.infrastructure.redis;

import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import com.crawler.frontier.infrastructure.config.FrontierRedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Phase-4 priority+domain queue + lease implementation: payload JSON survives a
 * round-trip and domain assignment is preserved. The full politeness-lease scenario (Q3) is
 * covered end-to-end by {@code FrontierServiceIT}.
 */
@Testcontainers
@SpringBootTest(classes = RedisFrontierRepositoryIT.TestApp.class,
        properties = "frontier.lease-ttl-ms=1000")
class RedisFrontierRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis/redis-stack-server:latest"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private RedisFrontierRepository repository;

    @Test
    void enqueueThenDequeuePreservesPayload() {
        Instant ts = Instant.parse("2026-05-07T10:00:00Z");
        Url url = Url.newUrl("https://payload.example.com/p", CrawlPriority.HIGH, ts);

        repository.enqueue(url);

        Optional<Url> popped = repository.dequeueNext();
        assertThat(popped).isPresent();
        assertThat(popped.get().getUrl()).isEqualTo(url.getUrl());
        assertThat(popped.get().getDomain().host()).isEqualTo("payload.example.com");
        assertThat(popped.get().getPriority()).isEqualTo(CrawlPriority.HIGH);
        assertThat(popped.get().getDiscoveredAt()).isEqualTo(ts);
    }

    @SpringBootApplication(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
    })
    @Import({FrontierRedisConfig.class, RedisFrontierRepository.class, JacksonAutoConfiguration.class})
    static class TestApp {
    }
}
