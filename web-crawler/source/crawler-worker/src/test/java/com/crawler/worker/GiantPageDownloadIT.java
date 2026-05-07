package com.crawler.worker;

import com.crawler.infra.web.WebClientHtmlSource;
import com.crawler.worker.domain.exception.DownloadException;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asserts robustness against giant pages / spider traps: when the HTTP body exceeds the configured
 * download cap, {@link WebClientHtmlSource} surfaces a {@link DownloadException} which the
 * pipeline drops without writing to MinIO or visited_url.
 */
class GiantPageDownloadIT {

    private static final int CAP_BYTES = 10 * 1024 * 1024; // 10MB
    private static WireMockServer wiremock;

    @BeforeAll
    static void startWiremock() {
        wiremock = new WireMockServer(options().dynamicPort());
        wiremock.start();
    }

    @AfterAll
    static void stopWiremock() {
        wiremock.stop();
    }

    @Test
    void giantBody_throwsDownloadException_sizeExceeded() {
        byte[] body = new byte[50 * 1024 * 1024]; // 50MB
        wiremock.stubFor(get(urlEqualTo("/giant"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/html")
                        .withBody(body)));

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(CAP_BYTES))
                .build();

        WebClientHtmlSource htmlSource = new WebClientHtmlSource(webClient);
        String url = "http://localhost:" + wiremock.port() + "/giant";

        assertThatThrownBy(() -> htmlSource.download(url))
                .isInstanceOf(DownloadException.class)
                .matches(t -> ((DownloadException) t).getReason().equals("size_exceeded"));
    }
}
