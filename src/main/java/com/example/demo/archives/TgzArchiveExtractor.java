package com.example.demo.archives;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.function.Consumer;

@Tgz
@Component
class TgzArchiveExtractor extends AbstractArchiveExtractor
        implements ArchiveExtractor {

    @Override
    public void extract(InputStream stream, Consumer<ArchiveFile> zipFileConsumer) throws Exception {

        stream = this.validateMagicBytes(stream, new byte[]{(byte) 0x1f, (byte) 0x8b});

        var totalSize = 0L;
        var entryCount = 0;

        try (var gzi = new GzipCompressorInputStream(stream);
             var tar = new TarArchiveInputStream(gzi)) {
            var entry = (TarArchiveEntry) null;
            while ((entry = tar.getNextEntry()) != null) {

                if (++entryCount > MAX_ENTRIES)
                    throw new SecurityException("Too many entries in archive");

                this.shouldExitOnInsecureFile(entry.getName());

                try (var baos = new ByteArrayOutputStream()) {
                    StreamUtils.copy(tar, baos);

                    byte[] bytes = baos.toByteArray();

                    if (bytes.length > MAX_FILE_SIZE)
                        throw new SecurityException("Entry too large: " + entry.getName());

                    totalSize += bytes.length;
                    if (totalSize > MAX_TOTAL_SIZE)
                        throw new SecurityException("Archive exceeds max uncompressed size");

                    if (isNonReadableText(bytes))
                        continue;

                    zipFileConsumer.accept(new ArchiveFile(entry.getName(), bytes));
                }
            }
        }
    }
}
