package org.phantam.fozminespoofcore.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for secure encryption and decryption operations.
 * <p>
 * This class provides AES-256/GCM encryption with a key derived from:
 * <ol>
 *   <li>System property {@code fozminespoof.encryption.key}</li>
 *   <li>Environment variable {@code FOZMINESPOOF_ENCRYPTION_KEY}</li>
 *   <li>A fallback key (if none is set) – a warning will be logged</li>
 * </ol>
 * The encrypted output is a Base64-encoded string containing the IV and ciphertext.
 * </p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * String encrypted = CryptoUtils.encrypt("mySecret");
 * String decrypted = CryptoUtils.decrypt(encrypted);
 * </pre>
 *
 * @author Phantam
 * @version 2.0.0
 * @since 2.0.0
 */
public final class CryptoUtils {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final String FALLBACK_KEY = "FozmineSpoofFallbackKey2026!";

    private static final Logger LOGGER = Logger.getLogger(CryptoUtils.class.getName());
    private static final SecretKey SECRET_KEY;

    static {
        // Determine encryption key from environment or system property
        String keyString = System.getProperty("fozminespoof.encryption.key");
        if (keyString == null || keyString.isEmpty()) {
            keyString = System.getenv("FOZMINESPOOF_ENCRYPTION_KEY");
        }
        if (keyString == null || keyString.isEmpty()) {
            keyString = FALLBACK_KEY;
            LOGGER.log(Level.WARNING,
                    "[CryptoUtils] No encryption key found in environment or system properties. "
                            + "Using fallback key. This is NOT secure for production!");
        }
        // Derive a 256-bit key from the string using SHA-256
        try {
            byte[] keyBytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(keyString.getBytes(StandardCharsets.UTF_8));
            SECRET_KEY = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize AES key", e);
        }
    }

    private CryptoUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Encrypts a plaintext string using AES-256/GCM.
     *
     * @param plainText the text to encrypt (must not be null)
     * @return Base64-encoded encrypted string containing IV and ciphertext,
     * or an empty string if encryption fails
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, spec);

            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[CryptoUtils] Encryption failed: " + e.getMessage(), e);
            return "";
        }
    }

    /**
     * Decrypts a Base64-encoded encrypted string using AES-256/GCM.
     *
     * @param base64Encrypted the encrypted data (must not be null)
     * @return the decrypted plaintext, or an empty string if decryption fails
     */
    public static String decrypt(String base64Encrypted) {
        if (base64Encrypted == null || base64Encrypted.isEmpty()) {
            return "";
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Encrypted);
            if (decoded.length < IV_LENGTH_BYTE) {
                // Invalid data – treat as plaintext (backward compatibility)
                return "";
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, spec);

            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If decryption fails, the input was probably plaintext → return as-is
            LOGGER.log(Level.FINE, "[CryptoUtils] Decryption failed, treating as plaintext: " + e.getMessage());
            return "";
        }
    }

    /**
     * Checks if a given string appears to be encrypted (Base64 + AES).
     *
     * @param text the text to check
     * @return {@code true} if it looks like encrypted data, {@code false} otherwise
     */
    public static boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            return decoded.length >= IV_LENGTH_BYTE + 16; // at least IV + tag
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}