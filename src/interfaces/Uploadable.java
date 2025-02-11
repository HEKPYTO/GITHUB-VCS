package interfaces;

import exceptions.VCSException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public interface Uploadable {
    boolean upload(File file) throws VCSException, IOException;
    boolean uploadDirectory(File directory) throws VCSException, IOException;
    List<File> getUploadedFiles();
    void removeFile(String filePath) throws VCSException;

    @FunctionalInterface
    public interface FileFilterable {
        boolean filter(File file);
    }

    default List<File> getUploadedFilesByFilter(FileFilterable filter) {
        return getUploadedFiles().stream()
                .filter(filter::filter)
                .collect(Collectors.toList());
    }

    static boolean isValidFile(File file) {
        return file != null && file.exists() && !file.isDirectory();
    }
}
