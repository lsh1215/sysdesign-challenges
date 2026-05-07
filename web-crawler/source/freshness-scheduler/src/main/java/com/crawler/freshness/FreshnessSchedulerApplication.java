package com.crawler.freshness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FreshnessSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreshnessSchedulerApplication.class, args);
    }
}
