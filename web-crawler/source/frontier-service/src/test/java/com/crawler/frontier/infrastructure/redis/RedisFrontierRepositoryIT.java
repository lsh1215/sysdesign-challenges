package com.crawler.frontier.infrastructure.redis;

import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import com.crawler.frontier.infrastructure.config.FrontierRedisConfig;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = RedisFrontierRepositoryIT.TestApp.class)
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
    void enqueue_then_dequeue_returns_FIFO_order() {
        List<Url> urls = List.of(
                Url.newUrl("https://a.example.com/", CrawlPriority.MEDIUM),
                Url.newUrl("https://b.example.com/", CrawlPriority.MEDIUM),
                Url.newUrl("https://c.example.com/", CrawlPriority.MEDIUM)
        );
        urls.forEach(repository::enqueue);

        for (Url expected : urls) {
            Optional<Url> dequeued = repository.dequeueNext();
            assertThat(dequeued).isPresent();
            assertThat(dequeued.get().getUrl()).isEqualTo(expected.getUrl());
        }
        assertThat(repository.dequeueNext()).isEmpty();
    }

    @SpringBootApplication(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
    })
    @Import({FrontierRedisConfig.class, RedisFrontierRepository.class})
    static class TestApp {
    }
}
