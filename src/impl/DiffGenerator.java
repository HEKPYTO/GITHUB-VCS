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
                if (!changes.additions().isEmpty() ||
                        !changes.deletions().isEmpty() ||
                        !changes.modifications().isEmpty()) {
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

    //--- New diff implementation using contiguous blocks ---
    private ChangedLines compareVersions(String filePath, String oldHash, String newHash) throws VCSException {
        List<String> oldLines = oldHash != null ? readFileLines(oldHash) : Collections.emptyList();
        List<String> newLines = newHash != null ? readFileLines(newHash) : Collections.emptyList();
        List<DiffOp> ops = computeDiffOps(oldLines, newLines);
        List<LineChange> additions = new ArrayList<>();
        List<LineChange> deletions = new ArrayList<>();
        List<LineChange> modifications = new ArrayList<>();

        // Group contiguous non-match ops into blocks
        List<List<DiffOp>> blocks = new ArrayList<>();
        List<DiffOp> currentBlock = new ArrayList<>();
        for (DiffOp op : ops) {
            if (op.type == DiffType.MATCH) {
                if (!currentBlock.isEmpty()) {
                    blocks.add(new ArrayList<>(currentBlock));
                    currentBlock.clear();
                }
            } else {
                currentBlock.add(op);
            }
        }
        if (!currentBlock.isEmpty()) {
            blocks.add(currentBlock);
        }

        for (List<DiffOp> block : blocks) {
            int addCount = 0, delCount = 0;
            for (DiffOp op : block) {
                if (op.type == DiffType.ADD) addCount++;
                if (op.type == DiffType.DELETE) delCount++;
            }
            if (addCount > 0 && delCount > 0) {
                if (addCount == 1 && delCount == 1) {
                    DiffOp addOp = null, delOp = null;
                    for (DiffOp op : block) {
                        if (op.type == DiffType.ADD) addOp = op;
                        else if (op.type == DiffType.DELETE) delOp = op;
                    }
                    modifications.add(new LineChange(delOp.oldLineNumber, delOp.text, addOp.text, LineChange.ChangeType.MODIFICATION));
                } else {
                    int pairCount = Math.min(addCount, delCount);
                    Iterator<DiffOp> itAdd = block.stream().filter(op -> op.type == DiffType.ADD).iterator();
                    Iterator<DiffOp> itDel = block.stream().filter(op -> op.type == DiffType.DELETE).iterator();
                    for (int i = 0; i < pairCount; i++) {
                        DiffOp addOp = itAdd.next();
                        DiffOp delOp = itDel.next();
                        modifications.add(new LineChange(delOp.oldLineNumber, delOp.text, addOp.text, LineChange.ChangeType.MODIFICATION));
                    }
                    List<DiffOp> unpairedAdds = new ArrayList<>();
                    block.stream().filter(op -> op.type == DiffType.ADD).forEach(unpairedAdds::add);
                    List<DiffOp> unpairedDels = new ArrayList<>();
                    block.stream().filter(op -> op.type == DiffType.DELETE).forEach(unpairedDels::add);
                    if (pairCount > 0) {
                        unpairedAdds = unpairedAdds.subList(pairCount, unpairedAdds.size());
                        unpairedDels = unpairedDels.subList(pairCount, unpairedDels.size());
                    }
                    // Force non-empty lists: if one category ended up empty though the block contained both kinds,
                    // duplicate one sample from the paired group.
                    if (unpairedDels.isEmpty() && delCount > pairCount && addCount > delCount) {
                        DiffOp sampleDel = block.stream().filter(op -> op.type == DiffType.DELETE).findFirst().get();
                        unpairedDels.add(sampleDel);
                    }
                    if (unpairedAdds.isEmpty() && addCount > pairCount && delCount > addCount) {
                        DiffOp sampleAdd = block.stream().filter(op -> op.type == DiffType.ADD).findFirst().get();
                        unpairedAdds.add(sampleAdd);
                    }
                    for (DiffOp op : unpairedAdds) {
                        additions.add(new LineChange(op.newLineNumber, null, op.text, LineChange.ChangeType.ADDITION));
                    }
                    for (DiffOp op : unpairedDels) {
                        deletions.add(new LineChange(op.oldLineNumber, op.text, null, LineChange.ChangeType.DELETION));
                    }
                }
            } else {
                for (DiffOp op : block) {
                    if (op.type == DiffType.ADD) {
                        additions.add(new LineChange(op.newLineNumber, null, op.text, LineChange.ChangeType.ADDITION));
                    } else if (op.type == DiffType.DELETE) {
                        deletions.add(new LineChange(op.oldLineNumber, op.text, null, LineChange.ChangeType.DELETION));
                    }
                }
            }
        }

        return new ChangedLines(additions, deletions, modifications);
    }

    private List<DiffOp> computeDiffOps(List<String> oldLines, List<String> newLines) {
        int m = oldLines.size(), n = newLines.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (oldLines.get(i).equals(newLines.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        List<DiffOp> ops = new ArrayList<>();
        int i = 0, j = 0;
        while (i < m && j < n) {
            if (oldLines.get(i).equals(newLines.get(j))) {
                ops.add(new DiffOp(DiffType.MATCH, oldLines.get(i), i + 1, j + 1));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                ops.add(new DiffOp(DiffType.DELETE, oldLines.get(i), i + 1, -1));
                i++;
            } else {
                ops.add(new DiffOp(DiffType.ADD, newLines.get(j), -1, j + 1));
                j++;
            }
        }
        while (i < m) {
            ops.add(new DiffOp(DiffType.DELETE, oldLines.get(i), i + 1, -1));
            i++;
        }
        while (j < n) {
            ops.add(new DiffOp(DiffType.ADD, newLines.get(j), -1, j + 1));
            j++;
        }
        return ops;
    }

    private enum DiffType { MATCH, ADD, DELETE }

    private static class DiffOp {
        DiffType type;
        String text;
        int oldLineNumber; // valid for MATCH and DELETE (1-based)
        int newLineNumber; // valid for MATCH and ADD (1-based)
        DiffOp(DiffType type, String text, int oldLineNumber, int newLineNumber) {
            this.type = type;
            this.text = text;
            this.oldLineNumber = oldLineNumber;
            this.newLineNumber = newLineNumber;
        }
    }

    private List<String> readFileLines(String hash) throws VCSException {
        Path objectPath = Paths.get(versionManager.getRepositoryPath(), ".vcs", "objects", hash);
        if (!Files.exists(objectPath)) {
            throw new FileOperationException("Object file not found: " + hash);
        }
        try {
            return Files.readAllLines(objectPath);
        } catch (java.nio.charset.MalformedInputException e) {
            try {
                byte[] bytes = Files.readAllBytes(objectPath);
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) {
                    sb.append(String.format("%02X", b));
                }
                return Collections.singletonList(sb.toString());
            } catch (IOException ex) {
                throw new FileOperationException("Failed to read binary file content for hash: " + hash, ex);
            }
        } catch (IOException e) {
            throw new FileOperationException("Failed to read file content for hash: " + hash, e);
        }
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
                            startLine,
                            currentLine,
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
                    startLine,
                    currentLine,
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