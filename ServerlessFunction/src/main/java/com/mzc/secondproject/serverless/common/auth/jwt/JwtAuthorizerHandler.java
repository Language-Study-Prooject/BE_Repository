package com.mzc.secondproject.serverless.common.auth.jwt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.mzc.secondproject.serverless.common.exception.CommonException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Lambda Authorizer - JWT 토큰 검증
 */
public class JwtAuthorizerHandler implements RequestHandler<APIGatewayCustomAuthorizerEvent, Map<String, Object>> {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthorizerHandler.class);

    private final JwtService jwtService;

    public JwtAuthorizerHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Map<String, Object> handleRequest(APIGatewayCustomAuthorizerEvent input, Context context) {

        String authoriztaionToken = input.getAuthorizationToken();

        try {
            String token = extractBearerToken(authoriztaionToken);

            Map<String, Object> claims = validateToken(token);

            String userId = (String) claims.get("userId");

            return generateIamPolicy(userId, "Allow", input.getMethodArn());

        } catch (CommonException e) {
            return generateIamPolicy("anonymous", "Deny", input.getMethodArn());
        }

    }

    // Authorization 헤더에서 Bearer 토큰 추출
    private String extractBearerToken(String authorizationToken) {

        if (authorizationToken == null || authorizationToken.isEmpty()) {
            throw CommonException.unauthorized("Authorization 헤더가 존재하지 않습니다");
        }

        if (authorizationToken != null && authorizationToken.startsWith("Bearer ")) {
            authorizationToken = authorizationToken.substring(7);
        }

        if (authorizationToken.isEmpty()) {
            throw CommonException.invalidToken();
        }

        return authorizationToken;

    }


    // JWT 토큰 검증 및 Claims 추축
    private Map<String, Object> validateToken(String token) {

        if (!jwtService.validateToken(token)) {
            if(jwtService.isTokenExpired(token)){
                throw CommonException.tokenExpired();
            };
            throw CommonException.invalidToken();
        }

        return jwtService.getClaims(token);
    }


    // IAM Policy JSON 생성
    private Map<String, Object> generateIamPolicy(String principalId, String effect, String resource) {

        Map<String, Object> policy = new HashMap<>();
        policy.put("principalId", principalId);

        Map<String, Object> policyDocument = new HashMap<>();
        policyDocument.put("Version", "2012-10-17");

        Map<String, Object> statement = new HashMap<>();
        statement.put("Action", "execute-api:Invoke");
        statement.put("Effect", effect);
        statement.put("Resource", resource);

        policyDocument.put("Statement", Collections.singletonList(statement));
        policy.put("policyDocument", policyDocument);

        return policy;
    }
}
