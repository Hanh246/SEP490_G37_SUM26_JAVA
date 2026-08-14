package com.sep.comiverse.unit.service;

import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.service.OfflineDownloadCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.AEADBadTagException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineDownloadCryptoServiceTest {

    @Test
    void encryptsPagesWithAes256GcmAndRejectsTampering() throws Exception {
        OfflineDownloadCryptoService service = initializedCrypto();
        byte[] contentKey = service.generateContentKey();
        byte[] nonce = service.randomBytes(12);
        byte[] aad = service.utf8("CVPK1|package|user|chapter|device|revision|1|hash");
        byte[] plaintext = "private comic page".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] ciphertext = service.encryptPage(plaintext, contentKey, nonce, aad);
        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(contentKey, "AES"),
                new GCMParameterSpec(128, nonce)
        );
        decrypt.updateAAD(aad);

        assertEquals(32, contentKey.length);
        assertArrayEquals(plaintext, decrypt.doFinal(ciphertext));

        ciphertext[0] ^= 1;
        Cipher tamperedDecrypt = Cipher.getInstance("AES/GCM/NoPadding");
        tamperedDecrypt.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(contentKey, "AES"),
                new GCMParameterSpec(128, nonce)
        );
        tamperedDecrypt.updateAAD(aad);
        assertThrows(AEADBadTagException.class, () -> tamperedDecrypt.doFinal(ciphertext));
    }

    @Test
    void wrapsContentKeyWithAndroidApi24CompatibleOaepParameters() throws Exception {
        OfflineDownloadCryptoService service = initializedCrypto();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair deviceKeyPair = generator.generateKeyPair();
        byte[] contentKey = service.generateContentKey();

        String wrapped = service.wrapContentKey(contentKey, deviceKeyPair.getPublic());
        Cipher unwrap = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        unwrap.init(
                Cipher.DECRYPT_MODE,
                deviceKeyPair.getPrivate(),
                new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA1,
                        PSource.PSpecified.DEFAULT
                )
        );

        assertEquals("RSA-OAEP-SHA256-MGF1SHA1", OfflineDownloadCryptoService.KEY_WRAP_ALGORITHM);
        assertArrayEquals(contentKey, unwrap.doFinal(service.decodeFlexible(wrapped)));
    }

    @Test
    void verifiesRsaPssDeviceProofWithExplicitParameters() throws Exception {
        OfflineDownloadCryptoService service = initializedCrypto();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair deviceKeyPair = generator.generateKeyPair();
        byte[] challenge = service.randomBytes(64);
        Signature signer = Signature.getInstance("RSASSA-PSS");
        signer.setParameter(new PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1
        ));
        signer.initSign(deviceKeyPair.getPrivate());
        signer.update(challenge);

        assertTrue(service.verifyDeviceProof(
                deviceKeyPair.getPublic(),
                challenge,
                service.encodeUrl(signer.sign())
        ));
    }

    static OfflineDownloadCryptoService initializedCrypto() throws Exception {
        OfflineDownloadProperties properties = enabledProperties();
        OfflineDownloadCryptoService service = new OfflineDownloadCryptoService(properties);
        ReflectionTestUtils.invokeMethod(service, "initialize");
        return service;
    }

    static OfflineDownloadProperties enabledProperties() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair signingKeys = generator.generateKeyPair();
        OfflineDownloadProperties properties = new OfflineDownloadProperties();
        properties.setEnabled(true);
        properties.setSigningPrivateKey(Base64.getEncoder().encodeToString(signingKeys.getPrivate().getEncoded()));
        properties.setSigningPublicKey(Base64.getEncoder().encodeToString(signingKeys.getPublic().getEncoded()));
        return properties;
    }
}
