package com.mzc.secondproject.serverless.domain.badge.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.badge.model.UserBadge;
import com.mzc.secondproject.serverless.domain.badge.service.BadgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BadgeHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(BadgeHandler.class);

    private final BadgeService badgeService;
    private final HandlerRouter router;

    public BadgeHandler() {
        this.badgeService = new BadgeService();
        this.router = initRouter();
    }

    private HandlerRouter initRouter() {
        return new HandlerRouter().addRoutes(
                Route.getAuth("/badges", this::getAllBadges),
                Route.getAuth("/badges/earned", this::getEarnedBadges)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    /**
     * 전체 뱃지 목록 조회 (획득 여부, 진행도 포함)
     */
    private APIGatewayProxyResponseEvent getAllBadges(APIGatewayProxyRequestEvent request, String userId) {
        List<BadgeService.BadgeInfo> badges = badgeService.getAllBadgesWithStatus(userId);

        long earnedCount = badges.stream().filter(BadgeService.BadgeInfo::earned).count();

        Map<String, Object> response = new HashMap<>();
        response.put("badges", badges);
        response.put("totalCount", badges.size());
        response.put("earnedCount", earnedCount);

        return ResponseGenerator.ok("Badges retrieved", response);
    }

    /**
     * 획득한 뱃지만 조회
     */
    private APIGatewayProxyResponseEvent getEarnedBadges(APIGatewayProxyRequestEvent request, String userId) {
        List<UserBadge> badges = badgeService.getUserBadges(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("badges", badges);
        response.put("count", badges.size());

        return ResponseGenerator.ok("Earned badges retrieved", response);
    }
}
