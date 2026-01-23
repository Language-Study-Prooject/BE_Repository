package com.mzc.secondproject.serverless.domain.user.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class PreSignUpHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	
	private static final Logger logger = LoggerFactory.getLogger(PreSignUpHandler.class);
	private static final String BUCKET_NAME = System.getenv("BUCKET_NAME");
	private static final String DEFAULT_PROFILE_URL = getDefaultProfileUrl();
	
	private static String getDefaultProfileUrl() {
		String envUrl = System.getenv("DEFAULT_PROFILE_URL");
		if (envUrl != null && !envUrl.isEmpty()) {
			return envUrl;
		}
		String bucket = BUCKET_NAME != null ? BUCKET_NAME : "group2-englishstudy";
		return String.format("https://%s.s3.amazonaws.com/profile/default.png", bucket);
	}
	
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
		
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> request = (Map<String, Object>) input.get("request");
			
			@SuppressWarnings("unchecked")
			Map<String, String> userAttributes = (Map<String, String>) request.get("userAttributes");
			
			String nickname = userAttributes.get("nickname");
			if (nickname == null || nickname.trim().isEmpty()) {
				String defaultNickname = UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "님";
				userAttributes.put("nickname", defaultNickname);
				logger.info("nickname 기본값: {}", defaultNickname);
			}
			
			String level = userAttributes.get("custom:level");
			if (level == null || level.trim().isEmpty()) {
				userAttributes.put("custom:level", "BEGINNER");
				logger.info("level 선택 기본값: BEGINNER");
			}
			
			String profileUrl = userAttributes.get("custom:profileUrl");
			if (profileUrl == null || profileUrl.trim().isEmpty()) {
				userAttributes.put("custom:profileUrl", DEFAULT_PROFILE_URL);
				logger.info("프로필 이미지 기본값: {}", DEFAULT_PROFILE_URL);
			}
			
			return input;
			
		} catch (Exception e) {
			logger.error("PreSignUp 트리거에서 오류가 발생했습니다");
			throw new RuntimeException("회원가입 처리 중 오류가 발생했습니다: " + e.getMessage());
		}
		
	}
}
