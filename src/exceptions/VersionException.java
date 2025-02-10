package exceptions;

public class VersionException extends VCSException {
    private final String versionId;

    public VersionException(String message) {
        super(message, "VCS_ERR_VERSION");
        this.versionId = null;
    }

    public VersionException(String message, String versionId) {
        super(message, "VCS_ERR_VERSION");
        this.versionId = versionId;
    }

    public VersionException(String message, String versionId, Throwable cause) {
        super(message, "VCS_ERR_VERSION", cause);
        this.versionId = versionId;
    }

    public String getVersionId() {
        return versionId;
    }

    public static class VersionNotFoundException extends VersionException {
        public VersionNotFoundException(String versionId) {
            super("Version not found: " + versionId, versionId);
        }
    }

    public static class InvalidVersionException extends VersionException {
        public InvalidVersionException(String versionId) {
            super("Invalid version format: " + versionId, versionId);
        }
    }

    public static class VersionCreationException extends VersionException {
        public VersionCreationException(String message) {
            super("Failed to create version: " + message);
        }
    }
}