package com.mzc.secondproject.serverless.common.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * JWT 토큰 유틸리티
 * Cognito JWT 토큰에서 claims를 추출
 */
public final class JwtUtil {
	
	private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
	private static final Gson gson = new Gson();
	
	private JwtUtil() {
		// 유틸리티 클래스 인스턴스화 방지
	}
	
	/**
	 * JWT 토큰에서 userId (sub claim) 추출
	 *
	 * @param token JWT 토큰 (Bearer 접두사 없이)
	 * @return userId (Optional)
	 */
	public static Optional<String> extractUserId(String token) {
		return extractClaim(token, "sub");
	}
	
	/**
	 * JWT 토큰에서 email 추출
	 */
	public static Optional<String> extractEmail(String token) {
		return extractClaim(token, "email");
	}
	
	/**
	 * JWT 토큰에서 특정 claim 추출
	 *
	 * @param token     JWT 토큰
	 * @param claimName claim 이름
	 * @return claim 값 (Optional)
	 */
	public static Optional<String> extractClaim(String token, String claimName) {
		try {
			if (token == null || token.isEmpty()) {
				return Optional.empty();
			}
			
			// Bearer 접두사 제거
			String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
			
			// JWT 구조: header.payload.signature
			String[] parts = cleanToken.split("\\.");
			if (parts.length != 3) {
				logger.warn("Invalid JWT format");
				return Optional.empty();
			}
			
			// Payload 디코딩 (Base64 URL-safe)
			String payload = new String(
					Base64.getUrlDecoder().decode(parts[1]),
					StandardCharsets.UTF_8
			);
			
			JsonObject claims = gson.fromJson(payload, JsonObject.class);
			
			if (claims.has(claimName) && !claims.get(claimName).isJsonNull()) {
				return Optional.of(claims.get(claimName).getAsString());
			}
			
			return Optional.empty();
			
		} catch (Exception e) {
			logger.error("Failed to extract claim from JWT: {}", e.getMessage());
			return Optional.empty();
		}
	}
	
	/**
	 * JWT 토큰이 만료되었는지 확인
	 *
	 * @param token JWT 토큰
	 * @return 만료 여부 (true = 만료됨)
	 */
	public static boolean isExpired(String token) {
		try {
			Optional<String> expClaim = extractClaim(token, "exp");
			if (expClaim.isEmpty()) {
				return true;
			}
			
			long exp = Long.parseLong(expClaim.get());
			long now = System.currentTimeMillis() / 1000;
			
			return now >= exp;
			
		} catch (Exception e) {
			logger.error("Failed to check JWT expiration: {}", e.getMessage());
			return true;
		}
	}
	
	/**
	 * JWT 토큰 유효성 검사 (형식 및 만료)
	 *
	 * @param token JWT 토큰
	 * @return 유효 여부
	 */
	public static boolean isValid(String token) {
		if (token == null || token.isEmpty()) {
			return false;
		}
		
		Optional<String> userId = extractUserId(token);
		if (userId.isEmpty()) {
			return false;
		}
		
		return !isExpired(token);
	}
}
