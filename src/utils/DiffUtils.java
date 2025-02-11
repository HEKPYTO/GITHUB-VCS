package utils;

import model.LineChange;
import java.util.*;

public class DiffUtils {
    public static List<String> applyPatches(List<String> originalLines,
                                            List<LineChange> changes) {
        List<String> result = new ArrayList<>(originalLines);

        // Sort changes by line number in reverse order to apply from bottom to top
        changes.sort((a, b) -> Integer.compare(b.lineNumber(), a.lineNumber()));

        for (LineChange change : changes) {
            int lineIndex = change.lineNumber() - 1;
            switch (change.type()) {
                case ADDITION:
                    if (lineIndex >= result.size()) {
                        result.add(change.newContent());
                    } else {
                        result.add(lineIndex, change.newContent());
                    }
                    break;

                case DELETION:
                    if (lineIndex < result.size()) {
                        result.remove(lineIndex);
                    }
                    break;

                case MODIFICATION:
                    if (lineIndex < result.size()) {
                        result.set(lineIndex, change.newContent());
                    }
                    break;
            }
        }

        return result;
    }

    public static String generateUnifiedDiff(String oldContent, String newContent,
                                             String oldPath, String newPath) {
        String[] oldLines = oldContent.split("\n");
        String[] newLines = newContent.split("\n");

        StringBuilder diff = new StringBuilder();
        diff.append("--- ").append(oldPath).append('\n');
        diff.append("+++ ").append(newPath).append('\n');

        List<DiffBlock> blocks = computeDiffBlocks(oldLines, newLines);

        for (DiffBlock block : blocks) {
            diff.append("@@ -").append(block.startOld + 1)
                    .append(",").append(block.countOld)
                    .append(" +").append(block.startNew + 1)
                    .append(",").append(block.countNew)
                    .append(" @@\n");

            for (String line : block.lines) {
                diff.append(line).append('\n');
            }
        }

        return diff.toString();
    }

    private static class DiffBlock {
        int startOld, countOld, startNew, countNew;
        List<String> lines;

        DiffBlock(int startOld, int countOld, int startNew, int countNew) {
            this.startOld = startOld;
            this.countOld = countOld;
            this.startNew = startNew;
            this.countNew = countNew;
            this.lines = new ArrayList<>();
        }
    }

    private static List<DiffBlock> computeDiffBlocks(String[] oldLines, String[] newLines) {
        List<DiffBlock> blocks = new ArrayList<>();
        int oldIndex = 0, newIndex = 0;

        while (oldIndex < oldLines.length || newIndex < newLines.length) {
            DiffBlock block = new DiffBlock(oldIndex, 0, newIndex, 0);

            while (oldIndex < oldLines.length && newIndex < newLines.length &&
                    oldLines[oldIndex].equals(newLines[newIndex])) {
                block.lines.add(" " + oldLines[oldIndex]);
                oldIndex++;
                newIndex++;
                block.countOld++;
                block.countNew++;
            }

            while (oldIndex < oldLines.length &&
                    (newIndex >= newLines.length ||
                            !oldLines[oldIndex].equals(newLines[newIndex]))) {
                block.lines.add("-" + oldLines[oldIndex]);
                oldIndex++;
                block.countOld++;
            }

            while (newIndex < newLines.length &&
                    (oldIndex >= oldLines.length ||
                            !oldLines[oldIndex].equals(newLines[newIndex]))) {
                block.lines.add("+" + newLines[newIndex]);
                newIndex++;
                block.countNew++;
            }

            if (!block.lines.isEmpty()) {
                blocks.add(block);
            }
        }

        return blocks;
    }
}