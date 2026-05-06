package com.crawler.infra.redis;

import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.output.CommandOutput;

import java.nio.ByteBuffer;

/**
 * RedisBloom replies to BF.ADD/BF.EXISTS as integer 0/1 under RESP2, but Redis Stack with RESP3
 * surfaces them as RESP boolean. This output accepts either and exposes the result as a Long
 * (1 = present/added-new, 0 = absent/already-member).
 */
final class BooleanOrIntegerOutput<K, V> extends CommandOutput<K, V, Long> {

    BooleanOrIntegerOutput(RedisCodec<K, V> codec) {
        super(codec, null);
    }

    @Override
    public void set(long integer) {
        output = integer;
    }

    @Override
    public void set(boolean value) {
        output = value ? 1L : 0L;
    }

    @Override
    public void set(ByteBuffer bytes) {
        if (bytes == null) {
            output = null;
            return;
        }
        // Some servers may surface the reply as bulk string "1"/"0".
        byte b = bytes.get();
        output = (b == (byte) '1') ? 1L : 0L;
    }
}
