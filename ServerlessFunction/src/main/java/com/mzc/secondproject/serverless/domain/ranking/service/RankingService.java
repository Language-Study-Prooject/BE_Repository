package com.mzc.secondproject.serverless.domain.ranking.service;

import com.mzc.secondproject.serverless.domain.ranking.model.UserRanking;
import com.mzc.secondproject.serverless.domain.ranking.repository.RankingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class RankingService {

	private static final Logger logger = LoggerFactory.getLogger(RankingService.class);

	private final RankingRepository rankingRepository;

	public RankingService() {
		this.rankingRepository = new RankingRepository();
	}

	public List<UserRanking> getDailyRanking(int limit) {
		String period = LocalDate.now().toString();
		return rankingRepository.getTopRankings("DAILY", period, limit);
	}

	public List<UserRanking> getWeeklyRanking(int limit) {
		LocalDate today = LocalDate.now();
		String period = today.getYear() + "-W" + String.format("%02d", today.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()));
		return rankingRepository.getTopRankings("WEEKLY", period, limit);
	}

	public List<UserRanking> getMonthlyRanking(int limit) {
		LocalDate today = LocalDate.now();
		String period = today.getYear() + "-" + String.format("%02d", today.getMonthValue());
		return rankingRepository.getTopRankings("MONTHLY", period, limit);
	}

	public List<UserRanking> getTotalRanking(int limit) {
		return rankingRepository.getTopRankings("TOTAL", "TOTAL", limit);
	}

	public List<UserRanking> getRanking(String periodType, int limit) {
		return switch (periodType.toUpperCase()) {
			case "DAILY" -> getDailyRanking(limit);
			case "WEEKLY" -> getWeeklyRanking(limit);
			case "MONTHLY" -> getMonthlyRanking(limit);
			case "TOTAL" -> getTotalRanking(limit);
			default -> getDailyRanking(limit);
		};
	}

	public MyRankingResult getMyRanking(String userId) {
		LocalDate today = LocalDate.now();

		String dailyPeriod = today.toString();
		String weeklyPeriod = today.getYear() + "-W" + String.format("%02d", today.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()));
		String monthlyPeriod = today.getYear() + "-" + String.format("%02d", today.getMonthValue());

		Optional<UserRanking> dailyRanking = rankingRepository.getUserRanking("DAILY", dailyPeriod, userId);
		Optional<UserRanking> weeklyRanking = rankingRepository.getUserRanking("WEEKLY", weeklyPeriod, userId);
		Optional<UserRanking> monthlyRanking = rankingRepository.getUserRanking("MONTHLY", monthlyPeriod, userId);
		Optional<UserRanking> totalRanking = rankingRepository.getUserRanking("TOTAL", "TOTAL", userId);

		int dailyRank = dailyRanking.isPresent() ? rankingRepository.getUserRank("DAILY", dailyPeriod, userId) : -1;
		int weeklyRank = weeklyRanking.isPresent() ? rankingRepository.getUserRank("WEEKLY", weeklyPeriod, userId) : -1;
		int monthlyRank = monthlyRanking.isPresent() ? rankingRepository.getUserRank("MONTHLY", monthlyPeriod, userId) : -1;
		int totalRank = totalRanking.isPresent() ? rankingRepository.getUserRank("TOTAL", "TOTAL", userId) : -1;

		return new MyRankingResult(
				new PeriodRanking("DAILY", dailyRanking.map(UserRanking::getScore).orElse(0), dailyRank),
				new PeriodRanking("WEEKLY", weeklyRanking.map(UserRanking::getScore).orElse(0), weeklyRank),
				new PeriodRanking("MONTHLY", monthlyRanking.map(UserRanking::getScore).orElse(0), monthlyRank),
				new PeriodRanking("TOTAL", totalRanking.map(UserRanking::getScore).orElse(0), totalRank)
		);
	}

	public record MyRankingResult(
			PeriodRanking daily,
			PeriodRanking weekly,
			PeriodRanking monthly,
			PeriodRanking total
	) {}

	public record PeriodRanking(
			String periodType,
			int score,
			int rank
	) {}
}
