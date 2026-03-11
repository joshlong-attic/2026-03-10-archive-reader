package com.example.demo.archives;

import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

abstract class AbstractArchiveExtractor implements ArchiveExtractor {

    protected static final long MAX_TOTAL_SIZE = 1_000 * 1024 * 1024;

    protected static final long MAX_FILE_SIZE = 1_00 * 1024 * 1024;

    protected static final int MAX_ENTRIES = 100_000;

    protected InputStream validateMagicBytes(InputStream stream, byte[] expected) throws IOException {
        var pis = new PushbackInputStream(stream, expected.length);
        var header = new byte[expected.length];
        pis.read(header);
        if (!Arrays.equals(header, expected))
            throw new IllegalArgumentException("Invalid archive format");
        pis.unread(header); // put the bytes back so the downstream reader sees them
        return pis;
    }

    protected void shouldExitOnInsecureFile(String name) {
        Assert.state(!name.contains(".."),
        "the file " + name + " contains a path traversal");
    }

    protected boolean isNonReadableText(byte[] bytes) {
        var content = new String(bytes, StandardCharsets.UTF_8);
        var nonPrintable = content.chars()
                .filter(c -> c < 32 && c != '\n' && c != '\r' && c != '\t')
                .count();
        return !(nonPrintable < bytes.length * 0.01);
    }
}
