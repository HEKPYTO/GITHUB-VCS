package models;

import java.util.Map;

public class ConflictResolution {
    private final String filePath;
    private final Map<Integer, String> resolvedLines;
    private final ResolutionStrategy strategy;

    public ConflictResolution(String filePath, Map<Integer, String> resolvedLines,
                              ResolutionStrategy strategy) {
        this.filePath = filePath;
        this.resolvedLines = resolvedLines;
        this.strategy = strategy;
    }

    public enum ResolutionStrategy {
        KEEP_SOURCE,
        KEEP_TARGET,
        CUSTOM
    }

    // Getters
    public String getFilePath() { return filePath; }
    public Map<Integer, String> getResolvedLines() { return Map.copyOf(resolvedLines); }
    public ResolutionStrategy getStrategy() { return strategy; }
}