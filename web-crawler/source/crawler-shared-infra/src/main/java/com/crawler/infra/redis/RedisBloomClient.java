package com.crawler.infra.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Lightweight wrapper over RedisBloom (BF.*) commands. Uses Lettuce's native
 * {@code RedisCommands.dispatch} so that {@link BooleanOrIntegerOutput} can decode the 0/1
 * reply (RESP2 integer) or boolean (RESP3) that BF.ADD/BF.EXISTS return.
 */
@Component
@RequiredArgsConstructor
public class RedisBloomClient {

    private static final ProtocolKeyword BF_ADD = bloomKeyword("BF.ADD");
    private static final ProtocolKeyword BF_EXISTS = bloomKeyword("BF.EXISTS");
    private static final ProtocolKeyword BF_RESERVE = bloomKeyword("BF.RESERVE");

    private final LettuceConnectionFactory connectionFactory;

    public boolean bfAdd(String key, String value) {
        Long result = dispatchInteger(BF_ADD, new CommandArgs<>(StringCodec.UTF8).addKey(key).addValue(value));
        return result != null && result == 1L;
    }

    public boolean bfExists(String key, String value) {
        Long result = dispatchInteger(BF_EXISTS, new CommandArgs<>(StringCodec.UTF8).addKey(key).addValue(value));
        return result != null && result == 1L;
    }

    public void bfReserve(String key, double errorRate, long capacity) {
        CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8)
                .addKey(key)
                .add(Double.toString(errorRate))
                .add(Long.toString(capacity));
        dispatchStatus(BF_RESERVE, args);
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
        byte[] bytes = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
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
