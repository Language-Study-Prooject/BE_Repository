package com.mzc.secondproject.serverless.domain.user.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.user.dto.request.ImageUploadRequest;
import com.mzc.secondproject.serverless.domain.user.dto.request.ProfileUpdateRequest;
import com.mzc.secondproject.serverless.domain.user.dto.response.ImageUploadResponse;
import com.mzc.secondproject.serverless.domain.user.dto.response.ProfileResponse;
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
	
	// HandlerRouter가 라우팅 + 파라미터 검증 + 예외 처리 모두 담당
	private final HandlerRouter router;
	
	public UserHandler() {
		UserRepository repository = new UserRepository();
		this.userService = new UserService(repository);
		this.router = initRouter();
	}
	
	private HandlerRouter initRouter() {
		
		return new HandlerRouter().addRoutes(
				Route.getAuth("/users/profile/me", this::getMyProfile),
				Route.putAuth("/users/profile/me", this::updateMyProfile),
				Route.postAuth("/users/profile/me/image", this::uploadProfileImage)
		);
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(
			APIGatewayProxyRequestEvent request,
			Context context
	) {
		return router.route(request);
	}
	
	/**
	 * GET /users/profile/me - 내 프로필 조회
	 */
	private APIGatewayProxyResponseEvent getMyProfile(
			APIGatewayProxyRequestEvent request,
			String userId // cognitoSub
	) {
		User user = userService.getProfile(userId, request);

		// profileUrl을 Presigned URL로 변환
		String presignedUrl = userService.getPresignedProfileUrl(user.getProfileUrl());

		ProfileResponse response = ProfileResponse.builder()
				.userId(user.getCognitoSub())
				.email(user.getEmail())
				.nickname(user.getNickname())
				.level(user.getLevel())
				.profileUrl(presignedUrl)  // Presigned URL 사용
				.createdAt(user.getCreatedAt())
				.updatedAt(user.getUpdatedAt())
				.build();
		
		return ResponseGenerator.ok(user.getNickname() + " 환영합니다!", response);
	}
	
	/**
	 * PUT /users/profile/me - 프로필 수정
	 */
	private APIGatewayProxyResponseEvent updateMyProfile(
			APIGatewayProxyRequestEvent requestEvent,
			String userId
	) {
		
		ProfileUpdateRequest updateRequest = gson.fromJson(requestEvent.getBody(), ProfileUpdateRequest.class);
		
		// 프로필 URL 수정
		if (updateRequest.getProfileUrl() != null && !updateRequest.getProfileUrl().isEmpty()) {
			userService.updateProfileImage(userId, updateRequest.getProfileUrl());
		}
		
		// 닉네임, 레벨 수정
		User user = userService.updateProfile(
				userId,
				updateRequest.getNickname(),
				updateRequest.getLevel()
		);
		
		ProfileResponse response = ProfileResponse.from(user);
		return ResponseGenerator.ok("프로필이 수정되었습니다.", response);
	}
	
	
	/**
	 * POST /users/profile/me/image - 프로필 이미지 업로드 URL 발급
	 */
	private APIGatewayProxyResponseEvent uploadProfileImage(
			APIGatewayProxyRequestEvent request,
			String userId
	) {
		ImageUploadRequest uploadRequest = gson.fromJson(request.getBody(), ImageUploadRequest.class);
		
		Map<String, String> urls = userService.generateProfileImageUploadUrl(
				userId,
				uploadRequest.getFileName(),
				uploadRequest.getContentType()
		);
		
		ImageUploadResponse response = ImageUploadResponse.builder()
				.uploadUrl(urls.get("uploadUrl"))
				.imageUrl(urls.get("imageUrl"))
				.build();
		
		return ResponseGenerator.ok("이미지 업로드 URL 발급 성공", response);
	}
	
}

