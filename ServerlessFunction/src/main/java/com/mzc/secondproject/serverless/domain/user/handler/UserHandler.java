package com.mzc.secondproject.serverless.domain.user.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.util.HashMap;
import java.util.Map;

public class UserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(UserHandler.class);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent request,
            Context context
    ) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();

            // Cognito Authorizer에서 claims 추출
            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");

            if (claims == null) {
                return ResponseGenerator.fail(CommonErrorCode.INVALID_TOKEN, "claims가 존재하지 않습니다.");
            }

            String userId = claims.get("sub");
            String email = claims.get("email");
            String nickname = claims.get("nickname");

            logger.info("인증된 사용자 : userId={}, email={}, nickname={}", userId, email, nickname);

            Map<String, Object> data = new HashMap<>();
            data.put("userId", userId);
            data.put("email", email);
            data.put("nickname", nickname);

            return ResponseGenerator.ok(nickname + "환영합니다", data);
        } catch (Exception e){
            return ResponseGenerator.fail(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

}
