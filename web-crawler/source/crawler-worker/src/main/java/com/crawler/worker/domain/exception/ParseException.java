package com.crawler.worker.domain.exception;

public class ParseException extends Exception {

    private final String url;
    private final String reason;

    public ParseException(String url, String reason, Throwable cause) {
        super("parse failed url=" + url + " reason=" + reason, cause);
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
