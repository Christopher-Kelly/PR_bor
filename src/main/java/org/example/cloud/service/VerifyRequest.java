package org.example.cloud.service;

import org.example.cloud.config.GithubConfig;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class VerifyRequest {

    private final byte[] secret;

    public VerifyRequest(GithubConfig config) {
        this.secret = config.webhook().secret().getBytes(StandardCharsets.UTF_8);
    }

    /** Throws if the signature is missing or doesn't match. */
    public void verifyRequest(String signatureHeader, String rawBody) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            throw new SecurityException("missing or malformed signature");
        }

        String expected = "sha256=" + hmacSha256(rawBody);

        // constant-time compare — don't use .equals() on security tokens
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));

        if (!matches) {
            throw new SecurityException("signature mismatch");
        }
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new SecurityException("HMAC computation failed", e);
        }
    }
}