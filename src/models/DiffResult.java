package models;

import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

public class DiffResult {
    private final String oldVersion;
    private final String newVersion;
    private final Map<String, ChangedLines> changes;

    public DiffResult(String oldVersion, String newVersion, Map<String, ChangedLines> changes) {
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
        this.changes = new HashMap<>(changes);
    }

    public Map<String, ChangedLines> getChanges() {
        return Collections.unmodifiableMap(changes);
    }

    public String getOldVersion() {
        return oldVersion;
    }

    public String getNewVersion() {
        return newVersion;
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