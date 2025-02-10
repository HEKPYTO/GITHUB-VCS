package interfaces;

import exceptions.VCSException;
import models.FileMetadata;
import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface Uploadable {
    boolean upload(File file) throws VCSException;
    boolean uploadDirectory(File directory) throws VCSException;
    List<File> getUploadedFiles();
    void removeFile(String filePath) throws VCSException;

    // Default method showing advanced interface usage
    default List<File> getUploadedFilesByFilter(FileFilterable filter) {
        return getUploadedFiles().stream()
                .filter(filter::filter)
                .collect(Collectors.toList());
    }

    // Static utility method
    static boolean isValidFile(File file) {
        return file != null && file.exists() && !file.isDirectory();
    }
}
