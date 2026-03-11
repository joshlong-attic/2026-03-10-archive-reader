package com.example.demo;

import com.example.demo.archives.ArchiveExtractor;
import com.example.demo.archives.ArchiveFile;
import com.example.demo.archives.Tgz;
import com.example.demo.archives.Zip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    private static final String FILE_PREFIX = "file:///${HOME}/Desktop/in.";
    private static final String TGZ = "tgz";
    private static final String ZIP = "zip";

    @Bean
    ArchiveRunner tgzArchiveRunner(
            @Tgz ArchiveExtractor archiveExtractor,
            @Value(FILE_PREFIX + TGZ) Resource resource) {
        return new ArchiveRunner(archiveExtractor, resource);
    }

    //   @Bean
    ArchiveRunner zipArchiveRunner(
            @Zip ArchiveExtractor archiveExtractor,
            @Value(FILE_PREFIX + ZIP) Resource resource) {
        return new ArchiveRunner(archiveExtractor, resource);
    }

}

class ArchiveRunner implements ApplicationRunner {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ArchiveExtractor archiveExtractor;
    private final Resource resource;

    ArchiveRunner(ArchiveExtractor archiveExtractor, Resource resource) {
        this.archiveExtractor = archiveExtractor;
        this.resource = resource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (var in = this.resource.getInputStream()) {
            var path = this.resource.getFile().toPath();
            Assert.isTrue(this.isZip(path) ||
                    this.isTarGz(path), "Unsupported archive format");

            this.log.info("extracting from {}", path);
            this.archiveExtractor.extract(in, archiveFile -> {
                if (this.isValidMarkdownFile(archiveFile)) {
                    try {
                        this.process(archiveFile);
                    } //
                    catch (Exception e) {
                        log.warn("failed to process {}", archiveFile.fileName(), e);
                    }
                }
            });
        }

    }

    private byte[] readNBytes(Path path, int n) throws IOException {
        try (var is = Files.newInputStream(path)) {
            Assert.isTrue(n <= is.available(), "n > available bytes");
            return is.readNBytes(n);
        }
    }

    private boolean isZip(Path path) throws IOException {
        var header = this.readNBytes(path, 4);
        // ZIP magic bytes: PK\x03\x04
        return header[0] == 0x50 && header[1] == 0x4B
                && header[2] == 0x03 && header[3] == 0x04;
    }

    private boolean isTarGz(Path path) throws IOException {
        var header = this.readNBytes(path, 2);
        // GZIP magic bytes: \x1f\x8b
        return header[0] == (byte) 0x1f && header[1] == (byte) 0x8b;
    }

    private boolean isValidMarkdownFile(ArchiveFile file) {
        return this.guessMimeType(file.fileName())
                .isCompatibleWith(MediaType.TEXT_MARKDOWN);
    }

    private MediaType guessMimeType(String name) {
        var guessContentTypeFromName =
                java.net.URLConnection.guessContentTypeFromName(name);
        return StringUtils.hasText(guessContentTypeFromName) ?
                MediaType.parseMediaType(guessContentTypeFromName) :
                MediaType.APPLICATION_OCTET_STREAM;
    }

    private void process(ArchiveFile archiveFile) {
        this.log.info("========================================");
        this.log.info("file = {}", archiveFile.fileName() + ":" +
                archiveFile.content().length);
        var content = new String(archiveFile.content(), Charset.defaultCharset());
        var frontMatter = FrontMatter.parse(content);
        frontMatter.forEach((k, v) -> this.log.info("{}={}", k, v));
    }
}

abstract class FrontMatter {

    static Map<String, Object> parse(String content) {
        if (!content.startsWith("---")) return Map.of();

        var end = content.indexOf("---", 3);
        if (end == -1) return Map.of();

        var yaml = content.substring(3, end).trim();
        return new Yaml().load(yaml);
    }

    static String body(String content) {
        if (!content.startsWith("---")) return content;
        var end = content.indexOf("---", 3);
        return end == -1 ? content : content.substring(end + 3).trim();
    }
}
