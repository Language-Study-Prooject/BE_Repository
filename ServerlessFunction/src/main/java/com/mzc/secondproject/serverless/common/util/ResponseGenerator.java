package com.mzc.secondproject.serverless.common.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.dto.ErrorInfo;
import com.mzc.secondproject.serverless.common.exception.ErrorCode;

import java.util.Map;

/**
 * API Gateway 응답 생성기
 */
public final class ResponseGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, String> CORS_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS",
            "Access-Control-Allow-Headers", "Content-Type,Authorization"
    );

    private ResponseGenerator() {}

    // === 기본 응답 생성 ===

    public static APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(CORS_HEADERS)
                .withBody(GSON.toJson(body));
    }

    // === 성공 응답 ===

    public static <T> APIGatewayProxyResponseEvent ok(String message, T data) {
        return createResponse(200, ApiResponse.ok(message, data));
    }

    public static <T> APIGatewayProxyResponseEvent ok(T data) {
        return createResponse(200, ApiResponse.ok(data));
    }

    public static <T> APIGatewayProxyResponseEvent created(String message, T data) {
        return createResponse(201, ApiResponse.ok(message, data));
    }

    public static APIGatewayProxyResponseEvent noContent() {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(204)
                .withHeaders(CORS_HEADERS);
    }

    // === 실패 응답 ===

    public static APIGatewayProxyResponseEvent badRequest(String message) {
        return createResponse(400, ApiResponse.fail(message));
    }

    public static APIGatewayProxyResponseEvent unauthorized(String message) {
        return createResponse(401, ApiResponse.fail(message));
    }

    public static APIGatewayProxyResponseEvent forbidden(String message) {
        return createResponse(403, ApiResponse.fail(message));
    }

    public static APIGatewayProxyResponseEvent notFound(String message) {
        return createResponse(404, ApiResponse.fail(message));
    }

    public static APIGatewayProxyResponseEvent methodNotAllowed(String message) {
        return createResponse(405, ApiResponse.fail(message));
    }

    public static APIGatewayProxyResponseEvent conflict(String message) {
        return createResponse(409, ApiResponse.fail(message));
    }

    public static APIGatewayProxyResponseEvent serverError(String message) {
        return createResponse(500, ApiResponse.fail(message));
    }

    public static APIGatewayProxyResponseEvent fail(int statusCode, String message) {
        return createResponse(statusCode, ApiResponse.fail(message));
    }

    /**
     * ErrorCode 기반 에러 응답 생성
     */
    public static APIGatewayProxyResponseEvent fail(ErrorCode errorCode) {
        ErrorInfo errorInfo = ErrorInfo.from(errorCode);
        return createResponse(errorCode.getStatusCode(), errorInfo);
    }

    /**
     * ErrorCode 기반 에러 응답 생성 (커스텀 메시지)
     */
    public static APIGatewayProxyResponseEvent fail(ErrorCode errorCode, String customMessage) {
        ErrorInfo errorInfo = ErrorInfo.from(errorCode, customMessage);
        return createResponse(errorCode.getStatusCode(), errorInfo);
    }

    // === 유틸리티 ===

    public static Gson gson() {
        return GSON;
    }
}
