package models;

import java.util.List;
import java.util.ArrayList;

public record ChangedLines(
        List<LineChange> additions,
        List<LineChange> deletions,
        List<LineChange> modifications
) {
    public ChangedLines {
        additions = new ArrayList<>(additions);
        deletions = new ArrayList<>(deletions);
        modifications = new ArrayList<>(modifications);
    }

    public List<LineChange> getAdditions() {
        return new ArrayList<>(additions);
    }

    public List<LineChange> getDeletions() {
        return new ArrayList<>(deletions);
    }

    public List<LineChange> getModifications() {
        return new ArrayList<>(modifications);
    }
}