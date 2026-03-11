package com.example.demo;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Qualifier("zip")
@interface Zip {
}

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Qualifier("tgz")
@interface Tgz {
}

record ZipFile(String fileName, byte[] content) {
}

@Zip
@Component
class ZipArchiveExtractor
        extends AbstractArchiveExtractor
        implements ArchiveExtractor {

    @Override
    public void extract(InputStream stream, Consumer<ZipFile> zipFileConsumer) throws Exception {

        stream = this.validateMagicBytes(stream, new byte[]{0x50, 0x4B, 0x03, 0x04});

        var totalSize = 0L;
        var entryCount = 0;

        try (var zis = new ZipInputStream(stream)) {
            var entry = (ZipEntry) null;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES)
                    throw new SecurityException("Too many entries in archive");

                if (isUnsafeEntry(entry.getName(), entry.isDirectory()))
                    continue;

                try (var baos = new ByteArrayOutputStream()) {
                    StreamUtils.copy(zis, baos);
                    zis.closeEntry();

                    var bytes = baos.toByteArray();

                    if (bytes.length > MAX_FILE_SIZE)
                        throw new SecurityException("Entry too large: " + entry.getName());

                    totalSize += bytes.length;
                    if (totalSize > MAX_TOTAL_SIZE)
                        throw new SecurityException("Archive exceeds max uncompressed size");

                    if (isNonReadableText(bytes))
                        throw new SecurityException("Non-text content in: " + entry.getName());

                    zipFileConsumer.accept(new ZipFile(entry.getName(), bytes));
                }
            }
        }
    }
}

@Tgz
@Component
class TgzArchiveExtractor extends AbstractArchiveExtractor
        implements ArchiveExtractor {


    @Override
    public void extract(InputStream stream, Consumer<ZipFile> zipFileConsumer) throws Exception {

        stream = this.validateMagicBytes(stream, new byte[]{(byte) 0x1f, (byte) 0x8b});

        var totalSize = 0L;
        var entryCount = 0;

        try (var gzi = new GzipCompressorInputStream(stream);
             var tar = new TarArchiveInputStream(gzi)) {
            var entry = (TarArchiveEntry) null;
            while ((entry = tar.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES)
                    throw new SecurityException("Too many entries in archive");

                if (isUnsafeEntry(entry.getName(), entry.isDirectory()))
                    continue;

                try (var baos = new ByteArrayOutputStream()) {
                    StreamUtils.copy(tar, baos);

                    byte[] bytes = baos.toByteArray();

                    if (bytes.length > MAX_FILE_SIZE)
                        throw new SecurityException("Entry too large: " + entry.getName());

                    totalSize += bytes.length;
                    if (totalSize > MAX_TOTAL_SIZE)
                        throw new SecurityException("Archive exceeds max uncompressed size");

                    if (isNonReadableText(bytes))
                        throw new SecurityException("Non-text content in: " + entry.getName());

                    zipFileConsumer.accept(new ZipFile(entry.getName(), bytes));
                }
            }
        }
    }
}

abstract class AbstractArchiveExtractor implements ArchiveExtractor {

    protected static final long MAX_TOTAL_SIZE = 100 * 1024 * 1024;

    protected static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

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


    protected boolean isUnsafeEntry(String name, boolean isDirectory) {
        return isDirectory
                || !name.endsWith(".md")
                || name.contains("..");
    }

    protected boolean isNonReadableText(byte[] bytes) {
        var content = new String(bytes, StandardCharsets.UTF_8);
        var nonPrintable = content.chars()
                .filter(c -> c < 32 && c != '\n' && c != '\r' && c != '\t')
                .count();
        return !(nonPrintable < bytes.length * 0.01);
    }
}

interface ArchiveExtractor {

    void extract(InputStream stream, Consumer<ZipFile> zipFileConsumer) throws Exception;

}