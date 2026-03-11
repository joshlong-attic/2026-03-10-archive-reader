package com.example.demo;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.annotation.*;
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

@Zip
@Component
class ZipArchiveExtractor implements ArchiveExtractor {

    @Override
    public void extract(InputStream stream, Consumer<ZipFile> zipFileConsumer) throws Exception {

        try (var zis = new ZipInputStream(stream)) {
            var entry = (ZipEntry) null;
            while ((entry = zis.getNextEntry()) != null) {
                try (var baos = new ByteArrayOutputStream();) {
                    StreamUtils.copy(zis, baos);
                    zis.closeEntry();
                    zipFileConsumer.accept(new ZipFile(
                            entry.getName(),
                            baos.toByteArray()));
                }
            }
        }
    }
}

@Tgz
@Component
class TgzArchiveExtractor implements ArchiveExtractor {

    @Override
    public void extract(InputStream stream, Consumer<ZipFile> zipFileConsumer) throws Exception {
        try (var gzi = new GzipCompressorInputStream(stream);
             var tar = new TarArchiveInputStream(gzi)) {
            var entry = (TarArchiveEntry) null;
            while ((entry = tar.getNextEntry()) != null) {
                try (var byteArrayOutputStream = new ByteArrayOutputStream()) {
                    StreamUtils.copy(tar, byteArrayOutputStream);
                    zipFileConsumer.accept(new ZipFile(
                            entry.getName(),
                            byteArrayOutputStream.toByteArray()));
                }
            }
        }
    }

}

record ZipFile(String fileName, byte[] content) {
}

interface ArchiveExtractor {

    void extract(InputStream stream, Consumer<ZipFile> zipFileConsumer) throws Exception;
}