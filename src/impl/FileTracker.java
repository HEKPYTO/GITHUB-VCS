package impl;

import model.*;
import utils.*;
import exceptions.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileTracker {
    private final String repositoryPath;
    private final Map<String, FileMetadata> fileMetadata;
    private final Set<String> trackedFiles;

    public FileTracker(String repositoryPath) throws FileOperationException {
        this.repositoryPath = repositoryPath;
        this.fileMetadata = new ConcurrentHashMap<>();
        this.trackedFiles = Collections.synchronizedSet(new HashSet<>());
        try {
            Files.createDirectories(Paths.get(repositoryPath, ".vcs", "objects"));
        } catch (IOException e) {
            throw new FileOperationException("Failed to initialize repository structure", e);
        }
    }

    public boolean trackFile(File file) throws VCSException, IOException {
        if (!file.exists() || !file.isFile()) {
            throw new FileOperationException("Invalid file: " + file.getPath());
        }

        String hash = HashUtils.calculateFileHash(file);
        Path objectsPath = Paths.get(repositoryPath, ".vcs", "objects");
        Files.copy(file.toPath(), objectsPath.resolve(hash), StandardCopyOption.REPLACE_EXISTING);

        FileMetadata metadata = new FileMetadata(file.getPath(), hash);
        fileMetadata.put(file.getPath(), metadata);
        trackedFiles.add(file.getPath());

        return true;
    }

    private String storeFileContent(File file) throws VCSException {
        try {
            String hash = HashUtils.calculateFileHash(file);
            Path objectsPath = Paths.get(repositoryPath, ".vcs", "objects");
            if (!Files.exists(objectsPath)) {
                Files.createDirectories(objectsPath);
            }

            Path objectFile = objectsPath.resolve(hash);
            synchronized(this) {
                if (!Files.exists(objectFile)) {
                    Files.copy(file.toPath(), objectFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return hash;
        } catch (IOException e) {
            throw new FileOperationException("Failed to store file content: " + file.getPath(), e);
        }
    }

    public void updateFileStatus(String filePath) throws VCSException {
        FileMetadata metadata = fileMetadata.get(filePath);
        if (metadata != null) {
            File file = new File(filePath);
            if (!file.exists()) {
                metadata.setStatus(FileStatus.DELETED);
            } else {
                String newHash = storeFileContent(file);  // Store the new content first
                if (!newHash.equals(metadata.getCurrentHash())) {
                    metadata.setStatus(FileStatus.MODIFIED);
                    metadata.setCurrentHash(newHash);
                }
            }
        }
    }

    public void commitFile(String filePath, String versionId) {
        FileMetadata metadata = fileMetadata.get(filePath);
        if (metadata != null) {
            metadata.addVersion(versionId);
            metadata.setStatus(FileStatus.TRACKED);
        }
    }

    public void untrackFile(String filePath) throws VCSException {
        fileMetadata.remove(filePath);
        trackedFiles.remove(filePath);
    }

    public List<File> getTrackedFiles() {
        return trackedFiles.stream()
                .map(File::new)
                .filter(File::exists)
                .toList();
    }

    public FileMetadata getFileMetadata(String filePath) {
        return fileMetadata.get(filePath);
    }

    public boolean isFileModified(String filePath) throws VCSException {
        FileMetadata metadata = fileMetadata.get(filePath);
        if (metadata == null) {
            return false;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return true;
        }

        String currentHash = HashUtils.calculateFileHash(file);
        return !currentHash.equals(metadata.getCurrentHash());
    }

    public String getFileHash(String filePath) {
        FileMetadata metadata = fileMetadata.get(filePath);
        return metadata != null ? metadata.getCurrentHash() : null;
    }
}