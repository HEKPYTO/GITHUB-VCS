package impl;

import interfaces.*;
import model.*;
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

        // For every file in the source version that also exists in the target, compare contents.
        for (Map.Entry<String, String> entry : sourceInfo.getFileHashes().entrySet()) {
            String filePath = entry.getKey();
            String sourceHash = entry.getValue();
            String targetHash = targetInfo.getFileHashes().get(filePath);

            if (targetHash != null && !sourceHash.equals(targetHash)) {
                List<String> sourceLines = readFileLines(sourceHash);
                List<String> targetLines = readFileLines(targetHash);
                List<ConflictInfo.ConflictBlock> conflicts = findConflicts(sourceLines, targetLines);
                if (!conflicts.isEmpty()) {
                    currentConflicts.add(new ConflictInfo(filePath, sourceHash, targetHash, conflicts));
                }
            }
        }

        return currentConflicts.isEmpty();
    }

    private List<String> readFileLines(String hash) throws VCSException {
        File file = new File(repositoryPath + "/.vcs/objects/" + hash);
        return FileUtils.readLines(file);
    }

    /**
     * Iterates over all line indices (up to the maximum number of lines in either version)
     * and groups consecutive differing lines into a block. When the block ends, the
     * similarity between the two blocks is computed and, if below the threshold, a conflict is recorded.
     */
    private List<ConflictInfo.ConflictBlock> findConflicts(List<String> sourceLines, List<String> targetLines) {
        List<ConflictInfo.ConflictBlock> conflicts = new ArrayList<>();
        int nSource = sourceLines.size();
        int nTarget = targetLines.size();
        int maxLines = Math.max(nSource, nTarget);

        int blockStart = -1; // Indicates that no block is currently being collected.
        int blockEnd = -1;

        for (int i = 0; i < maxLines; i++) {
            String sourceLine = (i < nSource) ? sourceLines.get(i) : "";
            String targetLine = (i < nTarget) ? targetLines.get(i) : "";
            if (!sourceLine.equals(targetLine)) {
                if (blockStart == -1) {
                    blockStart = i;
                }
                blockEnd = i;
            } else {
                // If we were in a differing block, finish it.
                if (blockStart != -1) {
                    addConflictBlockIfNeeded(conflicts, sourceLines, targetLines, blockStart, blockEnd);
                    blockStart = -1;
                    blockEnd = -1;
                }
            }
        }
        // If a block is active at the end of the file, finalize it.
        if (blockStart != -1) {
            addConflictBlockIfNeeded(conflicts, sourceLines, targetLines, blockStart, blockEnd);
        }

        return conflicts;
    }

    private void addConflictBlockIfNeeded(List<ConflictInfo.ConflictBlock> conflicts,
                                          List<String> sourceLines, List<String> targetLines,
                                          int blockStart, int blockEnd) {
        String sourceContent = getContentSlice(sourceLines, blockStart, blockEnd);
        String targetContent = getContentSlice(targetLines, blockStart, blockEnd);
        double similarity = calculateSimilarity(sourceContent, targetContent);
        double threshold = 0.5; // If similarity is greater than or equal to threshold, auto-merge.
        if (similarity < threshold) {
            conflicts.add(new ConflictInfo.ConflictBlock(blockStart, blockEnd, sourceContent, targetContent));
        }
    }

    private String getContentSlice(List<String> lines, int start, int end) {
        if (start >= lines.size()) return "";
        if (end < start) end = start;
        return String.join("\n", lines.subList(start, Math.min(end + 1, lines.size())));
    }

    // Calculate similarity based on the Levenshtein distance ratio.
    private double calculateSimilarity(String s1, String s2) {
        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - ((double) distance / maxLen);
    }

    // Standard Levenshtein distance calculation.
    private int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[m][n];
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

    private String applyResolution(ConflictInfo conflict, ConflictResolution resolution) throws VCSException {
        List<String> sourceLines = readFileLines(conflict.getSourceVersion());
        List<String> targetLines = readFileLines(conflict.getTargetVersion());
        List<String> resolvedLines = new ArrayList<>();
        int currentLine = 0;

        for (ConflictInfo.ConflictBlock block : conflict.getConflicts()) {
            while (currentLine < block.startLine()) {
                resolvedLines.add(sourceLines.get(currentLine));
                currentLine++;
            }

            switch (resolution.strategy()) {
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
                    Map<Integer, String> customResolutions = resolution.resolvedLines();
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
