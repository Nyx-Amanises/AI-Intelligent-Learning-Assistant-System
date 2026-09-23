package com.aiassistant.learning.common.security;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.SecretEncryptionProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 落库敏感字段的对称加密工具（AES-256-GCM）。
 *
 * <p>API Key 这类第三方凭据在调用时必须还原成明文，所以不能像密码那样只存哈希，
 * 只能用可逆加密存储，并把加密密钥放在数据库之外（环境变量 / 密钥管理服务）。</p>
 *
 * <p>密文格式：{@code enc:v1:<base64(iv || ciphertext || tag)>}。
 * 前缀用于区分明文旧数据和加密数据，也为将来轮换算法 / 密钥版本留出位置：
 * 没有前缀的值按明文旧数据处理，读到后由启动任务统一加密回写。</p>
 */
@Component
public class SecretCipher {

    /** 当前密文格式前缀。 */
    public static final String PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipher(SecretEncryptionProperties properties) {
        this.secretKey = deriveKey(properties.getEncryptionKey());
    }

    /**
     * 测试或工具场景直接用口令构造。
     */
    public static SecretCipher forPassphrase(String passphrase) {
        SecretEncryptionProperties properties = new SecretEncryptionProperties();
        properties.setEncryptionKey(passphrase);
        return new SecretCipher(properties);
    }

    /**
     * 加密。空值原样返回；已经是密文的值不会二次加密。
     */
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText) || isEncrypted(plainText)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new BusinessException(500, "敏感信息加密失败: " + exception.getMessage());
        }
    }

    /**
     * 解密。空值和没有密文前缀的旧明文原样返回。
     */
    public String decrypt(String storedValue) {
        if (!StringUtils.hasText(storedValue) || !isEncrypted(storedValue)) {
            return storedValue;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new BusinessException(500, "敏感信息解密失败，请确认 APP_SECRET_ENCRYPTION_KEY 与加密时一致");
        }
    }

    /**
     * 判断一个值是否已经是本工具产生的密文。
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKey deriveKey(String passphrase) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }
}
