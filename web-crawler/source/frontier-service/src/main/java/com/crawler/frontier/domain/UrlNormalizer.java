package com.crawler.frontier.domain;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class UrlNormalizer {

    private UrlNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
        try {
            URI uri = new URI(raw.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                throw new BusinessException(CrawlerErrorCode.INVALID_URL);
            }
            scheme = scheme.toLowerCase();
            host = host.toLowerCase();

            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            // strip trailing slash only when path is empty or just "/"
            if (path.equals("/")) {
                path = "";
            }

            String sortedQuery = sortQuery(uri.getRawQuery());

            int port = uri.getPort();
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port != -1) {
                sb.append(':').append(port);
            }
            sb.append(path);
            if (sortedQuery != null && !sortedQuery.isEmpty()) {
                sb.append('?').append(sortedQuery);
            }
            // fragment intentionally dropped
            return sb.toString();
        } catch (URISyntaxException e) {
            throw new BusinessException(CrawlerErrorCode.INVALID_URL);
        }
    }

    private static String sortQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        return Arrays.stream(rawQuery.split("&"))
                .filter(s -> !s.isEmpty())
                .sorted(Comparator.comparing(UrlNormalizer::keyOf))
                .collect(Collectors.joining("&"));
    }

    private static String keyOf(String pair) {
        int eq = pair.indexOf('=');
        return eq < 0 ? pair : pair.substring(0, eq);
    }
}
