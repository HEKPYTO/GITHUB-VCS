package models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FileMetadata {
    private final String filePath;
    private String currentHash;
    private LocalDateTime lastModified;
    private final List<String> versions;
    private FileStatus status;

    public FileMetadata(String filePath, String currentHash) {
        this.filePath = filePath;
        this.currentHash = currentHash;
        this.lastModified = LocalDateTime.now();
        this.versions = new ArrayList<>();
        this.status = FileStatus.TRACKED;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public void setCurrentHash(String hash) {
        if (!hash.equals(this.currentHash)) {
            this.status = FileStatus.MODIFIED;
        }
        this.currentHash = hash;
        this.lastModified = LocalDateTime.now();
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public List<String> getVersions() {
        return new ArrayList<>(versions);
    }

    public void addVersion(String versionId) {
        this.versions.add(versionId);
        this.status = FileStatus.TRACKED;  // Reset status after version creation
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }
}