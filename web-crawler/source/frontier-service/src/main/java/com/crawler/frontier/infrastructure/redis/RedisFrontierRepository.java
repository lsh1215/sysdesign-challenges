package com.crawler.frontier.infrastructure.redis;

import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import com.crawler.frontier.domain.repository.FrontierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Phase-3 stub. Pushes URLs to a single Redis list ({@link FrontierKeys#SIMPLE_QUEUE}) and pops
 * FIFO. Phase 4 replaces this with a weighted-priority + per-domain leasing implementation.
 */
@Repository
@RequiredArgsConstructor
public class RedisFrontierRepository implements FrontierRepository {

    private static final String FIELD_SEPARATOR = "\t";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void enqueue(Url url) {
        String payload = encode(url);
        redisTemplate.opsForList().rightPush(FrontierKeys.SIMPLE_QUEUE, payload);
    }

    @Override
    public Optional<Url> dequeueNext() {
        String payload = redisTemplate.opsForList().leftPop(FrontierKeys.SIMPLE_QUEUE);
        if (payload == null) {
            return Optional.empty();
        }
        return Optional.of(decode(payload));
    }

    private String encode(Url url) {
        return String.join(FIELD_SEPARATOR,
                url.getUrl(),
                url.getPriority().name(),
                url.getDiscoveredAt().toString());
    }

    private Url decode(String payload) {
        String[] parts = payload.split(FIELD_SEPARATOR, -1);
        String raw = parts[0];
        CrawlPriority priority = parts.length > 1 ? CrawlPriority.valueOf(parts[1]) : CrawlPriority.MEDIUM;
        Instant discoveredAt = parts.length > 2 ? Instant.parse(parts[2]) : Instant.now();
        return Url.newUrl(raw, priority, discoveredAt);
    }
}
