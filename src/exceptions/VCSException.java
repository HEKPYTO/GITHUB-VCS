package exceptions;

public class VCSException extends Exception {
    private final String errorCode;

    public VCSException(String message) {
        super(message);
        this.errorCode = "VCS_ERR_GENERIC";
    }

    public VCSException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "VCS_ERR_GENERIC";
    }

    public VCSException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public VCSException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}