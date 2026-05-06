package com.crawler.infra.redis;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;
import com.crawler.infra.api.UrlSeenIndex;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlSeenIndexImpl implements UrlSeenIndex {

    public static final String BLOOM_KEY = "url:seen";

    private final RedisBloomClient bloomClient;

    @Override
    public boolean markIfAbsent(String url) {
        try {
            return bloomClient.bfAdd(BLOOM_KEY, url);
        } catch (RedisCommandExecutionException e) {
            log.warn("bloom filter command failed key={} url={} reason={}", BLOOM_KEY, url, e.getMessage());
            throw new BusinessException(CrawlerErrorCode.BLOOM_FILTER_UNAVAILABLE);
        }
    }
}
