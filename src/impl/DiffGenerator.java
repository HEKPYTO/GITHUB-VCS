package impl;

import interfaces.Diffable;
import model.*;
import utils.*;
import exceptions.*;

import java.io.IOException;
import java.util.*;
import java.io.File;
import java.nio.file.*;

public class DiffGenerator implements Diffable {
    private final VersionManager versionManager;
    private final FileTracker fileTracker;

    public DiffGenerator(VersionManager versionManager, FileTracker fileTracker) {
        this.versionManager = versionManager;
        this.fileTracker = fileTracker;
    }

    @Override
    public DiffResult getDiff(String oldVersion, String newVersion) throws VCSException {
        VersionInfo oldVer = versionManager.getVersion(oldVersion);
        VersionInfo newVer = versionManager.getVersion(newVersion);

        if (oldVer == null || newVer == null) {
            throw new VersionException("Invalid version IDs");
        }

        Map<String, ChangedLines> fileChanges = new HashMap<>();
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(oldVer.getFileHashes().keySet());
        allFiles.addAll(newVer.getFileHashes().keySet());

        for (String filePath : allFiles) {
            String oldHash = oldVer.getFileHashes().get(filePath);
            String newHash = newVer.getFileHashes().get(filePath);

            if (!Objects.equals(oldHash, newHash)) {
                // At least one of oldHash or newHash exists since the file is in allFiles
                ChangedLines changes = compareVersions(filePath, oldHash, newHash);
                if (!changes.additions().isEmpty() || !changes.deletions().isEmpty() || !changes.modifications().isEmpty()) {
                    fileChanges.put(filePath, changes);
                }
            }
        }

        return new DiffResult(oldVersion, newVersion, fileChanges);
    }

    @Override
    public DiffResult getDiff(String filePath) throws VCSException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileOperationException("File does not exist: " + filePath);
        }

        FileMetadata metadata = fileTracker.getFileMetadata(filePath);
        if (metadata == null) {
            throw new FileOperationException("File is not tracked: " + filePath);
        }

        String currentHash = HashUtils.calculateFileHash(file);
        String storedHash = metadata.getCurrentHash();

        Map<String, ChangedLines> changes = new HashMap<>();
        if (!currentHash.equals(storedHash)) {
            changes.put(filePath, compareVersions(filePath, storedHash, currentHash));
        }

        return new DiffResult("current", "working", changes);
    }

    private ChangedLines compareVersions(String filePath, String oldHash, String newHash) throws VCSException {
        List<String> oldLines = oldHash != null ? readFileLines(oldHash) : new ArrayList<>();
        List<String> newLines = newHash != null ? readFileLines(newHash) : new ArrayList<>();

        List<LineChange> additions = new ArrayList<>();
        List<LineChange> deletions = new ArrayList<>();
        List<LineChange> modifications = new ArrayList<>();

        int oldIdx = 0, newIdx = 0;
        while (oldIdx < oldLines.size() || newIdx < newLines.size()) {
            if (oldIdx >= oldLines.size()) {
                // All remaining new lines are additions
                additions.add(new LineChange(newIdx + 1, null, newLines.get(newIdx), LineChange.ChangeType.ADDITION));
                newIdx++;
            } else if (newIdx >= newLines.size()) {
                // All remaining old lines are deletions
                deletions.add(new LineChange(oldIdx + 1, oldLines.get(oldIdx), null, LineChange.ChangeType.DELETION));
                oldIdx++;
            } else {
                String oldLine = oldLines.get(oldIdx);
                String newLine = newLines.get(newIdx);

                if (!oldLine.equals(newLine)) {
                    // Lines are different - treat as a modification
                    modifications.add(new LineChange(oldIdx + 1, oldLine, newLine, LineChange.ChangeType.MODIFICATION));
                }
                oldIdx++;
                newIdx++;
            }
        }

        return new ChangedLines(additions, deletions, modifications);
    }

    private List<String> readFileLines(String hash) throws VCSException {
        try {
            Path objectPath = Paths.get(versionManager.getRepositoryPath(), ".vcs", "objects", hash);
            if (!Files.exists(objectPath)) {
                throw new FileOperationException("Object file not found: " + hash);
            }
            return Files.readAllLines(objectPath);
        } catch (IOException e) {
            throw new FileOperationException("Failed to read file content for hash: " + hash, e);
        }
    }

    @Override
    public Map<String, ChangedLines> getChangedLines(String filePath) throws VCSException {
        Map<String, ChangedLines> changes = new HashMap<>();
        FileMetadata metadata = fileTracker.getFileMetadata(filePath);

        if (metadata != null) {
            File file = new File(filePath);
            String currentHash = HashUtils.calculateFileHash(file);
            String storedHash = metadata.getCurrentHash();

            if (!currentHash.equals(storedHash)) {
                changes.put(filePath, compareVersions(filePath, storedHash, currentHash));
            }
        }

        return changes;
    }
}