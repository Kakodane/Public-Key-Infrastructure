package com.ib.service.registration;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class RegistrationChallengeService {

        public static final int CHALLENGE_BYTES = 32;
        private static final int MIN_RSA_MODULUS_BITS = 2048;

        private final SecureRandom secureRandom = new SecureRandom();

        public RSAPublicKey parsePublicKeyPem(String pem) {
            if (pem == null || pem.isBlank()) {
                throw new IllegalArgumentException("Public key is required");
            }
            String base64 = canonicalizePem(pem);
            byte[] der;
            try {
                der = Base64.getDecoder().decode(base64);
            }
            catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Public key must be a PEM-encoded SubjectPublicKeyInfo", ex);
            }
            PublicKey key;
            try {
                key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            }
            catch (Exception ex) {
                throw new IllegalArgumentException("Public key could not be parsed as RSA SubjectPublicKeyInfo", ex);
            }
            if (!(key instanceof RSAPublicKey rsa)) {
                throw new IllegalArgumentException("Public key must be RSA");
            }
            if (rsa.getModulus().bitLength() < MIN_RSA_MODULUS_BITS) {
                throw new IllegalArgumentException("RSA public key must be at least " + MIN_RSA_MODULUS_BITS + " bits");
            }
            return rsa;
        }

        public Challenge createChallenge(RSAPublicKey publicKey) {
            byte[] plain = new byte[CHALLENGE_BYTES];
            secureRandom.nextBytes(plain);
            byte[] ciphertext;
            try {
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                OAEPParameterSpec params = new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA256,
                        PSource.PSpecified.DEFAULT
                );
                cipher.init(Cipher.ENCRYPT_MODE, publicKey, params);
                ciphertext = cipher.doFinal(plain);
            }
            catch (Exception ex) {
                throw new IllegalStateException("Failed to encrypt registration challenge", ex);
            }
            Base64.Encoder enc = Base64.getEncoder();
            return new Challenge(enc.encodeToString(plain), enc.encodeToString(ciphertext));
        }


        public boolean matches(String expectedBase64, String suppliedBase64) {
            if (expectedBase64 == null || suppliedBase64 == null) {
                return false;
            }
            byte[] a;
            byte[] b;
            try {
                a = Base64.getDecoder().decode(expectedBase64);
                b = Base64.getDecoder().decode(suppliedBase64);
            }
            catch (IllegalArgumentException ex) {
                return false;
            }
            if (a.length != b.length) {
                return false;
            }
            int diff = 0;
            for (int i = 0; i < a.length; i++) {
                diff |= a[i] ^ b[i];
            }
            return diff == 0;
        }

        public String canonicalizePem(String pem) {
            if (pem == null) {
                return "";
            }
            return pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
        }

        public record Challenge(String plainBase64, String encryptedBase64) {
        }
}
