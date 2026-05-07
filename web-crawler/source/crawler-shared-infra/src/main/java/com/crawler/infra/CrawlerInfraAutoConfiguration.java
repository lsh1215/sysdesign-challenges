package com.crawler.infra;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for crawler infrastructure adapters. Registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} so
 * downstream consumers ({@code crawler-worker}, {@code freshness-scheduler}) pick up every
 * adapter without a compile-time {@code @Import}.
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.crawler.infra")
public class CrawlerInfraAutoConfiguration {
}
