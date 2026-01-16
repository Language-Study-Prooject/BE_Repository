package com.mzc.secondproject.serverless.domain.ranking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.ranking.model.UserRanking;
import com.mzc.secondproject.serverless.domain.ranking.service.RankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class RankingHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final Logger logger = LoggerFactory.getLogger(RankingHandler.class);

	private final RankingService rankingService;
	private final HandlerRouter router;

	public RankingHandler() {
		this.rankingService = new RankingService();
		this.router = initRouter();
	}

	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.getAuth("/ranking/daily", this::getDailyRanking),
				Route.getAuth("/ranking/weekly", this::getWeeklyRanking),
				Route.getAuth("/ranking/monthly", this::getMonthlyRanking),
				Route.getAuth("/ranking/total", this::getTotalRanking),
				Route.getAuth("/ranking/me", this::getMyRanking),
				Route.getAuth("/ranking/{period}", this::getRankingByPeriod)
		);
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}

	private APIGatewayProxyResponseEvent getDailyRanking(APIGatewayProxyRequestEvent request, String userId) {
		int limit = parseLimit(request);
		List<UserRanking> rankings = rankingService.getDailyRanking(limit);
		return buildRankingResponse("DAILY", rankings);
	}

	private APIGatewayProxyResponseEvent getWeeklyRanking(APIGatewayProxyRequestEvent request, String userId) {
		int limit = parseLimit(request);
		List<UserRanking> rankings = rankingService.getWeeklyRanking(limit);
		return buildRankingResponse("WEEKLY", rankings);
	}

	private APIGatewayProxyResponseEvent getMonthlyRanking(APIGatewayProxyRequestEvent request, String userId) {
		int limit = parseLimit(request);
		List<UserRanking> rankings = rankingService.getMonthlyRanking(limit);
		return buildRankingResponse("MONTHLY", rankings);
	}

	private APIGatewayProxyResponseEvent getTotalRanking(APIGatewayProxyRequestEvent request, String userId) {
		int limit = parseLimit(request);
		List<UserRanking> rankings = rankingService.getTotalRanking(limit);
		return buildRankingResponse("TOTAL", rankings);
	}

	private APIGatewayProxyResponseEvent getRankingByPeriod(APIGatewayProxyRequestEvent request, String userId) {
		String period = request.getPathParameters().get("period");
		int limit = parseLimit(request);
		List<UserRanking> rankings = rankingService.getRanking(period, limit);
		return buildRankingResponse(period.toUpperCase(), rankings);
	}

	private APIGatewayProxyResponseEvent getMyRanking(APIGatewayProxyRequestEvent request, String userId) {
		RankingService.MyRankingResult result = rankingService.getMyRanking(userId);

		Map<String, Object> response = new HashMap<>();
		response.put("userId", userId);
		response.put("daily", Map.of("score", result.daily().score(), "rank", result.daily().rank()));
		response.put("weekly", Map.of("score", result.weekly().score(), "rank", result.weekly().rank()));
		response.put("monthly", Map.of("score", result.monthly().score(), "rank", result.monthly().rank()));
		response.put("total", Map.of("score", result.total().score(), "rank", result.total().rank()));

		return ResponseGenerator.ok("My ranking retrieved", response);
	}

	private APIGatewayProxyResponseEvent buildRankingResponse(String periodType, List<UserRanking> rankings) {
		List<Map<String, Object>> rankingList = IntStream.range(0, rankings.size())
				.mapToObj(i -> {
					UserRanking r = rankings.get(i);
					Map<String, Object> entry = new HashMap<>();
					entry.put("rank", i + 1);
					entry.put("userId", r.getUserId());
					entry.put("score", r.getScore());
					entry.put("nickname", r.getNickname());
					entry.put("profileUrl", r.getProfileUrl());
					return entry;
				})
				.toList();

		Map<String, Object> response = new HashMap<>();
		response.put("periodType", periodType);
		response.put("rankings", rankingList);
		response.put("totalCount", rankingList.size());

		return ResponseGenerator.ok("Ranking retrieved", response);
	}

	private int parseLimit(APIGatewayProxyRequestEvent request) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		if (queryParams != null && queryParams.get("limit") != null) {
			try {
				return Math.min(Integer.parseInt(queryParams.get("limit")), 100);
			} catch (NumberFormatException e) {
				return 50;
			}
		}
		return 50;
	}
}
