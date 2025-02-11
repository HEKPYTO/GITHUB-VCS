package exceptions;

import java.util.List;
import model.ConflictInfo;

public class MergeConflictException extends VCSException {
    private final String sourceVersion;
    private final String targetVersion;
    private final List<ConflictInfo> conflicts;

    public MergeConflictException(String message) {
        super(message, "VCS_ERR_MERGE_CONFLICT");
        this.sourceVersion = null;
        this.targetVersion = null;
        this.conflicts = null;
    }

    public MergeConflictException(String message, Throwable cause) {
        super(message, "VCS_ERR_MERGE_CONFLICT", cause);
        this.sourceVersion = null;
        this.targetVersion = null;
        this.conflicts = null;
    }

    public MergeConflictException(String message, String sourceVersion,
                                  String targetVersion, List<ConflictInfo> conflicts) {
        super(message, "VCS_ERR_MERGE_CONFLICT");
        this.sourceVersion = sourceVersion;
        this.targetVersion = targetVersion;
        this.conflicts = conflicts;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public List<ConflictInfo> getConflicts() {
        return conflicts != null ? List.copyOf(conflicts) : null;
    }

    public static class UnresolvedConflictException extends MergeConflictException {
        public UnresolvedConflictException(String filePath) {
            super("Unresolved conflict in file: " + filePath);
        }
    }

    public static class ConflictResolutionException extends MergeConflictException {
        public ConflictResolutionException(String message) {
            super("Failed to resolve conflict: " + message);
        }
    }

    public static class AutoMergeFailedException extends MergeConflictException {
        public AutoMergeFailedException(String message) {
            super("Automatic merge failed: " + message);
        }
    }
}