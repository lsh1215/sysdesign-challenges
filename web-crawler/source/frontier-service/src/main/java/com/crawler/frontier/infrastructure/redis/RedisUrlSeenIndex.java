package com.crawler.frontier.infrastructure.redis;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import com.crawler.frontier.domain.repository.UrlSeenIndex;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Frontier-service's own RedisBloom adapter. Crawler-worker has an equivalent implementation
 * via {@code crawler-shared-infra}; this is a deliberate, minimal copy to keep frontier-service
 * dependency-free of shared-infra (plan §"Module dependencies").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUrlSeenIndex implements UrlSeenIndex {

    public static final String BLOOM_KEY = "url:seen";
    private static final double DEFAULT_ERROR_RATE = 0.01;
    private static final long DEFAULT_CAPACITY = 1_000_000L;

    private static final ProtocolKeyword BF_ADD = bloomKeyword("BF.ADD");
    private static final ProtocolKeyword BF_RESERVE = bloomKeyword("BF.RESERVE");

    private final LettuceConnectionFactory connectionFactory;

    @PostConstruct
    void reserveIfMissing() {
        try {
            CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8)
                    .addKey(BLOOM_KEY)
                    .add(Double.toString(DEFAULT_ERROR_RATE))
                    .add(Long.toString(DEFAULT_CAPACITY));
            dispatchStatus(BF_RESERVE, args);
        } catch (RedisCommandExecutionException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("item exists")) {
                return; // already reserved — fine
            }
            log.warn("BF.RESERVE failed key={} reason={}", BLOOM_KEY, msg);
            throw new BusinessException(CrawlerErrorCode.BLOOM_FILTER_UNAVAILABLE);
        }
    }

    @Override
    public boolean markIfAbsent(String url) {
        try {
            CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8)
                    .addKey(BLOOM_KEY)
                    .addValue(url);
            Long result = dispatchInteger(BF_ADD, args);
            return result != null && result == 1L;
        } catch (RedisCommandExecutionException e) {
            log.warn("BF.ADD failed key={} url={} reason={}", BLOOM_KEY, url, e.getMessage());
            throw new BusinessException(CrawlerErrorCode.BLOOM_FILTER_UNAVAILABLE);
        }
    }

    private Long dispatchInteger(ProtocolKeyword keyword, CommandArgs<String, String> args) {
        try (StatefulRedisConnection<String, String> conn = openConnection()) {
            return conn.sync().dispatch(keyword, new BooleanOrIntegerOutput<>(StringCodec.UTF8), args);
        }
    }

    private void dispatchStatus(ProtocolKeyword keyword, CommandArgs<String, String> args) {
        try (StatefulRedisConnection<String, String> conn = openConnection()) {
            conn.sync().dispatch(keyword, new StatusOutput<>(StringCodec.UTF8), args);
        }
    }

    private StatefulRedisConnection<String, String> openConnection() {
        RedisClient client = (RedisClient) connectionFactory.getRequiredNativeClient();
        return client.connect(StringCodec.UTF8);
    }

    private static ProtocolKeyword bloomKeyword(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);
        return new ProtocolKeyword() {
            @Override
            public byte[] getBytes() {
                return bytes;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}
