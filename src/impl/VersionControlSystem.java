package impl;

import interfaces.*;
import model.*;
import utils.*;
import exceptions.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;

public class VersionControlSystem implements Uploadable, Versionable, Trackable {
    private final String repositoryPath;
//    private final Map<String, FileMetadata> fileMetadata;
    private final List<VersionInfo> versionHistory;
    private final List<Consumer<String>> fileChangeListeners;
    private final FileTracker fileTracker;
    private final VersionManager versionManager;
    private final DiffGenerator diffGenerator;
    private final MergeHandler mergeHandler;

    public VersionControlSystem(String repositoryPath) throws FileOperationException {
        this.repositoryPath = repositoryPath;
//        this.fileMetadata = new ConcurrentHashMap<>();
        this.versionHistory = new ArrayList<>();
        this.fileChangeListeners = new ArrayList<>();

        this.fileTracker = new FileTracker(repositoryPath);
        this.versionManager = new VersionManager(repositoryPath);
        this.diffGenerator = new DiffGenerator(versionManager, fileTracker);
        this.mergeHandler = new MergeHandler(versionManager);

        initializeRepository();
    }

    private void initializeRepository() throws FileOperationException {
        FileUtils.createDirectoryIfNotExists(repositoryPath + "/.vcs");
        FileUtils.createDirectoryIfNotExists(repositoryPath + "/.vcs/objects");
        FileUtils.createDirectoryIfNotExists(repositoryPath + "/.vcs/versions");
    }

    public FileTracker getFileTracker() {
        return fileTracker;
    }

    public MergeHandler getMergeHandler() {
        return mergeHandler;
    }

    public DiffGenerator getDiffGenerator() {
        return diffGenerator;
    }

    @Override
    public boolean upload(File file) throws VCSException, IOException {
        if (file == null || !file.exists()) {
            throw new FileOperationException("Invalid file: " + (file != null ? file.getPath() : "null"));
        }
        trackFile(file.getPath());
        return true;
    }

    @Override
    public boolean uploadDirectory(File directory) throws VCSException, IOException {
        if (!directory.isDirectory()) {
            throw new FileOperationException("Not a directory: " + directory.getPath());
        }
        boolean success = true;
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isFile()) {
                success &= upload(file);
            }
        }
        return success;
    }

    @Override
    public List<File> getUploadedFiles() {
        return fileTracker.getTrackedFiles().stream()
                .map(File::new)
                .filter(File::exists)
                .toList();
    }

    @Override
    public void removeFile(String filePath) throws VCSException {
        fileTracker.untrackFile(filePath);
        notifyFileChanged(filePath);
    }

    @Override
    public String createVersion(String message) throws VCSException {
        Map<String, String> currentHashes = new HashMap<>();
        for (String filePath : fileTracker.getTrackedFiles()) {
            String hash = fileTracker.getFileHash(filePath);
            if (hash != null) {
                currentHashes.put(filePath, hash);
            }
        }
        String versionId = versionManager.createVersion(message, currentHashes);
        versionHistory.add(versionManager.getVersion(versionId));

        for (String filePath : currentHashes.keySet()) {
            fileTracker.commitFile(filePath, versionId);
        }

        return versionId;
    }

    @Override
    public void revertToVersion(String versionId) throws VCSException, IOException {
        VersionInfo version = versionManager.getVersion(versionId);
        if (version == null) {
            throw new VersionException("Version not found: " + versionId);
        }

        for (Map.Entry<String, String> entry : version.getFileHashes().entrySet()) {
            String filePath = entry.getKey();
            String hash = entry.getValue();
            Path sourceFile = Paths.get(repositoryPath, ".vcs", "objects", hash);
            Path targetFile = Paths.get(filePath);

            Files.createDirectories(targetFile.getParent());
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

            FileMetadata metadata = fileTracker.getFileMetadata(filePath);
            if (metadata != null) {
                metadata.setCurrentHash(hash);
            }
            notifyFileChanged(filePath);
        }
    }

    @Override
    public List<VersionInfo> getVersionHistory() {
        return new ArrayList<>(versionHistory);
    }

    @Override
    public VersionInfo getCurrentVersion() {
        if (versionHistory.isEmpty()) {
            return null;
        }
        return versionHistory.getLast();
    }

    @Override
    public void trackFile(String filePath) throws VCSException, IOException {
        fileTracker.trackFile(filePath);
        notifyFileChanged(filePath);
    }

    @Override
    public void untrackFile(String filePath) throws VCSException {
        fileTracker.untrackFile(filePath);
        notifyFileChanged(filePath);
    }

    @Override
    public List<String> getTrackedFiles() {
        return fileTracker.getTrackedFiles();
    }

    @Override
    public Map<String, FileStatus> getFileStatuses() {
        Map<String, FileStatus> statuses = new HashMap<>();
        for (String filePath : fileTracker.getTrackedFiles()) {
            FileMetadata metadata = fileTracker.getFileMetadata(filePath);
            if (metadata != null) {
                statuses.put(filePath, metadata.getStatus());
            }
        }
        return statuses;
    }

    @Override
    public void addFileChangeListener(Consumer<String> listener) {
        fileChangeListeners.add(listener);
    }

    @Override
    public void removeFileChangeListener(Consumer<String> listener) {
        fileChangeListeners.remove(listener);
    }

    private void notifyFileChanged(String filePath) {
        for (Consumer<String> listener : fileChangeListeners) {
            listener.accept(filePath);
        }
    }
}