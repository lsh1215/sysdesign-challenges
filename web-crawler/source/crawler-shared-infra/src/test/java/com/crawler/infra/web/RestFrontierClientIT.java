package com.crawler.infra.web;

import com.crawler.infra.config.CrawlerWebClientConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(classes = RestFrontierClientIT.TestApp.class)
class RestFrontierClientIT {

    private static WireMockServer wiremock;

    @BeforeAll
    static void start() {
        wiremock = new WireMockServer(options().dynamicPort());
        wiremock.start();
        WireMock.configureFor("localhost", wiremock.port());
    }

    @AfterAll
    static void stop() {
        wiremock.stop();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("frontier.base-url", () -> "http://localhost:" + wiremock.port());
    }

    @Autowired
    private RestFrontierClient client;

    @Test
    void enqueue_posts_url_payload_and_succeeds_on_201() {
        wiremock.stubFor(post(urlEqualTo("/urls"))
                .willReturn(aResponse().withStatus(201)));

        assertThatCode(() -> client.enqueue("http://example.com/page")).doesNotThrowAnyException();

        wiremock.verify(postRequestedFor(urlEqualTo("/urls"))
                .withRequestBody(equalToJson("{\"url\":\"http://example.com/page\"}")));
    }

    @SpringBootApplication(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
            org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            com.crawler.infra.CrawlerInfraAutoConfiguration.class
    })
    @Import({CrawlerWebClientConfig.class, RestFrontierClient.class})
    static class TestApp {
    }
}
