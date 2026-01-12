package com.mzc.secondproject.serverless.common.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;

import java.util.Map;
import java.util.Optional;

/**
 * Cognito Authorizer에서 사용자 정보를 추출하는 유틸리티 클래스
 */
public class CognitoUtil {

    private CognitoUtil() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /**
     * Cognito claims에서 userId(sub)를 추출
     *
     * @param request API Gateway 요청
     * @return userId (Cognito sub)
     * @throws IllegalStateException claims가 없거나 sub가 없는 경우
     */
    public static String extractUserId(APIGatewayProxyRequestEvent request) {
        return extractClaim(request, "sub")
                .orElseThrow(() -> new IllegalStateException("Cognito sub claim not found"));
    }

    /**
     * Cognito claims에서 email을 추출
     *
     * @param request API Gateway 요청
     * @return email (Optional)
     */
    public static Optional<String> extractEmail(APIGatewayProxyRequestEvent request) {
        return extractClaim(request, "email");
    }

    /**
     * Cognito claims에서 nickname을 추출
     *
     * @param request API Gateway 요청
     * @return nickname (Optional)
     */
    public static Optional<String> extractNickname(APIGatewayProxyRequestEvent request) {
        return extractClaim(request, "custom:nickname");
    }

    /**
     * Cognito claims에서 특정 claim을 추출
     *
     * @param request API Gateway 요청
     * @param claimName claim 이름
     * @return claim 값 (Optional)
     */
    @SuppressWarnings("unchecked")
    public static Optional<String> extractClaim(APIGatewayProxyRequestEvent request, String claimName) {
        try {
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            if (authorizer == null) {
                return Optional.empty();
            }

            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            if (claims == null) {
                return Optional.empty();
            }

            return Optional.ofNullable(claims.get(claimName));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 경로 파라미터의 userId와 Cognito userId가 일치하는지 검증
     *
     * @param request API Gateway 요청
     * @param pathUserId 경로 파라미터에서 추출한 userId
     * @return 일치 여부
     */
    public static boolean validateUserAccess(APIGatewayProxyRequestEvent request, String pathUserId) {
        try {
            String cognitoUserId = extractUserId(request);
            return cognitoUserId.equals(pathUserId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 경로 파라미터와 Cognito userId를 검증하고, 유효한 경우 userId 반환
     * 검증 실패 시 Optional.empty() 반환
     *
     * @param request API Gateway 요청
     * @param pathUserId 경로 파라미터에서 추출한 userId
     * @return 검증된 userId (Optional)
     */
    public static Optional<String> validateAndExtractUserId(APIGatewayProxyRequestEvent request, String pathUserId) {
        try {
            String cognitoUserId = extractUserId(request);
            if (cognitoUserId.equals(pathUserId)) {
                return Optional.of(cognitoUserId);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 경로 파라미터에서 userId를 추출하고 Cognito 토큰과 검증
     * Handler에서 간편하게 사용할 수 있는 메서드
     *
     * @param request API Gateway 요청
     * @return 검증된 userId (Optional)
     */
    public static Optional<String> getValidatedUserId(APIGatewayProxyRequestEvent request) {
        String pathUserId = request.getPathParameters() != null
                ? request.getPathParameters().get("userId")
                : null;

        if (pathUserId == null) {
            // 경로에 userId가 없으면 토큰에서만 추출
            return extractClaim(request, "sub");
        }

        return validateAndExtractUserId(request, pathUserId);
    }
}
