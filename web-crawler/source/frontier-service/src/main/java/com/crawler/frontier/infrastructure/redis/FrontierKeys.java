package com.crawler.frontier.infrastructure.redis;

public final class FrontierKeys {

    public static final String QUEUE_PRIORITY_PREFIX = "frontier:queue:priority:";
    public static final String QUEUE_DOMAIN_PREFIX = "frontier:queue:domain:";
    public static final String DOMAIN_SET = "frontier:domains";
    public static final String LEASE_PREFIX = "frontier:lease:";
    public static final String SIMPLE_QUEUE = "frontier:queue:simple";

    private FrontierKeys() {
    }

    public static String priorityQueue(String priority) {
        return QUEUE_PRIORITY_PREFIX + priority;
    }

    public static String domainQueue(String host) {
        return QUEUE_DOMAIN_PREFIX + host;
    }

    public static String lease(String host) {
        return LEASE_PREFIX + host;
    }
}
