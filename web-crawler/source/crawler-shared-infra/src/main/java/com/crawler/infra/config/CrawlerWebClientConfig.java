package com.crawler.infra.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class CrawlerWebClientConfig {

    private static final int MAX_IN_MEMORY_BYTES = 10 * 1024 * 1024;

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
    }
}
