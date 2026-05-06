package com.crawler.frontier.domain;

public enum CrawlPriority {
    HIGH(5),
    MEDIUM(3),
    LOW(1);

    private final int weight;

    CrawlPriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
