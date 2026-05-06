package com.crawler.infra.api;

public interface UrlSeenIndex {

    boolean markIfAbsent(String url);
}
