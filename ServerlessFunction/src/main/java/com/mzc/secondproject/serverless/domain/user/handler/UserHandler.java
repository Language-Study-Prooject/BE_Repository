package com.mzc.secondproject.serverless.domain.user.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.util.Map;

public class UserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(UserHandler.class);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent request,
            Context context
    ) {
        // Cognito Authorizer에서 claims 추출
        Map<String, Object> claims = request.getRequestContext().getAuthorizer();

        String userId = "Unknown";
        String email = "Unknown";
        String nickname = "Unknown";

        if (claims != null) {
            Map<String, String> claimsMap = (Map<String, String>) claims.get("claims");
            if (claimsMap != null) {
                userId  = claimsMap.get("sub");
                email  = claimsMap.get("email");
                nickname = claimsMap.get("nickname");
            }
        }

        logger.info("인증된 사용자 : userId={}, email={}, nickname={}", userId, email, nickname);

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(nickname + " 환영합니다! (Email: " + email + ")");
    }

}
