package com.mzc.secondproject.serverless.domain.user.service;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.user.model.User;
import com.mzc.secondproject.serverless.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Arrays;
import java.util.List;


public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String BUCKET_NAME = System.getenv("PROFILE_BUCKET_NAME");
    private static final String DEFAULT_PROFILE_URL = "https://group2-englishstudy.s3.amazonaws.com/profile/default.png";
    private static final List<String> VALID_LEVELS = Arrays.asList("BEGINNER", "INTERMEDIATE", "ADVANCED");

    private final UserRepository userRepository;
    private final S3Presigner s3Presigner;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        // AwsClients 싱글톤 사용 - Cold Start 최적화
        this.s3Presigner = AwsClients.s3Presigner();
    }

    /**
     * 프로필 조회
     * DynamoDB에 없으면(신규사용자) 프로필 정보 최초 생성
     *
     * @param cognitoSub Cognito sub
     * @param email 이메일 (Cognito claims)
     * @param nickname 닉네임 (Cognito claims)
     * @param level 레벨 (Cognito claims)
     * @param profileUrl 프로필 URL (Cognito claims)
     * @return User 객체
     */
    public User getProfile(String cognitoSub, String email, String nickname, String level, String profileUrl) {

        return userRepository.findByCognitoSub(cognitoSub)
                .map(user -> {
                    // 기존 사용자: 마지막 로그인 시간 갱신
                    user.updateLastLoginAt();
                    userRepository.update(user);
                    return user;
                })
                .orElseGet(() -> {
                    // 신규 사용자: 프로필 서비스 사용시점에 DB에 저장
                    // PreSignUpHandler 실패 시에도 기본값 설정
                    User newUser = User.createNew(
                            cognitoSub,
                            email,
                            nickname != null ? nickname : generateDefaultNickname(),
                            level != null ? level : "BEGINNER",
                            profileUrl != null ? profileUrl : DEFAULT_PROFILE_URL
                    );
                    return userRepository.save(newUser);
                });
    }

    /**
     * 프로필 수정 (닉네임, 레벨)
     */
    public User updateProfile(String cognitoSub, String nickname, String level) {
        logger.info("프로필 수정 요청: cognitoSub={}, nickname={}, level={}", cognitoSub, nickname, level);

        User user = userRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

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
    public User updateProfileImage(String cognitoSub, String imageUrl) {

        User user = userRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        user.updateProfileUrl(imageUrl);
        return userRepository.update(user);
    }


    private void validateNickname(String nickname) {
        if (nickname.length() < 2 || nickname.length() > 20) {
            throw new IllegalArgumentException("닉네임은 2~20자여야 합니다.");
        }
    }

    private void validateLevel(String level) {
        if (!VALID_LEVELS.contains(level)) {
            throw new IllegalArgumentException("유효하지 않은 레벨입니다.");
        }
    }

    private void validateImageContentType(String contentType) {
        List<String> validTypes = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");
        if (!validTypes.contains(contentType)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
        }
    }


    private String generateDefaultNickname() {
        return java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "님";
    }


}
