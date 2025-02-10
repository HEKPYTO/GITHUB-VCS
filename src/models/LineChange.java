package models;

public class LineChange {
    private final int lineNumber;
    private final String oldContent;
    private final String newContent;
    private final ChangeType type;

    public LineChange(int lineNumber, String oldContent, String newContent, ChangeType type) {
        this.lineNumber = lineNumber;
        this.oldContent = oldContent;
        this.newContent = newContent;
        this.type = type;
    }

    public enum ChangeType {
        ADDITION,
        DELETION,
        MODIFICATION
    }

    // Getters
    public int getLineNumber() { return lineNumber; }
    public String getOldContent() { return oldContent; }
    public String getNewContent() { return newContent; }
    public ChangeType getType() { return type; }
}