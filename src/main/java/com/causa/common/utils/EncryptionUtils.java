package com.causa.common.utils;

import org.eclipse.microprofile.config.ConfigProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption Utility
 *
 * <p>AES-256-GCM symmetric encryption for sensitive configuration values
 * stored in the {@code configurations} table.
 *
 * <h2>Algorithm</h2>
 * <ul>
 *   <li>Algorithm: AES-256-GCM (authenticated encryption — tamper-proof)</li>
 *   <li>IV: 12 bytes, randomly generated per encryption</li>
 *   <li>Tag length: 128 bits</li>
 *   <li>Output format: Base64( iv[12] + ciphertext + tag[16] )</li>
 * </ul>
 *
 * <h2>Master key</h2>
 * <p>{@link #getMasterKey()} is the single point of truth for the encryption key.
 * Currently it derives a 256-bit key from the database password configured in
 * {@code application.yml} / ENV. Replace the body of {@link #getMasterKey()} to
 * integrate a proper KMS (HashiCorp Vault, GCP KMS, AWS KMS, etc.) when ready.
 *
 * <p><strong>Thread Safety:</strong> All methods are stateless and thread-safe.
 *
 * @since 0.0.1
 */
public final class EncryptionUtils {

    private static final String ALGORITHM     = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH     = 12;   // bytes — NIST recommended for GCM
    private static final int    TAG_LENGTH    = 128;  // bits
    private static final int    KEY_LENGTH    = 32;   // bytes → 256-bit AES
    private static final SecureRandom RANDOM  = new SecureRandom();

    private EncryptionUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    // =========================================================================
    // Master key — replace this body to integrate a real KMS later
    // =========================================================================

    /**
     * Returns the 256-bit AES master encryption key.
     *
     * <p>The key material is read from {@code causa.encryption.key}
     * ({@code CAUSA_ENCRYPTION_KEY} env var), which must be set in production
     * via a Kubernetes Secret. The {@code application.yml} default is only used
     * in local development and must never be used in production.
     *
     * <p><strong>TODO:</strong> Replace with a real KMS lookup:
     * <pre>{@code
     *   // Example: GCP KMS, HashiCorp Vault, AWS Secrets Manager, etc.
     *   return fetchKeyFromKms();
     * }</pre>
     *
     * @return 256-bit AES {@link SecretKey}
     */
    public static SecretKey getMasterKey() {
        String encryptionKey = ConfigProvider.getConfig()
                .getValue("causa.encryption.key", String.class);

        byte[] rawKey = new byte[KEY_LENGTH];
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        // Copy key bytes into a 32-byte array (truncate or zero-pad as needed)
        System.arraycopy(keyBytes, 0, rawKey, 0, Math.min(keyBytes.length, KEY_LENGTH));
        return new SecretKeySpec(rawKey, "AES");
    }

    // =========================================================================
    // Encrypt / Decrypt
    // =========================================================================

    /**
     * Encrypts a plaintext string using AES-256-GCM with a random IV.
     *
     * @param plaintext the value to encrypt; must not be {@code null}
     * @return Base64-encoded ciphertext in the format {@code iv + ciphertext + gcmTag}
     * @throws EncryptionException if encryption fails
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext to encrypt must not be null");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getMasterKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Pack: iv (12 bytes) || ciphertext+tag
            ByteBuffer buffer = ByteBuffer.allocate(IV_LENGTH + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a Base64-encoded ciphertext produced by {@link #encrypt(String)}.
     *
     * @param ciphertext Base64-encoded value as produced by {@link #encrypt}
     * @return the original plaintext
     * @throws EncryptionException if decryption fails (wrong key, corrupted data, etc.)
     */
    public static String decrypt(String ciphertext) {
        if (ciphertext == null) {
            throw new IllegalArgumentException("Ciphertext to decrypt must not be null");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encryptedBytes = new byte[buffer.remaining()];
            buffer.get(encryptedBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(encryptedBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed", e);
        }
    }

    // =========================================================================
    // Exception
    // =========================================================================

    /**
     * Thrown when an encrypt or decrypt operation fails.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
