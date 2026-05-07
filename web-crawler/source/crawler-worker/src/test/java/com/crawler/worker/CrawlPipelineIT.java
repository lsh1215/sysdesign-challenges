package com.crawler.worker;

import com.crawler.worker.application.CrawlPipelineService;
import com.crawler.worker.domain.Url;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = CrawlerWorkerApplication.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                "worker.poll.delay-ms=86400000"
        }
)
class CrawlPipelineIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("crawler")
            .withUsername("crawler")
            .withPassword("crawler");

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-08-17T01-24-54Z")
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    private static WireMockServer wiremock;

    @BeforeAll
    static void startWiremock() {
        wiremock = new WireMockServer(options().dynamicPort());
        wiremock.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");

        registry.add("minio.endpoint", MINIO::getS3URL);
        registry.add("minio.access-key", MINIO::getUserName);
        registry.add("minio.secret-key", MINIO::getPassword);
        registry.add("minio.bucket", () -> "crawler-html");

        registry.add("frontier.base-url", () -> "http://localhost:" + wiremock.port());
    }

    @Autowired
    private CrawlPipelineService pipelineService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedPageWithTwoLinks_persistsAndPostsToFrontier() throws Exception {
        ensureBucket();
        wiremock.resetAll();
        wiremock.stubFor(get(urlMatching("/seed-page.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/html")
                        .withBody("<html><body><a href='/page-a'>a</a><a href='/page-b'>b</a></body></html>")));
        wiremock.stubFor(post(urlEqualTo("/urls"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"queued\"}")));

        String seedUrl = "http://localhost:" + wiremock.port() + "/seed-page";
        Url seed = Url.of(seedUrl);

        pipelineService.process(seed);

        Long visitedCount = jdbcTemplate.queryForObject("select count(*) from visited_url", Long.class);
        Long contentSeenCount = jdbcTemplate.queryForObject("select count(*) from content_seen", Long.class);
        assertThat(visitedCount).isEqualTo(1L);
        assertThat(contentSeenCount).isEqualTo(1L);

        long minioCount = countObjects("crawler-html");
        assertThat(minioCount).isEqualTo(1L);

        wiremock.verify(2, postRequestedFor(urlEqualTo("/urls")));

        // Re-run pipeline on same seed URL with same body — content_seen HIT path; no new MinIO put.
        pipelineService.process(seed);
        long minioAfterSecond = countObjects("crawler-html");
        assertThat(minioAfterSecond).isEqualTo(1L);
        Long visitedAfterSecond = jdbcTemplate.queryForObject("select count(*) from visited_url", Long.class);
        assertThat(visitedAfterSecond).isEqualTo(1L);
    }

    private void ensureBucket() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket("crawler-html").build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("crawler-html").build());
        }
    }

    private long countObjects(String bucket) {
        long count = 0;
        for (Result<Item> r : minioClient.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build())) {
            try {
                r.get();
                count++;
            } catch (Exception e) {
                throw new AssertionError("listObjects iteration failed", e);
            }
        }
        return count;
    }
}
