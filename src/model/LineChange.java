package model;

public record LineChange(int lineNumber, String oldContent, String newContent, model.LineChange.ChangeType type) {

    public enum ChangeType {
        ADDITION,
        DELETION,
        MODIFICATION
    }
}