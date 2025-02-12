package utils;

import exceptions.FileOperationException;
import java.io.File;

public class ValidationUtils {
    public static void validateFile(File file) throws FileOperationException {
        if (file == null) {
            throw new FileOperationException("File cannot be null");
        }
        if (!file.exists()) {
            throw new FileOperationException.FileNotFoundException(file.getPath());
        }
        if (!file.isFile()) {
            throw new FileOperationException("Not a regular file: " + file.getPath());
        }
        if (!file.canRead()) {
            throw new FileOperationException.FileAccessException(file.getPath());
        }
    }

    public static void validateDirectory(File directory) throws FileOperationException {
        if (directory == null) {
            throw new FileOperationException("Directory cannot be null");
        }
        if (!directory.exists()) {
            throw new FileOperationException.FileNotFoundException(directory.getPath());
        }
        if (!directory.isDirectory()) {
            throw new FileOperationException("Not a directory: " + directory.getPath());
        }
        if (!directory.canRead()) {
            throw new FileOperationException.FileAccessException(directory.getPath());
        }
    }

    public static void validateFilePath(String filePath) throws FileOperationException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new FileOperationException("File path cannot be null or empty");
        }
        validateFile(new File(filePath));
    }

    public static void validateDirectoryPath(String directoryPath) throws FileOperationException {
        if (directoryPath == null || directoryPath.trim().isEmpty()) {
            throw new FileOperationException("Directory path cannot be null or empty");
        }
        validateDirectory(new File(directoryPath));
    }

    public static void validateFileWritable(File file) throws FileOperationException {
        validateFile(file);
        if (!file.canWrite()) {
            throw new FileOperationException("File is not writable: " + file.getPath());
        }
    }

    public static void validateDirectoryWritable(File directory) throws FileOperationException {
        validateDirectory(directory);
        if (!directory.canWrite()) {
            throw new FileOperationException("Directory is not writable: " + directory.getPath());
        }
    }
}