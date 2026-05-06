package com.crawler.infra.minio;

import com.crawler.infra.config.MinioConfig;
import com.crawler.worker.domain.ContentHash;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = MinioContentStoreIT.TestApp.class)
class MinioContentStoreIT {

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-08-17T01-24-54Z")
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint", MINIO::getS3URL);
        registry.add("minio.access-key", MINIO::getUserName);
        registry.add("minio.secret-key", MINIO::getPassword);
        registry.add("minio.bucket", () -> "crawler-html");
    }

    @Autowired
    private MinioContentStore contentStore;

    @Autowired
    private MinioClient minioClient;

    @BeforeAll
    static void ensureBucket() {
    }

    @Test
    void put_returns_dated_storage_key_and_object_is_retrievable() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket("crawler-html").build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("crawler-html").build());
        }
        byte[] body = "<html>hello</html>".getBytes();
        ContentHash hash = ContentHash.of(body);

        String key = contentStore.put(hash, body);

        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertThat(key).isEqualTo(today + "/" + hash.hex() + ".html");

        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket("crawler-html").object(key).build())) {
            assertThat(in.readAllBytes()).isEqualTo(body);
        }
    }

    @SpringBootApplication(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class
    })
    @Import({MinioConfig.class, MinioContentStore.class})
    static class TestApp {
    }
}
