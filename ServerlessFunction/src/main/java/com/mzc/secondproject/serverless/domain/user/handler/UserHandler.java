package com.mzc.secondproject.serverless.domain.user.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.user.dto.ImageUploadRequest;
import com.mzc.secondproject.serverless.domain.user.dto.ImageUploadResponse;
import com.mzc.secondproject.serverless.domain.user.dto.ProfileResponse;
import com.mzc.secondproject.serverless.domain.user.dto.ProfileUpdateRequest;
import com.mzc.secondproject.serverless.domain.user.model.User;
import com.mzc.secondproject.serverless.domain.user.repository.UserRepository;
import com.mzc.secondproject.serverless.domain.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class UserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(UserHandler.class);
    private static final Gson gson = new Gson();
    private final UserService userService;

    public UserHandler() {
        UserRepository repository = new UserRepository();
        this.userService = new UserService(repository);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent request,
            Context context
    ) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();

            String method = request.getHttpMethod();
            String path = request.getPath();

            // Cognito Authorizer에서 claims 추출
            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");

            if (claims == null) {
                return ResponseGenerator.fail(CommonErrorCode.INVALID_TOKEN, "claims가 존재하지 않습니다.");
            }

            // Cognito에서 사용자 정보 추출
            String cognitoSub = claims.get("sub");
            String email = claims.get("email");
            String nickname = claims.get("nickname");
            String level = claims.get("custom:level");
            String profileUrl = claims.get("custom:profileUrl");


            if (path.equals("/users/profile/me")) {
                switch (method) {
                    case "GET":
                        return getMyProfile(cognitoSub, email, nickname, level, profileUrl);
                    case "PUT":
                        return updateMyProfile(cognitoSub, request.getBody());
                    default:
                        return ResponseGenerator.fail(CommonErrorCode.METHOD_NOT_ALLOWED, "지원하지 않는 메서드입니다.");
                }
            }

            return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND, "지원하지 않는 경로입니다.");

        } catch (Exception e){
            return ResponseGenerator.fail(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /users/profile/me - 내 프로필 조회
     *
     * - Cognito claims에서 사용자 정보 추출
     * - DynamoDB에서 추가 정보 조회 (없으면 Lazy Registration)
     */
    private APIGatewayProxyResponseEvent getMyProfile(
            String cognitoSub,
            String email,
            String nickname,
            String level,
            String profileUrl
    ) {
        logger.info("프로필 조회 시작: cognitoSub={}", cognitoSub);

        User user = userService.getProfile(cognitoSub, email, nickname, level, profileUrl);
        ProfileResponse response = ProfileResponse.from(user);

        return ResponseGenerator.ok(user.getNickname() + " 환영합니다!", response);
    }

    /**
     * PUT /users/profile/me - 프로필 수정
     *
     * Request Body:
     * {
     *   "nickname": "새닉네임",
     *   "level": "INTERMEDIATE",
     *   "profileUrl": "https://..."
     * }
     */
    private APIGatewayProxyResponseEvent updateMyProfile(String cognitoSub, String body) {
        logger.info("프로필 수정 시작: cognitoSub={}", cognitoSub);

        if (body == null || body.isEmpty()) {
            return ResponseGenerator.fail(CommonErrorCode.REQUIRED_FIELD_MISSING, "요청 본문이 비어있습니다.");
        }

        ProfileUpdateRequest updateRequest = gson.fromJson(body, ProfileUpdateRequest.class);

//        // 프로필 URL만 수정하는 경우
//        if (updateRequest.getProfileUrl() != null && !updateRequest.getProfileUrl().isEmpty()) {
//            userService.updateProfileUrl(cognitoSub, updateRequest.getProfileUrl());
//        }

        // 닉네임, 레벨 수정
        User user = userService.updateProfile(
                cognitoSub,
                updateRequest.getNickname(),
                updateRequest.getLevel()
        );

        ProfileResponse response = ProfileResponse.from(user);
        return ResponseGenerator.ok("프로필이 수정되었습니다.", response);
    }


    @SuppressWarnings("unchecked")
    private Map<String, String> extractClaims(APIGatewayProxyRequestEvent request) {
        try {
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            if (authorizer == null) {
                logger.warn("Authorizer가 null입니다.");
                return null;
            }
            return (Map<String, String>) authorizer.get("claims");
        } catch (Exception e) {
            logger.error("claims 추출 실패", e);
            return null;
        }
    }

}

