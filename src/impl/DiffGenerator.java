package impl;

import interfaces.Diffable;
import interfaces.Mergeable;
import model.*;
import utils.*;
import exceptions.*;

import java.io.IOException;
import java.util.*;
import java.io.File;
import java.nio.file.*;

public class DiffGenerator implements Diffable, Mergeable {
    private final VersionManager versionManager;
    private final FileTracker fileTracker;
    private final List<ConflictInfo> currentConflicts;

    public DiffGenerator(VersionManager versionManager, FileTracker fileTracker) {
        this.versionManager = versionManager;
        this.fileTracker = fileTracker;
        this.currentConflicts = new ArrayList<>();
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

    @Override
    public boolean merge(String sourceVersion, String targetVersion) throws VCSException {
        VersionInfo sourceInfo = versionManager.getVersion(sourceVersion);
        VersionInfo targetInfo = versionManager.getVersion(targetVersion);

        if (sourceInfo == null || targetInfo == null) {
            throw new VersionException("Invalid version IDs");
        }

        currentConflicts.clear();
        Map<String, ChangedLines> diffChanges = getDiff(sourceVersion, targetVersion).changes();

        for (Map.Entry<String, ChangedLines> entry : diffChanges.entrySet()) {
            String filePath = entry.getKey();
            ChangedLines changes = entry.getValue();

            if (!changes.modifications().isEmpty()) {
                List<ConflictInfo.ConflictBlock> conflicts = detectConflicts(changes);
                if (!conflicts.isEmpty()) {
                    currentConflicts.add(new ConflictInfo(
                            filePath,
                            sourceInfo.getFileHashes().get(filePath),
                            targetInfo.getFileHashes().get(filePath),
                            conflicts
                    ));
                }
            }
        }

        return currentConflicts.isEmpty();
    }

    @Override
    public List<ConflictInfo> getConflicts() {
        return new ArrayList<>(currentConflicts);
    }

    @Override
    public void resolveConflict(String filePath, ConflictResolution resolution) throws VCSException {
        ConflictInfo conflict = findConflict(filePath);
        if (conflict == null) {
            throw new MergeConflictException("No conflict found for file: " + filePath);
        }

        List<String> mergedLines = applyResolution(conflict, resolution);
        String newHash = HashUtils.calculateStringHash(String.join("\n", mergedLines));

        Path objectPath = Paths.get(versionManager.getRepositoryPath(), ".vcs", "objects", newHash);
        try {
            Files.write(objectPath, mergedLines);
            currentConflicts.remove(conflict);
        } catch (IOException e) {
            throw new FileOperationException("Failed to save resolved file", e);
        }
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
                additions.add(new LineChange(newIdx + 1, null, newLines.get(newIdx), LineChange.ChangeType.ADDITION));
                newIdx++;
            } else if (newIdx >= newLines.size()) {
                deletions.add(new LineChange(oldIdx + 1, oldLines.get(oldIdx), null, LineChange.ChangeType.DELETION));
                oldIdx++;
            } else {
                String oldLine = oldLines.get(oldIdx);
                String newLine = newLines.get(newIdx);

                if (!oldLine.equals(newLine)) {
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

    private ConflictInfo findConflict(String filePath) {
        return currentConflicts.stream()
                .filter(c -> c.getFilePath().equals(filePath))
                .findFirst()
                .orElse(null);
    }

    private List<ConflictInfo.ConflictBlock> detectConflicts(ChangedLines changes) {
        List<ConflictInfo.ConflictBlock> conflicts = new ArrayList<>();
        Map<Integer, LineChange> modificationMap = new HashMap<>();

        for (LineChange mod : changes.modifications()) {
            modificationMap.put(mod.lineNumber(), mod);
        }

        int startLine = -1;
        int currentLine = -1;
        StringBuilder sourceContent = new StringBuilder();
        StringBuilder targetContent = new StringBuilder();

        for (Map.Entry<Integer, LineChange> entry : modificationMap.entrySet()) {
            int line = entry.getKey();
            if (currentLine == -1 || line != currentLine + 1) {
                if (currentLine != -1) {
                    conflicts.add(new ConflictInfo.ConflictBlock(
                            startLine, currentLine,
                            sourceContent.toString(),
                            targetContent.toString()
                    ));
                    sourceContent.setLength(0);
                    targetContent.setLength(0);
                }
                startLine = line;
            }
            LineChange change = entry.getValue();
            sourceContent.append(change.oldContent()).append('\n');
            targetContent.append(change.newContent()).append('\n');
            currentLine = line;
        }

        if (startLine != -1) {
            conflicts.add(new ConflictInfo.ConflictBlock(
                    startLine, currentLine,
                    sourceContent.toString(),
                    targetContent.toString()
            ));
        }

        return conflicts;
    }

    private List<String> applyResolution(ConflictInfo conflict, ConflictResolution resolution) throws VCSException {
        List<String> sourceLines = readFileLines(conflict.getSourceVersion());
        List<String> resolvedLines = new ArrayList<>(sourceLines);

        for (ConflictInfo.ConflictBlock block : conflict.getConflicts()) {
            int startIdx = block.startLine() - 1;
            int endIdx = block.endLine();

            List<String> replacementLines;
            switch (resolution.strategy()) {
                case KEEP_SOURCE:
                    continue;
                case KEEP_TARGET:
                    replacementLines = Arrays.asList(block.targetContent().split("\n"));
                    break;
                case CUSTOM:
                    replacementLines = resolution.resolvedLines().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(Map.Entry::getValue)
                            .toList();
                    break;
                default:
                    throw new MergeConflictException("Invalid resolution strategy");
            }

            resolvedLines.subList(startIdx, endIdx).clear();
            resolvedLines.addAll(startIdx, replacementLines);
        }

        return resolvedLines;
    }
}