package utils;

import exceptions.FileOperationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {
    private static final String HASH_ALGORITHM = "SHA-256";

    public static String calculateFileHash(File file) throws FileOperationException {
        try {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            return calculateHash(fileBytes);
        } catch (IOException e) {
            throw new FileOperationException.FileAccessException("Failed to read file for hashing: " + file.getPath());
        }
    }

    public static String calculateStringHash(String content) throws FileOperationException {
        if (content == null) {
            throw new FileOperationException("Content cannot be null");
        }
        return calculateHash(content.getBytes());
    }

    private static String calculateHash(byte[] data) throws FileOperationException {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new FileOperationException("Hash algorithm not available: " + HASH_ALGORITHM);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public static boolean compareHashes(String hash1, String hash2) {
        return hash1 != null && hash1.equals(hash2);
    }
}
