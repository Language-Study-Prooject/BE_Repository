package com.mzc.secondproject.serverless.domain.user.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CognitoUserPoolPostConfirmationEvent;
import com.mzc.secondproject.serverless.domain.user.model.User;
import com.mzc.secondproject.serverless.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Cognito Post Confirmation 트리거 핸들러
 *
 * 사용자 이메일 인증을 완료한 직후 DB에 데이터 생성
 */
public class PostConfirmationHandler implements RequestHandler<CognitoUserPoolPostConfirmationEvent, CognitoUserPoolPostConfirmationEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(PostConfirmationHandler.class);
	private static final String DEFAULT_PROFILE_URL = "https://group2-englishstudy.s3.amazonaws.com/profile/default.png";
	
	private final UserRepository userRepository;
	
	public PostConfirmationHandler() {
		this.userRepository = new UserRepository();
	}
	
	@Override
	public CognitoUserPoolPostConfirmationEvent handleRequest(
			CognitoUserPoolPostConfirmationEvent event,
			Context context
	) {
		
		try {
			// 확인 완료 이벤트만 처리 (비밀번호 재설정 등은 무시)
			if (!"PostConfirmation_ConfirmSignUp".equals(event.getTriggerSource())) {
				return event;
			}
			
			Map<String, String> userAttributes = event.getRequest().getUserAttributes();
			
			// Cognito에서 사용자 정보 추출
			String cognitoSub = userAttributes.get("sub");
			String email = userAttributes.get("email");
			String nickname = userAttributes.get("nickname");
			String level = userAttributes.get("custom:level");
			String profileUrl = userAttributes.get("custom:profileUrl");
			
			logger.info("사용자 정보: cognitoSub={}, email={}", cognitoSub, email);
			
			// 중복 확인
			if (userRepository.findByCognitoSub(cognitoSub).isPresent()) {
				return event;
			}
			
			User newUser = User.createNew(
					cognitoSub,
					email,
					nickname != null ? nickname : generateDefaultNickname(),
					level != null ? level : "BEGINNER",
					profileUrl != null ? profileUrl : DEFAULT_PROFILE_URL
			);
			
			userRepository.save(newUser);
			logger.info("사용자 DynamoDB 저장 완료: email={}", email);
			
		} catch (Exception e) {
			// 예외가 발생해도 회원가입은 진행 - getProfile()에서 fallback으로 처리
		}
		
		return event;
	}
	
	/**
	 * 닉네임 기본값 생성
	 */
	private String generateDefaultNickname() {
		return UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "님";
	}
}
