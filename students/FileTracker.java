package impl;

import interfaces.Trackable;
import model.*;
import utils.*;
import exceptions.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FileTracker implements Trackable {
    private final String repositoryPath;
    private final Map<String, FileMetadata> fileMetadata;
    private final Set<String> trackedFiles;
    private final List<Consumer<String>> fileChangeListeners;

    public FileTracker(String repositoryPath) throws FileOperationException {
        this.repositoryPath = repositoryPath;
        this.fileMetadata = new ConcurrentHashMap<>();
        this.trackedFiles = Collections.synchronizedSet(new HashSet<>());
        this.fileChangeListeners = new ArrayList<>();
        try {
            Files.createDirectories(Paths.get(repositoryPath, ".vcs", "objects"));
        } catch (IOException e) {
            throw new FileOperationException("Failed to initialize repository structure", e);
        }
    }

    public boolean trackFile(File file) throws VCSException, IOException {
        // -- BEGIN FILL CODE HERE -- *

        // -- ENDED FILL CODE HERE -- *
    }

    public void trackFile(String filePath) throws VCSException, IOException {
        // -- BEGIN FILL CODE HERE -- *

        // -- ENDED FILL CODE HERE -- *
    }

    public void untrackFile(String filePath) {
        // -- BEGIN FILL CODE HERE -- *

        // -- ENDED FILL CODE HERE -- *
    }

    public List<String> getTrackedFiles() {
        // -- BEGIN FILL CODE HERE -- *

        // -- ENDED FILL CODE HERE -- *
    }

    public Map<String, FileStatus> getFileStatuses() {
        // -- BEGIN FILL CODE HERE -- *

        // -- ENDED FILL CODE HERE -- *
    }

    @Override
    public void addFileChangeListener(Consumer<String> listener) {
        // -- BEGIN FILL CODE HERE -- *

        // -- ENDED FILL CODE HERE -- *
    }

    @Override
    public void removeFileChangeListener(Consumer<String> listener) {
        // -- BEGIN FILL CODE HERE -- *

        // -- ENDED FILL CODE HERE -- *
    }

    private void notifyFileChanged(String filePath) {
        for (Consumer<String> listener : fileChangeListeners) {
            listener.accept(filePath);
        }
    }

    public void updateFileStatus(String filePath) throws VCSException {
        FileMetadata metadata = fileMetadata.get(filePath);
        if (metadata != null) {
            File file = new File(filePath);
            if (!file.exists()) {
                metadata.setStatus(FileStatus.DELETED);
            } else {
                String newHash = storeFileContent(file);
                if (!newHash.equals(metadata.getCurrentHash())) {
                    metadata.setStatus(FileStatus.MODIFIED);
                    metadata.setCurrentHash(newHash);
                }
            }
            notifyFileChanged(filePath);
        }
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

    public void commitFile(String filePath, String versionId) {
        FileMetadata metadata = fileMetadata.get(filePath);
        if (metadata != null) {
            metadata.addVersion(versionId);
            metadata.setStatus(FileStatus.TRACKED);
            notifyFileChanged(filePath);
        }
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