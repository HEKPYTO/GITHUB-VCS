package model;

import java.util.Map;

public record ConflictResolution(String filePath, Map<Integer, String> resolvedLines,
                                 ResolutionStrategy strategy) {

    public ConflictResolution {
        if (filePath == null || resolvedLines == null || strategy == null) {
            throw new NullPointerException("All parameters must be non-null");
        }
    }

    public enum ResolutionStrategy {
        KEEP_SOURCE,
        KEEP_TARGET,
        CUSTOM
    }

    @Override
    public Map<Integer, String> resolvedLines() {
        return Map.copyOf(resolvedLines);
    }
}