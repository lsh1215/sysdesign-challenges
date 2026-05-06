package com.crawler.infra.persistence;

import com.crawler.worker.domain.ContentHash;
import com.crawler.worker.domain.repository.ContentSeenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ContentSeenRepositoryImpl implements ContentSeenRepository {

    private final ContentSeenJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean exists(ContentHash hash) {
        return jpaRepository.existsById(hash.hex());
    }

    @Override
    @Transactional
    public void record(ContentHash hash, String storageKey) {
        if (jpaRepository.existsById(hash.hex())) {
            return;
        }
        jpaRepository.save(ContentSeenJpaEntity.of(hash.hex(), storageKey));
    }
}
