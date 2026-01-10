package com.mzc.secondproject.serverless.domain.user.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class UserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(UserHandler.class);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent request,
            Context context
    ) {
        // JwtAuthorizerHandler의 반환 값(IAM Policy)에서 principalId 꺼내기
        Map<String, Object> authData = request.getRequestContext().getAuthorizer();
        String user = "Unknown";
        if (authData != null) {
            user = (String) authData.get("principalId");
        }

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(user + "님 환영합니다");
    }

}
