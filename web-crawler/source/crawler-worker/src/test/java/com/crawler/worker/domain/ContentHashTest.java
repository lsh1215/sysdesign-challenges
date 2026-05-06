package com.crawler.worker.domain;

import com.crawler.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentHashTest {

    @Test
    void sameBodyProducesSameHash() {
        ContentHash a = ContentHash.of("hello".getBytes());
        ContentHash b = ContentHash.of("hello".getBytes());

        assertEquals(a, b);
        assertEquals(a.hex(), b.hex());
    }

    @Test
    void differentBodyProducesDifferentHash() {
        ContentHash a = ContentHash.of("hello".getBytes());
        ContentHash b = ContentHash.of("world".getBytes());

        assertNotEquals(a, b);
    }

    @Test
    void hashIsLowercase64HexChars() {
        ContentHash hash = ContentHash.of("payload".getBytes());

        assertEquals(64, hash.hex().length());
        assertEquals(hash.hex(), hash.hex().toLowerCase());
    }

    @Test
    void fromHexValidatesLength() {
        assertThrows(BusinessException.class, () -> ContentHash.fromHex("abc"));
    }

    @Test
    void fromHexValidatesHexCharacters() {
        String invalid = "z".repeat(64);
        assertThrows(BusinessException.class, () -> ContentHash.fromHex(invalid));
    }

    @Test
    void fromHexAcceptsValidHex() {
        String valid = "0".repeat(64);
        ContentHash hash = ContentHash.fromHex(valid);
        assertEquals(valid, hash.hex());
    }
}
