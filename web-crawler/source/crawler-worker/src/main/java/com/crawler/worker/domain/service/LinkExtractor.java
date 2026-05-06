package com.crawler.worker.domain.service;

import java.util.List;

public interface LinkExtractor {

    List<String> extract(ParsedDoc doc, String sourceUrl);
}
