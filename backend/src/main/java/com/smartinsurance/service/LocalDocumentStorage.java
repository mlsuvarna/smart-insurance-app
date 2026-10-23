package com.smartinsurance.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "local", matchIfMissing = true)
public class LocalDocumentStorage implements DocumentStorage {
    private final Path root;
    public LocalDocumentStorage(@Value("${app.storage.local-path:./var/documents}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    @Override
    public void store(String key, byte[] content) {
        Path target = resolve(key);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target);
            }
        } catch (IOException ex) {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException cleanupFailure) { ex.addSuppressed(cleanupFailure); }
            }
            throw new IllegalStateException("Document storage write failed", ex);
        }
    }
    @Override
    public byte[] load(String key) {
        try { return Files.readAllBytes(resolve(key)); }
        catch (IOException ex) { throw new IllegalStateException("Document storage read failed", ex); }
    }
    @Override
    public void delete(String key) {
        try { Files.deleteIfExists(resolve(key)); }
        catch (IOException ex) { throw new IllegalStateException("Document storage cleanup failed", ex); }
    }
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return target;
    }
}
