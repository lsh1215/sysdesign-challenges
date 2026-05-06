package com.crawler.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@Table(name = "visited_url")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitedUrlJpaEntity {

    @Id
    @Column(name = "url", columnDefinition = "varchar(2048)", nullable = false)
    private String url;

    @Column(name = "domain", columnDefinition = "varchar(255)", nullable = false)
    private String domain;

    @Column(name = "last_crawled_at", nullable = false)
    private Instant lastCrawledAt;

    @Column(name = "last_content_hash", columnDefinition = "char(64)", nullable = false)
    private String lastContentHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private VisitedUrlJpaEntity(String url, String domain, Instant lastCrawledAt, String lastContentHash) {
        this.url = url;
        this.domain = domain;
        this.lastCrawledAt = lastCrawledAt;
        this.lastContentHash = lastContentHash;
    }

    public static VisitedUrlJpaEntity of(String url, String domain, Instant lastCrawledAt, String lastContentHash) {
        return new VisitedUrlJpaEntity(url, domain, lastCrawledAt, lastContentHash);
    }

    public void update(Instant lastCrawledAt, String lastContentHash) {
        this.lastCrawledAt = lastCrawledAt;
        this.lastContentHash = lastContentHash;
    }
}
