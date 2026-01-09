package com.mzc.secondproject.serverless.common.router;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.exception.ServerlessException;
import com.mzc.secondproject.serverless.common.dto.ErrorInfo;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lambda Handler를 위한 HTTP 라우터
 *
 * 선언적 라우팅 + 자동 Path/Query 파라미터 검증 제공
 *
 * 사용 예시:
 * <pre>
 * new HandlerRouter().addRoutes(
 *     Route.get("/rooms/{roomId}", this::getRoom),  // roomId 자동 검증
 *     Route.delete("/rooms/{roomId}", this::deleteRoom).requireQueryParams("userId")  // roomId + userId 검증
 * );
 * </pre>
 */
public class HandlerRouter {

    private static final Logger logger = LoggerFactory.getLogger(HandlerRouter.class);

    private final List<RouteEntry> routes = new ArrayList<>();

    /**
     * 라우트 등록
     */
    public HandlerRouter addRoute(Route route) {
        String regex = convertPatternToRegex(route.pathPattern());
        Pattern pattern = Pattern.compile(regex);
        routes.add(new RouteEntry(route, pattern));
        return this;
    }

    /**
     * 여러 라우트 한번에 등록
     */
    public HandlerRouter addRoutes(Route... routeArray) {
        for (Route route : routeArray) {
            addRoute(route);
        }
        return this;
    }

    /**
     * 요청을 적절한 핸들러로 라우팅
     */
    public APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent request) {
        String method = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Routing request: {} {}", method, path);

        for (RouteEntry entry : routes) {
            if (entry.matches(method, path)) {
                logger.debug("Matched route: {} {}", entry.route.method(), entry.route.pathPattern());

                // Path/Query 파라미터 자동 검증
                String validationError = validateParams(request, entry.route);
                if (validationError != null) {
                    logger.warn("Validation failed: {}", validationError);
                    return ResponseGenerator.fail(CommonErrorCode.REQUIRED_FIELD_MISSING, validationError);
                }

                try {
                    return entry.route.handler().apply(request);
                } catch (ServerlessException e) {
                    return handleServerlessException(e);
                } catch (IllegalArgumentException e) {
                    logger.warn("Bad request: {}", e.getMessage());
                    return ResponseGenerator.fail(CommonErrorCode.INVALID_INPUT, e.getMessage());
                } catch (IllegalStateException e) {
                    logger.warn("Conflict: {}", e.getMessage());
                    return ResponseGenerator.fail(CommonErrorCode.RESOURCE_ALREADY_EXISTS, e.getMessage());
                } catch (SecurityException e) {
                    logger.warn("Forbidden: {}", e.getMessage());
                    return ResponseGenerator.fail(CommonErrorCode.FORBIDDEN, e.getMessage());
                } catch (Exception e) {
                    logger.error("Error handling request", e);
                    return ResponseGenerator.fail(CommonErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
        }

        logger.warn("No route found for: {} {}", method, path);
        return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * Path/Query 파라미터 검증
     *
     * @return 에러 메시지 (검증 성공 시 null)
     */
    private String validateParams(APIGatewayProxyRequestEvent request, Route route) {
        List<String> missingParams = new ArrayList<>();

        // Path 파라미터 검증
        Map<String, String> pathParams = request.getPathParameters();
        for (String param : route.requiredPathParams()) {
            if (pathParams == null || isBlank(pathParams.get(param))) {
                missingParams.add(param);
            }
        }

        // Query 파라미터 검증
        Map<String, String> queryParams = request.getQueryStringParameters();
        for (String param : route.requiredQueryParams()) {
            if (queryParams == null || isBlank(queryParams.get(param))) {
                missingParams.add(param);
            }
        }

        if (missingParams.isEmpty()) {
            return null;
        }

        return missingParams.stream()
                .map(p -> p + " is required")
                .collect(Collectors.joining(", "));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 경로 패턴을 정규식으로 변환
     * /rooms/{roomId} -> /rooms/[^/]+
     * /rooms/{roomId}/join -> /rooms/[^/]+/join
     */
    private String convertPatternToRegex(String pathPattern) {
        String regex = pathPattern
                .replaceAll("\\{[^}]+\\}", "[^/]+")
                .replace("/", "\\/");
        return ".*" + regex + "$";
    }

    /**
     * ServerlessException 처리
     * ErrorCode 기반의 표준화된 에러 응답 생성
     */
    private APIGatewayProxyResponseEvent handleServerlessException(ServerlessException e) {
        ErrorInfo errorInfo = ErrorInfo.from(e);

        if (e.isClientError()) {
            logger.warn("Client error [{}]: {}", errorInfo.code(), e.getMessage());
        } else {
            logger.error("Server error [{}]: {}", errorInfo.code(), e.getMessage(), e);
        }

        return ResponseGenerator.createResponse(e.getStatusCode(), errorInfo);
    }

    /**
     * 라우트 엔트리 (라우트 + 컴파일된 패턴)
     */
    private record RouteEntry(Route route, Pattern pattern) {
        boolean matches(String method, String path) {
            if (!route.method().equalsIgnoreCase(method)) {
                return false;
            }
            Matcher matcher = pattern.matcher(path);
            return matcher.matches();
        }
    }
}
