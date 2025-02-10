package impl;

import interfaces.*;
import models.*;
import utils.*;
import exceptions.*;
import java.util.*;
import java.io.File;

public class MergeHandler implements Mergeable {
    private final VersionManager versionManager;
    private final DiffGenerator diffGenerator;
    private final List<ConflictInfo> currentConflicts;
    private final String repositoryPath;

    public MergeHandler(VersionManager versionManager, DiffGenerator diffGenerator) {
        this.versionManager = versionManager;
        this.diffGenerator = diffGenerator;
        this.currentConflicts = new ArrayList<>();
        this.repositoryPath = versionManager.getRepositoryPath();
    }

    @Override
    public boolean merge(String sourceVersion, String targetVersion) throws VCSException {
        VersionInfo sourceInfo = versionManager.getVersion(sourceVersion);
        VersionInfo targetInfo = versionManager.getVersion(targetVersion);

        if (sourceInfo == null || targetInfo == null) {
            throw new VersionException("Invalid version IDs");
        }

        currentConflicts.clear();
        Map<String, String> mergedFiles = new HashMap<>(targetInfo.getFileHashes());

        for (Map.Entry<String, String> entry : sourceInfo.getFileHashes().entrySet()) {
            String filePath = entry.getKey();
            String sourceHash = entry.getValue();
            String targetHash = targetInfo.getFileHashes().get(filePath);

            if (targetHash == null) {
                mergedFiles.put(filePath, sourceHash);
            } else if (!sourceHash.equals(targetHash)) {
                if (!tryAutoMerge(filePath, sourceHash, targetHash)) {
                    return false;
                }
            }
        }

        return currentConflicts.isEmpty();
    }

    private boolean tryAutoMerge(String filePath, String sourceHash, String targetHash)
            throws VCSException {
        List<String> sourceLines = readFileLines(sourceHash);
        List<String> targetLines = readFileLines(targetHash);

        List<ConflictInfo.ConflictBlock> conflicts = findConflicts(sourceLines, targetLines);
        if (!conflicts.isEmpty()) {
            currentConflicts.add(new ConflictInfo(filePath, sourceHash, targetHash, conflicts));
            return false;
        }
        return true;
    }

    private List<String> readFileLines(String hash) throws VCSException {
        File file = new File(repositoryPath + "/.vcs/objects/" + hash);
        return FileUtils.readLines(file);
    }

    private List<ConflictInfo.ConflictBlock> findConflicts(List<String> sourceLines, List<String> targetLines) {
        List<ConflictInfo.ConflictBlock> conflicts = new ArrayList<>();
        int i = 0;
        while (i < Math.min(sourceLines.size(), targetLines.size())) {
            if (!sourceLines.get(i).equals(targetLines.get(i))) {
                int startLine = i;
                while (i < Math.min(sourceLines.size(), targetLines.size()) &&
                        !sourceLines.get(i).equals(targetLines.get(i))) {
                    i++;
                }

                conflicts.add(new ConflictInfo.ConflictBlock(
                        startLine,
                        i - 1,
                        String.join("\n", sourceLines.subList(startLine, i)),
                        String.join("\n", targetLines.subList(startLine, i))
                ));
            }
            i++;
        }

        if (i < sourceLines.size() || i < targetLines.size()) {
            conflicts.add(new ConflictInfo.ConflictBlock(
                    i,
                    Math.max(sourceLines.size(), targetLines.size()) - 1,
                    i < sourceLines.size() ? String.join("\n", sourceLines.subList(i, sourceLines.size())) : "",
                    i < targetLines.size() ? String.join("\n", targetLines.subList(i, targetLines.size())) : ""
            ));
        }

        return conflicts;
    }

    @Override
    public void resolveConflict(String filePath, ConflictResolution resolution) throws VCSException {
        ConflictInfo conflict = findConflict(filePath);
        if (conflict == null) {
            throw new MergeConflictException("No conflict found for file: " + filePath);
        }

        String resolvedContent = applyResolution(conflict, resolution);
        String newHash = HashUtils.calculateStringHash(resolvedContent);

        File resolvedFile = new File(repositoryPath + "/.vcs/objects/" + newHash);
        FileUtils.writeString(resolvedFile, resolvedContent);

        File originalFile = new File(filePath);
        FileUtils.writeString(originalFile, resolvedContent);

        currentConflicts.remove(conflict);
    }

    private ConflictInfo findConflict(String filePath) {
        return currentConflicts.stream()
                .filter(c -> c.getFilePath().equals(filePath))
                .findFirst()
                .orElse(null);
    }

    private String applyResolution(ConflictInfo conflict, ConflictResolution resolution)
            throws VCSException {
        List<String> sourceLines = readFileLines(conflict.getSourceVersion());
        List<String> targetLines = readFileLines(conflict.getTargetVersion());
        List<String> resolvedLines = new ArrayList<>();
        int currentLine = 0;

        for (ConflictInfo.ConflictBlock block : conflict.getConflicts()) {
            while (currentLine < block.startLine()) {
                resolvedLines.add(sourceLines.get(currentLine));
                currentLine++;
            }

            switch (resolution.getStrategy()) {
                case KEEP_SOURCE:
                    for (int i = block.startLine(); i <= block.endLine() && i < sourceLines.size(); i++) {
                        resolvedLines.add(sourceLines.get(i));
                    }
                    break;
                case KEEP_TARGET:
                    for (int i = block.startLine(); i <= block.endLine() && i < targetLines.size(); i++) {
                        resolvedLines.add(targetLines.get(i));
                    }
                    break;
                case CUSTOM:
                    Map<Integer, String> customResolutions = resolution.getResolvedLines();
                    for (int i = block.startLine(); i <= block.endLine(); i++) {
                        String customLine = customResolutions.get(i);
                        resolvedLines.add(customLine != null ? customLine : sourceLines.get(i));
                    }
                    break;
            }
            currentLine = block.endLine() + 1;
        }

        while (currentLine < sourceLines.size()) {
            resolvedLines.add(sourceLines.get(currentLine));
            currentLine++;
        }

        return String.join("\n", resolvedLines);
    }

    @Override
    public List<ConflictInfo> getConflicts() {
        return new ArrayList<>(currentConflicts);
    }
}