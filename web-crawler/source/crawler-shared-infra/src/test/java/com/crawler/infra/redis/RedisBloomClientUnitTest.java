package com.crawler.infra.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class RedisBloomClientUnitTest {

    @Test
    void bfAdd_dispatches_BFADD_with_IntegerOutput_and_returns_true_on_one() {
        Mocks m = newMocks(1L);

        RedisBloomClient bloom = new RedisBloomClient(m.factory);
        boolean added = bloom.bfAdd("url:seen", "https://a.example.com/");

        assertThat(added).isTrue();

        ArgumentCaptor<ProtocolKeyword> keywordCap = ArgumentCaptor.forClass(ProtocolKeyword.class);
        ArgumentCaptor<CommandOutput> outputCap = ArgumentCaptor.forClass(CommandOutput.class);
        verify(m.sync).dispatch(keywordCap.capture(), outputCap.capture(), any(CommandArgs.class));

        assertThat(keywordCap.getValue().name()).isEqualTo("BF.ADD");
        assertThat(outputCap.getValue()).isInstanceOf(BooleanOrIntegerOutput.class);
    }

    @Test
    void bfExists_returns_false_when_dispatch_returns_zero() {
        Mocks m = newMocks(0L);

        RedisBloomClient bloom = new RedisBloomClient(m.factory);
        assertThat(bloom.bfExists("url:seen", "missing")).isFalse();

        ArgumentCaptor<ProtocolKeyword> keywordCap = ArgumentCaptor.forClass(ProtocolKeyword.class);
        verify(m.sync).dispatch(keywordCap.capture(), any(CommandOutput.class), any(CommandArgs.class));
        assertThat(keywordCap.getValue().name()).isEqualTo("BF.EXISTS");
    }

    private static Mocks newMocks(Long dispatchResult) {
        Mocks m = new Mocks();
        m.factory = mock(LettuceConnectionFactory.class);
        m.client = mock(RedisClient.class);
        m.conn = mock(StatefulRedisConnection.class);
        m.sync = mock(RedisCommands.class);
        when(m.factory.getRequiredNativeClient()).thenReturn(m.client);
        when(m.client.connect(any(RedisCodec.class))).thenReturn(m.conn);
        when(m.conn.sync()).thenReturn(m.sync);
        when(m.sync.dispatch(any(ProtocolKeyword.class), any(CommandOutput.class), any(CommandArgs.class)))
                .thenReturn(dispatchResult);
        return m;
    }

    private static class Mocks {
        LettuceConnectionFactory factory;
        RedisClient client;
        StatefulRedisConnection<String, String> conn;
        RedisCommands<String, String> sync;
    }
}
