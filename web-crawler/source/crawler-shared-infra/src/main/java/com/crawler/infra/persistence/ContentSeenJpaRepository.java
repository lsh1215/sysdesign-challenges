package com.crawler.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentSeenJpaRepository extends JpaRepository<ContentSeenJpaEntity, String> {
}
