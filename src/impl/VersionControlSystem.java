package impl;

import interfaces.*;
import models.*;
import utils.*;
import exceptions.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class VersionControlSystem implements Uploadable, Versionable, Trackable {
    private final String repositoryPath;
    private final Map<String, FileMetadata> fileMetadata;
    private final List<VersionInfo> versionHistory;
    private final Set<String> trackedFiles;
    private final List<Consumer<String>> fileChangeListeners;
    private final FileTracker fileTracker;
    private final VersionManager versionManager;
    private final DiffGenerator diffGenerator;
    private final MergeHandler mergeHandler;

    public VersionControlSystem(String repositoryPath) throws FileOperationException {
        this.repositoryPath = repositoryPath;
        this.fileMetadata = new ConcurrentHashMap<>();
        this.versionHistory = new ArrayList<>();
        this.trackedFiles = new HashSet<>();
        this.fileChangeListeners = new ArrayList<>();

        this.fileTracker = new FileTracker(repositoryPath);
        this.versionManager = new VersionManager(repositoryPath);
        this.diffGenerator = new DiffGenerator(versionManager, fileTracker);
        this.mergeHandler = new MergeHandler(versionManager, diffGenerator);

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
    public boolean upload(File file) throws VCSException {
        return fileTracker.trackFile(file);
    }

    @Override
    public boolean uploadDirectory(File directory) throws VCSException {
        if (!directory.isDirectory()) {
            throw new FileOperationException("Not a directory: " + directory.getPath());
        }
        boolean success = true;
        for (File file : directory.listFiles()) {
            if (file.isFile()) {
                success &= upload(file);
            }
        }
        return success;
    }

    @Override
    public List<File> getUploadedFiles() {
        return fileTracker.getTrackedFiles();
    }

    @Override
    public void removeFile(String filePath) throws VCSException {
        fileTracker.untrackFile(filePath);
        fileMetadata.remove(filePath);
        trackedFiles.remove(filePath);
        notifyFileChanged(filePath);
    }

    @Override
    public String createVersion(String message) throws VCSException {
        Map<String, String> currentHashes = new HashMap<>();
        for (String filePath : trackedFiles) {
            String hash = fileTracker.getFileHash(filePath);
            if (hash != null) {
                currentHashes.put(filePath, hash);
            }
        }
        String versionId = versionManager.createVersion(message, currentHashes);
        versionHistory.add(versionManager.getVersion(versionId));
        return versionId;
    }

    @Override
    public void revertToVersion(String versionId) throws VCSException {
        VersionInfo version = versionManager.getVersion(versionId);
        if (version == null) {
            throw new VersionException("Version not found: " + versionId);
        }

        for (Map.Entry<String, String> entry : version.getFileHashes().entrySet()) {
            String filePath = entry.getKey();
            String hash = entry.getValue();
            File sourceFile = new File(repositoryPath + "/.vcs/objects/" + hash);
            File targetFile = new File(filePath);
            FileUtils.copyFile(sourceFile, targetFile);

            FileMetadata metadata = fileMetadata.get(filePath);
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
        return versionHistory.get(versionHistory.size() - 1);
    }

    @Override
    public void trackFile(String filePath) throws VCSException {
        File file = new File(filePath);
        if (fileTracker.trackFile(file)) {
            trackedFiles.add(filePath);
            notifyFileChanged(filePath);
        }
    }

    @Override
    public void untrackFile(String filePath) throws VCSException {
        fileTracker.untrackFile(filePath);
        trackedFiles.remove(filePath);
        notifyFileChanged(filePath);
    }

    @Override
    public List<String> getTrackedFiles() {
        return new ArrayList<>(trackedFiles);
    }

    @Override
    public Map<String, FileStatus> getFileStatuses() {
        Map<String, FileStatus> statuses = new HashMap<>();
        for (String filePath : trackedFiles) {
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