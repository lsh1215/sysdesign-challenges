package com.crawler.infra;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Single import surface for consumers ({@code crawler-worker}, {@code freshness-scheduler}). They
 * import this class with {@code @Import(CrawlerInfraAutoConfiguration.class)} from their main
 * application class to pick up every infrastructure adapter without scanning the whole module.
 */
@Configuration
@ComponentScan(basePackages = "com.crawler.infra")
public class CrawlerInfraAutoConfiguration {
}
