package models;

import java.util.List;

public class ConflictInfo {
    private final String filePath;
    private final String sourceVersion;
    private final String targetVersion;
    private final List<ConflictBlock> conflicts;
    private ConflictStatus status;

    public ConflictInfo(String filePath, String sourceVersion, String targetVersion,
                        List<ConflictBlock> conflicts) {
        this.filePath = filePath;
        this.sourceVersion = sourceVersion;
        this.targetVersion = targetVersion;
        this.conflicts = conflicts;
        this.status = ConflictStatus.UNRESOLVED;
    }

    public record ConflictBlock(
            int startLine,
            int endLine,
            String sourceContent,
            String targetContent
    ) {}

    public enum ConflictStatus {
        UNRESOLVED,
        RESOLVED_KEEP_SOURCE,
        RESOLVED_KEEP_TARGET,
        RESOLVED_CUSTOM
    }

    // Getters
    public String getFilePath() { return filePath; }
    public String getSourceVersion() { return sourceVersion; }
    public String getTargetVersion() { return targetVersion; }
    public List<ConflictBlock> getConflicts() { return List.copyOf(conflicts); }
    public ConflictStatus getStatus() { return status; }
    public void setStatus(ConflictStatus status) { this.status = status; }
}