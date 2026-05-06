package com.crawler.frontier.infrastructure.redis;

import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.output.CommandOutput;

import java.nio.ByteBuffer;

/**
 * Decodes BF.ADD/BF.EXISTS replies — RESP2 returns integer 0/1, RESP3 returns boolean. Mirrors
 * the equivalent helper in {@code crawler-shared-infra}; duplicated here to keep frontier-service
 * free of an inter-module dependency on shared-infra (per plan §"Module dependencies").
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
        byte b = bytes.get();
        output = (b == (byte) '1') ? 1L : 0L;
    }
}
