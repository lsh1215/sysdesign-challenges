package com.crawler.infra.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.stream.Stream;

public interface VisitedUrlJpaRepository extends JpaRepository<VisitedUrlJpaEntity, String> {

    Stream<VisitedUrlJpaEntity> findByLastCrawledAtBefore(Instant cutoff, Pageable page);
}
