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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@Table(name = "content_seen")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentSeenJpaEntity {

    @Id
    @Column(name = "hash", columnDefinition = "char(64)", nullable = false)
    private String hash;

    @Column(name = "storage_key", columnDefinition = "varchar(512)", nullable = false)
    private String storageKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private ContentSeenJpaEntity(String hash, String storageKey) {
        this.hash = hash;
        this.storageKey = storageKey;
    }

    public static ContentSeenJpaEntity of(String hash, String storageKey) {
        return new ContentSeenJpaEntity(hash, storageKey);
    }
}
