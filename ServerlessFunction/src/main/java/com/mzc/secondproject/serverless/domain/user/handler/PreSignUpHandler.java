package com.mzc.secondproject.serverless.domain.user.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class PreSignUpHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(PreSignUpHandler.class);
    private static final String DEFAULT_PROFILE_URL = System.getenv("DEFAULT_PROFILE_URL");

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
                String defaultUrl = DEFAULT_PROFILE_URL != null
                        ? DEFAULT_PROFILE_URL
                        : "https://group2-englishstudy.s3.amazonaws.com/profile/default.png";
                userAttributes.put("custom:profileUrl", defaultUrl);
                logger.info("프로필 이미지 기본값: {}", defaultUrl);
            }
        } catch (Exception e) {
            logger.error("PreSignUp 트리거에서 오류가 발생했습니다");
        }

        return input;
    }
}
