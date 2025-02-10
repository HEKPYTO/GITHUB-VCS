package exceptions;

public class FileOperationException extends VCSException {
    public FileOperationException(String message) {
        super(message, "VCS_ERR_FILE_OP");
    }

    public FileOperationException(String message, Throwable cause) {
        super(message, "VCS_ERR_FILE_OP", cause);
    }

    public static class FileNotFoundException extends FileOperationException {
        public FileNotFoundException(String message) {
            super("File not found: " + message);
        }
    }

    public static class FileAccessException extends FileOperationException {
        public FileAccessException(String message) {
            super("File access denied: " + message);
        }
    }

    public static class FileCorruptedException extends FileOperationException {
        public FileCorruptedException(String message) {
            super("File corrupted: " + message);
        }
    }

    public static class FileAlreadyExistsException extends FileOperationException {
        public FileAlreadyExistsException(String message) {
            super("File already exists: " + message);
        }
    }
}