package model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VersionInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String versionId;
    private final String message;
    private final LocalDateTime timestamp;
    private final String author;
    private final Map<String, String> fileHashes;

    public VersionInfo(String message, String author, Map<String, String> fileHashes) {
        this.versionId = UUID.randomUUID().toString();
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.author = author;
        this.fileHashes = new HashMap<>(fileHashes);
    }

    public String getVersionId() {
        return versionId;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getAuthor() {
        return author;
    }

    public Map<String, String> getFileHashes() {
        return new HashMap<>(fileHashes);
    }

    @Override
    public String toString() {
        return String.format("Version[id=%s, message='%s', author='%s', files=%d]",
                versionId, message, author, fileHashes.size());
    }
}