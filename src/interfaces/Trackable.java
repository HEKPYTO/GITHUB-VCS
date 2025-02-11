package interfaces;

import exceptions.VCSException;
import model.FileStatus;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface Trackable {
    void trackFile(String filePath) throws VCSException, IOException;
    void untrackFile(String filePath) throws VCSException;
    List<String> getTrackedFiles();
    Map<String, FileStatus> getFileStatuses();

    void addFileChangeListener(Consumer<String> listener);
    void removeFileChangeListener(Consumer<String> listener);

    default void trackFiles(List<String> filePaths) throws VCSException, IOException {
        for (String path : filePaths) {
            trackFile(path);
        }
    }
}