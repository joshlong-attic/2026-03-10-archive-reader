package com.example.demo.archives;

import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Zip
@Component
class ZipArchiveExtractor
        extends AbstractArchiveExtractor
        implements ArchiveExtractor {

    @Override
    public void extract(InputStream stream, Consumer<ArchiveFile> zipFileConsumer) throws Exception {

        stream = this.validateMagicBytes(stream, new byte[]{0x50, 0x4B, 0x03, 0x04});

        var totalSize = 0L;
        var entryCount = 0;

        try (var zis = new ZipInputStream(stream)) {
            var entry = (ZipEntry) null;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES)
                    throw new SecurityException("Too many entries in archive");

                this.shouldExitOnInsecureFile(entry.getName());

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
                        continue;

                    zipFileConsumer.accept(new ArchiveFile(entry.getName(), bytes));
                }
            }
        }
    }


}

