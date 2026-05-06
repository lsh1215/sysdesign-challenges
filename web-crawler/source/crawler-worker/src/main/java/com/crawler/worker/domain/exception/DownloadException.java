package com.crawler.worker.domain.exception;

public class DownloadException extends Exception {

    private final String url;
    private final String reason;

    public DownloadException(String url, String reason, Throwable cause) {
        super("download failed url=" + url + " reason=" + reason, cause);
        this.url = url;
        this.reason = reason;
    }

    public String getUrl() {
        return url;
    }

    public String getReason() {
        return reason;
    }
}
