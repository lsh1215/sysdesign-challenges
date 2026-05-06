package com.crawler.infra.persistence;

import com.crawler.worker.domain.ContentHash;
import com.crawler.worker.domain.VisitedUrl;
import com.crawler.worker.domain.repository.VisitedUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class VisitedUrlRepositoryImpl implements VisitedUrlRepository {

    private final VisitedUrlJpaRepository jpaRepository;

    @Override
    @Transactional
    public void save(VisitedUrl visitedUrl) {
        Optional<VisitedUrlJpaEntity> existing = jpaRepository.findById(visitedUrl.getUrl());
        if (existing.isPresent()) {
            existing.get().update(visitedUrl.getLastCrawledAt(), visitedUrl.getLastContentHash().hex());
            return;
        }
        VisitedUrlJpaEntity entity = VisitedUrlJpaEntity.of(
                visitedUrl.getUrl(),
                visitedUrl.getDomain().host(),
                visitedUrl.getLastCrawledAt(),
                visitedUrl.getLastContentHash().hex()
        );
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VisitedUrl> findByUrl(String url) {
        return jpaRepository.findById(url).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Stream<VisitedUrl> findStaleSince(Instant cutoff, int batchSize) {
        // Materialize within the transaction so the consumer can read the Stream after the
        // transactional method returns without "Connection is closed" errors.
        try (Stream<VisitedUrlJpaEntity> source =
                     jpaRepository.findByLastCrawledAtBefore(cutoff, PageRequest.of(0, batchSize))) {
            return source.map(this::toDomain).toList().stream();
        }
    }

    private VisitedUrl toDomain(VisitedUrlJpaEntity entity) {
        return VisitedUrl.record(
                entity.getUrl(),
                entity.getLastCrawledAt(),
                ContentHash.fromHex(entity.getLastContentHash())
        );
    }
}
