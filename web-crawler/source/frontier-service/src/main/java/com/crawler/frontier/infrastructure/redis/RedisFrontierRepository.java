package com.crawler.frontier.infrastructure.redis;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import com.crawler.frontier.domain.repository.FrontierRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed frontier with per-domain politeness leases.
 *
 * <p><b>MVI deviation</b> from the SDD's "priority queue → BackQueueRouter dispatcher → domain
 * queue" pipeline: enqueue pushes directly into the per-domain queue and records the priority
 * inside the serialized payload. Skipping the priority intermediate queue avoids needing a
 * domain-to-priority mapping at dequeue time. Priority weighting at the dispatcher is therefore
 * only informational in MVI; lease-based fair scheduling across domains is what actually
 * delivers politeness. Documented in plan §Phase 4 work-items notes.
 */
@Slf4j
@Repository
public class RedisFrontierRepository implements FrontierRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration leaseTtl;

    public RedisFrontierRepository(StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${frontier.lease-ttl-ms:1000}") long leaseTtlMs) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.leaseTtl = Duration.ofMillis(leaseTtlMs);
    }

    @Override
    public void enqueue(Url url) {
        String host = url.getDomain().host();
        String payload = encode(url);
        redisTemplate.opsForList().rightPush(FrontierKeys.domainQueue(host), payload);
        redisTemplate.opsForSet().add(FrontierKeys.DOMAIN_SET, host);
    }

    @Override
    public Optional<Url> dequeueNext() {
        Set<String> domains = redisTemplate.opsForSet().members(FrontierKeys.DOMAIN_SET);
        if (domains == null || domains.isEmpty()) {
            return Optional.empty();
        }
        List<String> shuffled = new ArrayList<>(domains);
        Collections.shuffle(shuffled);
        for (String domain : shuffled) {
            String leaseKey = FrontierKeys.lease(domain);
            Boolean leaseAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(leaseKey, "1", leaseTtl);
            if (!Boolean.TRUE.equals(leaseAcquired)) {
                continue;
            }
            String payload = redisTemplate.opsForList().leftPop(FrontierKeys.domainQueue(domain));
            if (payload != null) {
                return Optional.of(decode(payload));
            }
            // queue empty — release lease immediately and prune the empty domain
            redisTemplate.delete(leaseKey);
            redisTemplate.opsForSet().remove(FrontierKeys.DOMAIN_SET, domain);
        }
        return Optional.empty();
    }

    private String encode(Url url) {
        try {
            return objectMapper.writeValueAsString(new UrlJson(
                    url.getUrl(),
                    url.getDomain().host(),
                    url.getPriority().name(),
                    url.getDiscoveredAt().toString()
            ));
        } catch (JsonProcessingException e) {
            log.warn("frontier payload encode failed url={} reason={}", url.getUrl(), e.getMessage());
            throw new BusinessException(CrawlerErrorCode.FRONTIER_UNAVAILABLE);
        }
    }

    private Url decode(String payload) {
        try {
            UrlJson json = objectMapper.readValue(payload, UrlJson.class);
            CrawlPriority priority = json.priority() == null
                    ? CrawlPriority.MEDIUM
                    : CrawlPriority.valueOf(json.priority());
            Instant discoveredAt = json.discoveredAt() == null
                    ? Instant.now()
                    : Instant.parse(json.discoveredAt());
            return Url.newUrl(json.url(), priority, discoveredAt);
        } catch (JsonProcessingException e) {
            log.warn("frontier payload decode failed payload={} reason={}", payload, e.getMessage());
            throw new BusinessException(CrawlerErrorCode.FRONTIER_UNAVAILABLE);
        }
    }

    private record UrlJson(String url, String domain, String priority, String discoveredAt) {
    }
}
