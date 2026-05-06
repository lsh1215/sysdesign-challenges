package com.crawler.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCodeBase {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
