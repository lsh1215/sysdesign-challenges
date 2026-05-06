package com.crawler.worker.domain.repository;

import com.crawler.worker.domain.exception.DownloadException;

public interface HtmlSource {

    byte[] download(String url) throws DownloadException;
}
