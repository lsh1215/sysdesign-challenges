package com.crawler.infra.minio;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import com.crawler.infra.config.MinioProperties;
import com.crawler.worker.domain.ContentHash;
import com.crawler.worker.domain.repository.ContentStore;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioContentStore implements ContentStore {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public String put(ContentHash hash, byte[] body) {
        String storageKey = LocalDate.now(ZoneOffset.UTC).format(DATE_PATH) + "/" + hash.hex() + ".html";
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(storageKey)
                    .stream(new ByteArrayInputStream(body), body.length, -1)
                    .contentType("text/html")
                    .build());
            return storageKey;
        } catch (MinioException | IOException | GeneralSecurityException e) {
            log.warn("minio put failed bucket={} key={} reason={}", properties.bucket(), storageKey, e.getMessage());
            throw new BusinessException(CrawlerErrorCode.CONTENT_STORE_UNAVAILABLE);
        }
    }
}
