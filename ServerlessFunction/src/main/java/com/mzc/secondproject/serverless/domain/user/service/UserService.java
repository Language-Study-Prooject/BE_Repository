package com.mzc.secondproject.serverless.domain.user.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.user.exception.UserException;
import com.mzc.secondproject.serverless.domain.user.model.User;
import com.mzc.secondproject.serverless.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class UserService {
	
	private static final Logger logger = LoggerFactory.getLogger(UserService.class);
	private static final String BUCKET_NAME = System.getenv("PROFILE_BUCKET_NAME");
	private static final String DEFAULT_PROFILE_URL = getDefaultProfileUrl();

	private static String getDefaultProfileUrl() {
		String bucket = BUCKET_NAME != null ? BUCKET_NAME : "group2-englishstudy";
		return String.format("https://%s.s3.amazonaws.com/profile/default.png", bucket);
	}
	private static final List<String> VALID_LEVELS = Arrays.asList("BEGINNER", "INTERMEDIATE", "ADVANCED");
	
	private static final List<String> VALID_IMAGE_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");
	private static final int NICKNAME_MIN_LENGTH = 2;
	private static final int NICKNAME_MAX_LENGTH = 20;
	
	private final UserRepository userRepository;
	private final S3Presigner s3Presigner;
	
	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
		// AwsClients 싱글톤 사용 - Cold Start 최적화
		this.s3Presigner = AwsClients.s3Presigner();
	}
	
	/**
	 * 프로필 조회
	 * DynamoDB에 없으면 request에서 claims 추출 → fallback 저장
	 *
	 * @param userId  Cognito sub
	 * @param request API Gateway 요청 (fallback 시 claims 추출용)
	 * @return User 객체
	 */
	public User getProfile(String userId, APIGatewayProxyRequestEvent request) {
		
		return userRepository.findByCognitoSub(userId)
				.map(user -> {
					// 정상 DB에서 조회 완료
					user.updateLastLoginAt();
					userRepository.update(user);
					return user;
				})
				.orElseGet(() -> {
					// PostConfirmation 실패 대비 fallback
					return createUserFromRequest(userId, request);
				});
	}
	
	/**
	 * request에서 Cognito claims 추출 후 사용자 생성 (fallback용)
	 */
	@SuppressWarnings("unchecked")
	private User createUserFromRequest(String userId, APIGatewayProxyRequestEvent request) {
		
		Map<String, String> claims = null;
		try {
			Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
			if (authorizer != null) {
				claims = (Map<String, String>) authorizer.get("claims");
			}
		} catch (Exception e) {
			logger.error("claims 추출 실패", e);
		}
		
		// claims에서 정보 추출
		String email = claims != null ? claims.get("email") : "unknown@example.com";
		String nickname = claims != null ? claims.get("nickname") : null;
		String level = claims != null ? claims.get("custom:level") : null;
		String profileUrl = claims != null ? claims.get("custom:profileUrl") : null;
		
		User newUser = User.createNew(
				userId,
				email,
				nickname != null ? nickname : generateDefaultNickname(),
				level != null ? level : "BEGINNER",
				profileUrl != null ? profileUrl : DEFAULT_PROFILE_URL
		);
		
		return userRepository.save(newUser);
	}
	
	
	/**
	 * 프로필 수정 (닉네임, 레벨)
	 *
	 * @param userId   cognitoSub
	 * @param nickname 새 닉네임 (null이면 변경 안 함)
	 * @param level    새 레벨 (null이면 변경 안 함)
	 * @return 수정된 User 객체
	 * @throws UserException USER_NOT_FOUND, INVALID_NICKNAME, INVALID_LEVEL
	 */
	public User updateProfile(String userId, String nickname, String level) {
		logger.info("프로필 수정 요청: userId={}, nickname={}, level={}", userId, nickname, level);
		
		User user = userRepository.findByCognitoSub(userId)
				.orElseThrow(() -> UserException.userNotFound(userId));
		
		// 닉네임 수정
		if (nickname != null && !nickname.trim().isEmpty()) {
			validateNickname(nickname);
			user.updateNickname(nickname);
		}
		
		// 레벨 수정
		if (level != null && !level.trim().isEmpty()) {
			validateLevel(level);
			user.updateLevel(level);
		}
		
		User updatedUser = userRepository.update(user);
		logger.info("프로필 수정 완료: email={}", updatedUser.getEmail());
		
		return updatedUser;
	}
	
	
	/**
	 * 프로필 이미지 URL 업데이트 (업로드 완료 후 호출)
	 */
	public User updateProfileImage(String userId, String imageUrl) {
		
		User user = userRepository.findByCognitoSub(userId)
				.orElseThrow(() -> UserException.userNotFound(userId));
		
		user.updateProfileUrl(imageUrl);
		return userRepository.update(user);
	}
	
	/**
	 * 프로필 이미지 업로드를 위한 Presigned URL 발급
	 *
	 * @param userId      cognitoSub
	 * @param fileName    파일명
	 * @param contentType MIME 타입
	 * @return {uploadUrl, imageUrl}
	 * @throws UserException INVALID_IMAGE_TYPE
	 */
	public Map<String, String> generateProfileImageUploadUrl(String userId, String fileName, String contentType) {
		
		validateImageContentType(contentType);
		
		String objectKey = String.format("profile/%s/%s", userId, fileName);
		String imageUrl = String.format("https://%s.s3.amazonaws.com/%s", BUCKET_NAME, objectKey);
		
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(BUCKET_NAME)
				.key(objectKey)
				.contentType(contentType)
				.build();
		
		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
				.signatureDuration(Duration.ofMinutes(10))
				.putObjectRequest(putObjectRequest)
				.build();
		
		PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
		String uploadUrl = presignedRequest.url().toString();
		
		updateProfileImage(userId, imageUrl);
		
		logger.info("Presigned URL 생성 완료: objectKey={}", objectKey);
		
		return Map.of(
				"uploadUrl", uploadUrl,
				"imageUrl", imageUrl
		);
	}
	
	
	private void validateNickname(String nickname) {
		if (nickname.length() < 2 || nickname.length() > 20) {
			throw UserException.invalidNickname(nickname, NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH);
		}
	}
	
	private void validateLevel(String level) {
		if (!VALID_LEVELS.contains(level)) {
			throw UserException.invalidLevel(level);
		}
	}
	
	private void validateImageContentType(String contentType) {
		if (!VALID_IMAGE_TYPES.contains(contentType)) {
			throw UserException.invalidImageType(contentType);
		}
	}
	
	
	private String generateDefaultNickname() {
		return java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "님";
	}
	
	
}
