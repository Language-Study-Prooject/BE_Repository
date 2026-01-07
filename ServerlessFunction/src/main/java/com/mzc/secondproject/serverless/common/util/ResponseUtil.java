package com.mzc.secondproject.serverless.common.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Map;

/**
 * API Gateway 응답 생성 유틸리티
 */
public final class ResponseUtil {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, String> CORS_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS",
            "Access-Control-Allow-Headers", "Content-Type,Authorization"
    );

    private ResponseUtil() {
        // 인스턴스화 방지
    }

    /**
     * JSON 응답 생성
     */
    public static APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(CORS_HEADERS)
                .withBody(GSON.toJson(body));
    }

    /**
     * 200 OK 응답
     */
    public static APIGatewayProxyResponseEvent ok(Object body) {
        return createResponse(200, body);
    }

    /**
     * 201 Created 응답
     */
    public static APIGatewayProxyResponseEvent created(Object body) {
        return createResponse(201, body);
    }

    /**
     * 400 Bad Request 응답
     */
    public static APIGatewayProxyResponseEvent badRequest(Object body) {
        return createResponse(400, body);
    }

    /**
     * 404 Not Found 응답
     */
    public static APIGatewayProxyResponseEvent notFound(Object body) {
        return createResponse(404, body);
    }

    /**
     * 500 Internal Server Error 응답
     */
    public static APIGatewayProxyResponseEvent serverError(Object body) {
        return createResponse(500, body);
    }

    /**
     * Gson 인스턴스 반환 (JSON 파싱용)
     */
    public static Gson gson() {
        return GSON;
    }
}
