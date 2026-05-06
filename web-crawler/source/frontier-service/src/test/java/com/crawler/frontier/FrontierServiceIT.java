package com.crawler.frontier;

import com.crawler.frontier.application.EnqueueResult;
import com.crawler.frontier.application.FrontierService;
import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class FrontierServiceIT {

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
    private FrontierService frontierService;

    @Test
    void enqueueDequeueRoundtrip() {
        Url url = Url.newUrl("https://round.example.com/page", CrawlPriority.HIGH);

        EnqueueResult result = frontierService.enqueue(url);

        assertThat(result).isEqualTo(EnqueueResult.QUEUED);

        Optional<Url> popped = frontierService.pollNext();
        assertThat(popped).isPresent();
        assertThat(popped.get().getUrl()).isEqualTo(url.getUrl());
        assertThat(popped.get().getDomain().host()).isEqualTo("round.example.com");
        assertThat(popped.get().getPriority()).isEqualTo(CrawlPriority.HIGH);

        // second poll within the lease window — domain still leased AND queue empty for that
        // domain — returns empty.
        Optional<Url> second = frontierService.pollNext();
        assertThat(second).isEmpty();
    }

    @Test
    void politenessLeaseHoldsDomain_Q3() throws InterruptedException {
        Url a1 = Url.newUrl("https://a.q3.example.com/1", CrawlPriority.MEDIUM);
        Url a2 = Url.newUrl("https://a.q3.example.com/2", CrawlPriority.MEDIUM);
        Url b1 = Url.newUrl("https://b.q3.example.com/1", CrawlPriority.MEDIUM);

        assertThat(frontierService.enqueue(a1)).isEqualTo(EnqueueResult.QUEUED);
        assertThat(frontierService.enqueue(a2)).isEqualTo(EnqueueResult.QUEUED);
        assertThat(frontierService.enqueue(b1)).isEqualTo(EnqueueResult.QUEUED);

        // Poll 1: acquires lease on one of the two domains and returns its first URL.
        Optional<Url> poll1 = frontierService.pollNext();
        assertThat(poll1).isPresent();
        String poll1Host = poll1.get().getDomain().host();

        // Poll 2: domain from poll 1 is leased; the OTHER domain is free → returns its URL.
        Optional<Url> poll2 = frontierService.pollNext();
        assertThat(poll2).isPresent();
        String poll2Host = poll2.get().getDomain().host();
        assertThat(poll2Host).isNotEqualTo(poll1Host);

        // Poll 3: both domain leases held; even though a.q3 still has a2, lease blocks → empty.
        Optional<Url> poll3 = frontierService.pollNext();
        assertThat(poll3).isEmpty();

        // Wait for both 1-second leases to expire.
        Thread.sleep(1_100);

        // Poll 4: leases expired; the still-non-empty domain (a.q3) yields a2.
        Optional<Url> poll4 = frontierService.pollNext();
        assertThat(poll4).isPresent();
        assertThat(poll4.get().getDomain().host()).isEqualTo("a.q3.example.com");
        assertThat(poll4.get().getUrl()).isEqualTo(a2.getUrl());
    }
}
