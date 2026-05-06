package com.crawler.worker.domain;

import com.crawler.common.exception.BusinessException;
import com.crawler.common.exception.CrawlerErrorCode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ContentHash {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    private final String hex;

    private ContentHash(String hex) {
        this.hex = hex;
    }

    public static ContentHash of(byte[] body) {
        if (body == null) {
            throw new BusinessException(CrawlerErrorCode.INVALID_CONTENT_HASH);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(body);
            return new ContentHash(toHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(CrawlerErrorCode.INVALID_CONTENT_HASH);
        }
    }

    public static ContentHash fromHex(String hex) {
        if (hex == null || !HEX_64.matcher(hex).matches()) {
            throw new BusinessException(CrawlerErrorCode.INVALID_CONTENT_HASH);
        }
        return new ContentHash(hex);
    }

    public String hex() {
        return hex;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentHash other)) return false;
        return Objects.equals(hex, other.hex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hex);
    }

    @Override
    public String toString() {
        return hex;
    }
}
