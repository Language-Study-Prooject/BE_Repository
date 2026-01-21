package com.mzc.secondproject.serverless.common.router;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

/**
 * Cognito 인증이 필요한 요청 핸들러
 *
 * userId가 자동으로 추출되어 전달됩니다.
 */
@FunctionalInterface
public interface AuthenticatedHandler {
	APIGatewayProxyResponseEvent handle(APIGatewayProxyRequestEvent request, String userId);
}
