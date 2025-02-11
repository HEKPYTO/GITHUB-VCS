package interfaces;

import exceptions.VCSException;
import model.ChangedLines;
import model.DiffResult;
import java.util.Map;

public interface Diffable {
    DiffResult getDiff(String oldVersion, String newVersion) throws VCSException;

    DiffResult getDiff(String filePath) throws VCSException;

    Map<String, ChangedLines> getChangedLines(String filePath) throws VCSException;

    interface DiffStrategy<T> {
        T computeDiff(String oldContent, String newContent);
    }

    default <T> T computeCustomDiff(String oldVersion, String newVersion, DiffStrategy<T> strategy)
            throws VCSException {
        return strategy.computeDiff(oldVersion, newVersion);
    }
}