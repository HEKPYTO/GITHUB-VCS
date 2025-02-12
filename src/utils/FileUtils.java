// FileUtils.java
package utils;

import exceptions.FileOperationException;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static void createDirectoryIfNotExists(String path) throws FileOperationException {
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            throw new FileOperationException.FileAccessException("Failed to create directory: " + path);
        }
    }

    public static void copyFile(File source, File destination) throws FileOperationException {
        try {
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileOperationException.FileAccessException(
                    "Failed to copy file from " + source.getPath() + " to " + destination.getPath());
        }
    }

    public static List<String> readLines(File file) throws FileOperationException {
        try {
            return Files.readAllLines(file.toPath());
        } catch (IOException e) {
            throw new FileOperationException.FileAccessException("Failed to read file: " + file.getPath());
        }
    }

    public static void writeString(File file, String content) throws FileOperationException {
        try {
            Files.writeString(file.toPath(), content);
        } catch (IOException e) {
            throw new FileOperationException.FileAccessException("Failed to write to file: " + file.getPath());
        }
    }

    public static void writeObjectToFile(String path, Object object) throws FileOperationException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(object);
        } catch (IOException e) {
            throw new FileOperationException.FileCorruptedException("Failed to write object to file: " + path);
        }
    }

    public static <T> T readObjectFromFile(File file, Class<T> type) throws FileOperationException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (type.isInstance(obj)) {
                return type.cast(obj);
            } else {
                throw new FileOperationException.FileCorruptedException(
                        "Object in file is not of type " + type.getSimpleName());
            }
        } catch (IOException e) {
            throw new FileOperationException.FileAccessException("Failed to read file: " + file.getPath());
        } catch (ClassNotFoundException e) {
            throw new FileOperationException.FileCorruptedException("Invalid object format in file: " + file.getPath());
        }
    }

    public static List<File> listFiles(File directory) throws FileOperationException {
        if (!directory.isDirectory()) {
            throw new FileOperationException("Not a directory: " + directory.getPath());
        }

        List<File> files = new ArrayList<>();
        File[] fileArray = directory.listFiles();
        if (fileArray != null) {
            for (File file : fileArray) {
                if (file.isFile()) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    public static boolean isFileEmpty(File file) throws FileOperationException {
        try {
            return Files.size(file.toPath()) == 0;
        } catch (IOException e) {
            throw new FileOperationException("Failed to check file size: " + file.getPath(), e);
        }
    }

    public static void deleteFile(File file) throws FileOperationException {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new FileOperationException("Failed to delete file: " + file.getPath(), e);
        }
    }

    public static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex == -1 ? "" : fileName.substring(lastDotIndex + 1);
    }
}