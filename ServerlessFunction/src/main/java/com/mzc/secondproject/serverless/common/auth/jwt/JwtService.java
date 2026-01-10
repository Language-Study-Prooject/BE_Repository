package com.mzc.secondproject.serverless.common.auth.jwt;

import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    private static final String ALGORITHM = "HmacSHA256";
    private static final String SECRET_KEY = "ddingjoo-sajangnim-nabbayoo";
    private static final long EXPIRATION_TIME = 3600;

    /**
     * JWT 토큰 생성
     * @param userId 사용자 ID (sub 클레임)
     * @param claims 추가 클레임 (email, nickname, level 등)
     * @return JWT 토큰 문자열
     */
    public String generateToken(String userId, Map<String, Object> claims) {
        try {
            // Header
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String headerJson = ResponseGenerator.gson().toJson(header);
            String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

            // Payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", userId);  // subject (사용자 ID)
            payload.put("iat", System.currentTimeMillis() / 1000);  // issued at
            payload.put("exp", System.currentTimeMillis() / 1000 + EXPIRATION_TIME);  // expiration

            // 추가 클레임 병합
            if (claims != null) {
                payload.putAll(claims);
            }

            String payloadJson = ResponseGenerator.gson().toJson(payload);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            // Signature
            String headerPayload = encodedHeader + "." + encodedPayload;
            String signature = createSignature(headerPayload);

            // 최종 토큰
            String token = headerPayload + "." + signature;

            return token;

        } catch (Exception e) {
            throw new RuntimeException("토큰 생성을 실패했습니다", e);
        }
    }

    private String createSignature(String headerPayload) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(
                SECRET_KEY.getBytes(StandardCharsets.UTF_8),
                ALGORITHM
        );
        mac.init(keySpec);

        byte[] signatureBytes = mac.doFinal(headerPayload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
    }


    public String getUserId(String token) {
        Map<String, Object> claims = getClaims(token);
        return (String) claims.get("sub");
    }

    public boolean validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String headerPayload = parts[0] + "." + parts[1];
            String signature = parts[2];

            if (!verifySignature(headerPayload, signature)) {
                return false;
            }

            if (isTokenExpired(token)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.error("토큰 검증에 실패하였습니다", e);
            return false;
        }
    }


    public boolean isTokenExpired(String token) {
        try {
            Map<String, Object> claims = getClaims(token);
            Object expObj = claims.get("exp");

            if (expObj == null) {
                return false;
            }

            long exp;
            if (expObj instanceof Double) {
                exp = ((Double) expObj).longValue();
            } else {
                exp = Long.parseLong(expObj.toString());
            }

            long now = System.currentTimeMillis() / 1000;
            return now > exp;

        } catch (Exception e) {
            logger.error("Error checking token expiration", e);
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getClaims(String token) {
        String[] parts = token.split("\\.");
        String payload = parts[1];

        String decoded = new String(
                Base64.getUrlDecoder().decode(payload),
                StandardCharsets.UTF_8
        );

        return ResponseGenerator.gson().fromJson(decoded, Map.class);
    }


    private boolean verifySignature(String headerPayload, String signature) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8),
                    ALGORITHM
            );
            mac.init(keySpec);

            byte[] calculated = mac.doFinal(headerPayload.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(calculated);

            return expected.equals(signature);
        } catch (Exception e) {
            logger.error("Signature verification error", e);
            return false;
        }
    }
}
