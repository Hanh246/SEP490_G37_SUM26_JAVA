package com.sep.comiverse.service;

import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.exception.OfflineDownloadException;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.math.BigInteger;
import java.util.Base64;

@Service
public class OfflineDownloadCryptoService {

    public static final String KEY_WRAP_ALGORITHM = "RSA-OAEP-SHA256-MGF1SHA1";
    public static final String DEVICE_PROOF_ALGORITHM = "RSA-PSS-SHA256-MGF1SHA256";

    private final OfflineDownloadProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private PrivateKey signingPrivateKey;
    private PublicKey signingPublicKey;

    public OfflineDownloadCryptoService(OfflineDownloadProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            validateConfiguration();
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            signingPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(
                    decodeConfiguredKey(properties.getSigningPrivateKey())
            ));
            signingPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(
                    decodeConfiguredKey(properties.getSigningPublicKey())
            ));

            byte[] probe = randomBytes(32);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(signingPrivateKey, secureRandom);
            signer.update(probe);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(signingPublicKey);
            verifier.update(probe);
            if (!verifier.verify(signature)) {
                throw new IllegalStateException("Offline license signing keys do not form a valid pair");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Offline downloads are enabled but signing keys are invalid", exception);
        }
    }

    public void requireAvailable() {
        if (!properties.isEnabled() || signingPrivateKey == null || signingPublicKey == null) {
            throw new OfflineDownloadException(
                    "OFFLINE_DOWNLOADS_UNAVAILABLE",
                    "Offline downloads are temporarily unavailable",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    public byte[] signLicense(byte[] content) {
        requireAvailable();
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(signingPrivateKey, secureRandom);
            signature.update(content);
            return signature.sign();
        } catch (Exception exception) {
            throw cryptographicFailure(exception);
        }
    }

    public PublicKey parseDevicePublicKey(String encoded) {
        try {
            byte[] der = decodeFlexible(encoded);
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof RSAPublicKey rsaKey)
                    || rsaKey.getModulus().bitLength() < 2048
                    || rsaKey.getModulus().bitLength() > 4096
                    || !BigInteger.valueOf(65537L).equals(rsaKey.getPublicExponent())) {
                throw new IllegalArgumentException("RSA public key must be 2048 to 4096 bits with exponent 65537");
            }
            return key;
        } catch (Exception exception) {
            throw new OfflineDownloadException(
                    "INVALID_DEVICE_KEY",
                    "The device public key is invalid or unsupported",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    public String canonicalPublicKeyBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public boolean verifyDeviceProof(PublicKey publicKey, byte[] challenge, String encodedSignature) {
        try {
            Signature verifier = Signature.getInstance("RSASSA-PSS");
            verifier.setParameter(new PSSParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    32,
                    1
            ));
            verifier.initVerify(publicKey);
            verifier.update(challenge);
            return verifier.verify(decodeFlexible(encodedSignature));
        } catch (Exception exception) {
            return false;
        }
    }

    public byte[] generateContentKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256, secureRandom);
            return generator.generateKey().getEncoded();
        } catch (Exception exception) {
            throw cryptographicFailure(exception);
        }
    }

    public String wrapContentKey(byte[] contentKey, PublicKey devicePublicKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    devicePublicKey,
                    new OAEPParameterSpec(
                            "SHA-256",
                            "MGF1",
                            MGF1ParameterSpec.SHA1,
                            PSource.PSpecified.DEFAULT
                    ),
                    secureRandom
            );
            return encodeUrl(cipher.doFinal(contentKey));
        } catch (Exception exception) {
            throw cryptographicFailure(exception);
        }
    }

    public byte[] encryptPage(byte[] plaintext, byte[] contentKey, byte[] nonce, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(contentKey, "AES"),
                    new GCMParameterSpec(128, nonce)
            );
            cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (Exception exception) {
            throw cryptographicFailure(exception);
        }
    }

    public byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        secureRandom.nextBytes(value);
        return value;
    }

    public String sha256Hex(byte[] input) {
        return java.util.HexFormat.of().formatHex(sha256(input));
    }

    public byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw cryptographicFailure(exception);
        }
    }

    public String encodeUrl(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public byte[] decodeFlexible(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Encoded value is blank");
        }
        String normalized = value.trim().replaceAll("\\s", "");
        try {
            return Base64.getUrlDecoder().decode(normalized);
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(normalized);
        }
    }

    public byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeConfiguredKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Signing key is missing");
        }
        String normalized = value
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private void validateConfiguration() {
        if (properties.getLicenseDuration() == null
                || properties.getLicenseDuration().isZero()
                || properties.getLicenseDuration().isNegative()
                || properties.getLicenseDuration().compareTo(java.time.Duration.ofDays(7)) > 0
                || properties.getChallengeTtl() == null
                || properties.getChallengeTtl().isZero()
                || properties.getChallengeTtl().isNegative()
                || properties.getChallengeTtl().compareTo(java.time.Duration.ofMinutes(10)) > 0
                || properties.getMaxDevicesPerUser() < 1
                || properties.getMaxChallengesPerHour() < 1
                || properties.getMaxPackagesPerHour() < 1
                || properties.getMaxLicensesPerHour() < 1
                || properties.getMaxPages() < 1
                || properties.getMaxPageBytes() < 1024
                || properties.getMaxPackageBytes() < properties.getMaxPageBytes()
                || properties.getAllowedImageHosts() == null
                || properties.getAllowedImageHosts().isEmpty()
                || isBlank(properties.getSigningKeyId())
                || isBlank(properties.getIssuer())
                || isBlank(properties.getAudience())) {
            throw new IllegalArgumentException("Offline download limits or identity settings are unsafe");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private OfflineDownloadException cryptographicFailure(Exception exception) {
        return new OfflineDownloadException(
                "OFFLINE_CRYPTOGRAPHY_FAILED",
                "The protected offline package could not be created",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
