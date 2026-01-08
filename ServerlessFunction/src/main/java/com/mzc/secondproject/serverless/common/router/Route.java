package com.mzc.secondproject.serverless.common.router;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.function.Function;

/**
 * HTTP 라우트 정의
 * @param method HTTP 메서드 (GET, POST, PUT, DELETE 등)
 * @param pathPattern 경로 패턴 (예: "/rooms", "/rooms/{roomId}", "/rooms/{roomId}/join")
 * @param handler 요청 처리 함수
 */
public record Route(
        String method,
        String pathPattern,
        Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler
) {
    public static Route get(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
        return new Route("GET", pathPattern, handler);
    }

    public static Route post(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
        return new Route("POST", pathPattern, handler);
    }

    public static Route put(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
        return new Route("PUT", pathPattern, handler);
    }

    public static Route delete(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
        return new Route("DELETE", pathPattern, handler);
    }

    public static Route patch(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
        return new Route("PATCH", pathPattern, handler);
    }
}
