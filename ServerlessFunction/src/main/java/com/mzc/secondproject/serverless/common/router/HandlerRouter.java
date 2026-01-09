package com.mzc.secondproject.serverless.common.router;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.exception.ServerlessException;
import com.mzc.secondproject.serverless.common.dto.ErrorInfo;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lambda Handler를 위한 HTTP 라우터
 * if-else 체인 대신 선언적 라우팅 제공
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
                try {
                    return entry.route.handler().apply(request);
                } catch (ServerlessException e) {
                    return handleServerlessException(e);
                } catch (IllegalArgumentException e) {
                    logger.warn("Bad request: {}", e.getMessage());
                    return createResponse(400, ApiResponse.fail(e.getMessage()));
                } catch (IllegalStateException e) {
                    logger.warn("Conflict: {}", e.getMessage());
                    return createResponse(409, ApiResponse.fail(e.getMessage()));
                } catch (SecurityException e) {
                    logger.warn("Forbidden: {}", e.getMessage());
                    return createResponse(403, ApiResponse.fail(e.getMessage()));
                } catch (Exception e) {
                    logger.error("Error handling request", e);
                    return createResponse(500, ApiResponse.fail("Internal server error: " + e.getMessage()));
                }
            }
        }

        logger.warn("No route found for: {} {}", method, path);
        return createResponse(404, ApiResponse.fail("Not found"));
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

        return createResponse(e.getStatusCode(), errorInfo);
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
