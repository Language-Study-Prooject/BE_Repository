package com.mzc.secondproject.serverless.common.router;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.util.CognitoUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 라우트 정의
 *
 * Path 패턴에서 자동으로 필수 파라미터를 추출합니다.
 * 예: "/rooms/{roomId}/messages/{messageId}" → ["roomId", "messageId"]
 *
 * @param method              HTTP 메서드 (GET, POST, PUT, DELETE 등)
 * @param pathPattern         경로 패턴 (예: "/rooms", "/rooms/{roomId}", "/rooms/{roomId}/join")
 * @param handler             요청 처리 함수
 * @param requiredPathParams  필수 Path 파라미터 목록 (자동 추출)
 * @param requiredQueryParams 필수 Query 파라미터 목록 (선택적 지정)
 */
public record Route(
		String method,
		String pathPattern,
		Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler,
		List<String> requiredPathParams,
		List<String> requiredQueryParams
) {
	private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)}");
	
	/**
	 * Path 패턴에서 파라미터 이름 추출
	 */
	private static List<String> extractPathParams(String pathPattern) {
		List<String> params = new ArrayList<>();
		Matcher matcher = PATH_PARAM_PATTERN.matcher(pathPattern);
		while (matcher.find()) {
			params.add(matcher.group(1));
		}
		return Collections.unmodifiableList(params);
	}
	
	public static Route get(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
		return new Route("GET", pathPattern, handler, extractPathParams(pathPattern), List.of());
	}
	
	public static Route post(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
		return new Route("POST", pathPattern, handler, extractPathParams(pathPattern), List.of());
	}
	
	public static Route put(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
		return new Route("PUT", pathPattern, handler, extractPathParams(pathPattern), List.of());
	}
	
	public static Route delete(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
		return new Route("DELETE", pathPattern, handler, extractPathParams(pathPattern), List.of());
	}
	
	public static Route patch(String pathPattern, Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler) {
		return new Route("PATCH", pathPattern, handler, extractPathParams(pathPattern), List.of());
	}
	
	// ============ Cognito 인증 핸들러 메서드 ============
	
	/**
	 * Cognito 인증이 필요한 GET 라우트
	 * userId가 자동으로 추출되어 핸들러에 전달됩니다.
	 */
	public static Route getAuth(String pathPattern, AuthenticatedHandler handler) {
		return new Route("GET", pathPattern,
				request -> handler.handle(request, CognitoUtil.extractUserId(request)),
				extractPathParams(pathPattern), List.of());
	}
	
	/**
	 * Cognito 인증이 필요한 POST 라우트
	 */
	public static Route postAuth(String pathPattern, AuthenticatedHandler handler) {
		return new Route("POST", pathPattern,
				request -> handler.handle(request, CognitoUtil.extractUserId(request)),
				extractPathParams(pathPattern), List.of());
	}
	
	/**
	 * Cognito 인증이 필요한 PUT 라우트
	 */
	public static Route putAuth(String pathPattern, AuthenticatedHandler handler) {
		return new Route("PUT", pathPattern,
				request -> handler.handle(request, CognitoUtil.extractUserId(request)),
				extractPathParams(pathPattern), List.of());
	}
	
	/**
	 * Cognito 인증이 필요한 DELETE 라우트
	 */
	public static Route deleteAuth(String pathPattern, AuthenticatedHandler handler) {
		return new Route("DELETE", pathPattern,
				request -> handler.handle(request, CognitoUtil.extractUserId(request)),
				extractPathParams(pathPattern), List.of());
	}
	
	/**
	 * Cognito 인증이 필요한 PATCH 라우트
	 */
	public static Route patchAuth(String pathPattern, AuthenticatedHandler handler) {
		return new Route("PATCH", pathPattern,
				request -> handler.handle(request, CognitoUtil.extractUserId(request)),
				extractPathParams(pathPattern), List.of());
	}
	
	/**
	 * 필수 Query 파라미터 추가
	 */
	public Route requireQueryParams(String... params) {
		return new Route(method, pathPattern, handler, requiredPathParams, List.of(params));
	}
}
