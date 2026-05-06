package com.crawler.worker.domain.service;

import com.crawler.worker.domain.exception.ParseException;

public interface ContentParser {

    ParsedDoc parse(byte[] body, String sourceUrl) throws ParseException;
}
