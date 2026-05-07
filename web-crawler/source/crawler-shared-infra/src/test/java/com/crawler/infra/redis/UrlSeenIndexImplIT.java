package com.crawler.infra.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = UrlSeenIndexImplIT.TestApp.class)
class UrlSeenIndexImplIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS_STACK = new GenericContainer<>(DockerImageName.parse("redis/redis-stack-server:latest"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_STACK::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_STACK.getMappedPort(6379));
    }

    @Autowired
    private RedisBloomClient bloomClient;

    @Autowired
    private UrlSeenIndexImpl index;

    @BeforeEach
    void reserve() {
        // BF.RESERVE is per-key; ignore if already exists by tolerating second reservation failure.
        try {
            bloomClient.bfReserve("url:seen", 0.01, 1000);
        } catch (RuntimeException ignored) {
            // already reserved on prior test run within the same container
        }
    }

    @Test
    void markIfAbsent_first_call_true_second_call_false() {
        assertThat(index.markIfAbsent("https://a.example.com/")).isTrue();
        assertThat(index.markIfAbsent("https://a.example.com/")).isFalse();
        assertThat(index.markIfAbsent("https://b.example.com/")).isTrue();
    }

    @SpringBootApplication(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
            org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
            com.crawler.infra.CrawlerInfraAutoConfiguration.class
    })
    @Import({RedisBloomClient.class, UrlSeenIndexImpl.class})
    static class TestApp {
    }
}
