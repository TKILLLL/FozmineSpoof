package org.phantam.fozminespoofcore.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CryptoUtils {

    private static final String SECRET_KEY = "FozmineSpoofSecretKey2026";

    private CryptoUtils() {}

    public static String decrypt(String base64Encrypted) {
        try {
            byte[] data = Base64.getDecoder().decode(base64Encrypted);
            byte[] keyBytes = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                result[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
            }
            return new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static String encrypt(String plainText) {
        try {
            byte[] data = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                result[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
            }
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            return "";
        }
    }
}