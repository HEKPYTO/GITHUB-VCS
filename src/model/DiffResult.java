package model;

import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

public record DiffResult(String oldVersion, String newVersion, Map<String, ChangedLines> changes) {
    public DiffResult(String oldVersion, String newVersion, Map<String, ChangedLines> changes) {
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
        this.changes = new HashMap<>(changes);
    }

    @Override
    public Map<String, ChangedLines> changes() {
        return Collections.unmodifiableMap(changes);
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    public int getTotalChanges() {
        return changes.values().stream()
                .mapToInt(changes ->
                        changes.getAdditions().size() +
                                changes.getDeletions().size() +
                                changes.getModifications().size())
                .sum();
    }

    @Override
    public String toString() {
        return String.format("DiffResult{oldVersion='%s', newVersion='%s', changedFiles=%d}",
                oldVersion, newVersion, changes.size());
    }
}