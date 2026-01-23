package com.mzc.secondproject.serverless.domain.news.service;

import com.mzc.secondproject.serverless.domain.news.config.NewsConfig;
import com.mzc.secondproject.serverless.domain.news.constants.NewsKey;
import com.mzc.secondproject.serverless.domain.news.model.NewsArticle;
import com.mzc.secondproject.serverless.domain.news.model.NewsQuizResult;
import com.mzc.secondproject.serverless.domain.news.model.QuizAnswerResult;
import com.mzc.secondproject.serverless.domain.news.model.QuizQuestion;
import com.mzc.secondproject.serverless.domain.news.repository.NewsArticleRepository;
import com.mzc.secondproject.serverless.domain.news.repository.NewsQuizRepository;
import com.mzc.secondproject.serverless.domain.notification.service.NotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * 뉴스 퀴즈 서비스
 */
public class NewsQuizService {
	
	private static final Logger logger = LoggerFactory.getLogger(NewsQuizService.class);

	private final NewsArticleRepository articleRepository;
	private final NewsQuizRepository quizRepository;
	private final NotificationPublisher notificationPublisher;

	public NewsQuizService() {
		this.articleRepository = new NewsArticleRepository();
		this.quizRepository = new NewsQuizRepository();
		this.notificationPublisher = NotificationPublisher.getInstance();
	}

	public NewsQuizService(NewsArticleRepository articleRepository, NewsQuizRepository quizRepository,
						   NotificationPublisher notificationPublisher) {
		this.articleRepository = articleRepository;
		this.quizRepository = quizRepository;
		this.notificationPublisher = notificationPublisher;
	}
	
	/**
	 * 퀴즈 조회
	 */
	public Optional<QuizData> getQuiz(String articleId, String userId) {
		Optional<NewsArticle> articleOpt = articleRepository.findById(articleId);
		if (articleOpt.isEmpty()) {
			logger.warn("기사를 찾을 수 없음: {}", articleId);
			return Optional.empty();
		}
		
		NewsArticle article = articleOpt.get();
		List<QuizQuestion> questions = article.getQuiz();
		
		if (questions == null || questions.isEmpty()) {
			logger.warn("퀴즈가 없는 기사: {}", articleId);
			return Optional.empty();
		}
		
		// 이미 제출했는지 확인
		boolean submitted = quizRepository.hasSubmitted(userId, articleId);
		
		// 정답 제거한 퀴즈 반환
		List<QuizQuestionView> questionViews = questions.stream()
				.map(q -> QuizQuestionView.builder()
						.questionId(q.getQuestionId())
						.type(q.getType())
						.question(q.getQuestion())
						.options(q.getOptions())
						.points(q.getPoints())
						.build())
				.toList();
		
		return Optional.of(QuizData.builder()
				.articleId(articleId)
				.articleTitle(article.getTitle())
				.level(article.getLevel())
				.questions(questionViews)
				.totalPoints(questions.stream().mapToInt(QuizQuestion::getPoints).sum())
				.submitted(submitted)
				.build());
	}
	
	/**
	 * 퀴즈 제출 및 채점
	 */
	public QuizSubmitResult submitQuiz(String userId, String articleId, List<QuizAnswer> answers, Integer timeTaken) {
		// 이미 제출했는지 확인
		if (quizRepository.hasSubmitted(userId, articleId)) {
			logger.warn("이미 제출한 퀴즈: userId={}, articleId={}", userId, articleId);
			return null;
		}
		
		// 기사 조회
		Optional<NewsArticle> articleOpt = articleRepository.findById(articleId);
		if (articleOpt.isEmpty()) {
			logger.warn("기사를 찾을 수 없음: {}", articleId);
			return null;
		}
		
		NewsArticle article = articleOpt.get();
		List<QuizQuestion> questions = article.getQuiz();
		
		if (questions == null || questions.isEmpty()) {
			logger.warn("퀴즈가 없는 기사: {}", articleId);
			return null;
		}
		
		// 정답 맵 생성
		Map<String, QuizQuestion> questionMap = new HashMap<>();
		for (QuizQuestion q : questions) {
			questionMap.put(q.getQuestionId(), q);
		}
		
		// 채점
		List<QuizAnswerResult> answerResults = new ArrayList<>();
		int earnedPoints = 0;
		int totalPoints = 0;
		
		for (QuizAnswer answer : answers) {
			QuizQuestion question = questionMap.get(answer.questionId());
			if (question == null) continue;
			
			boolean correct = question.getCorrectAnswer().equalsIgnoreCase(answer.answer());
			int points = correct ? question.getPoints() : 0;
			earnedPoints += points;
			totalPoints += question.getPoints();
			
			answerResults.add(QuizAnswerResult.builder()
					.questionId(answer.questionId())
					.type(question.getType())
					.userAnswer(answer.answer())
					.correctAnswer(question.getCorrectAnswer())
					.correct(correct)
					.points(points)
					.build());
		}
		
		// 점수 계산 (100점 만점)
		int score = totalPoints > 0 ? (earnedPoints * 100) / totalPoints : 0;
		
		// 결과 저장
		String now = Instant.now().toString();
		String today = LocalDate.now().toString();
		
		NewsQuizResult result = NewsQuizResult.builder()
				.pk(NewsKey.userNewsPk(userId))
				.sk(NewsKey.quizSk(articleId))
				.gsi1pk(NewsKey.userNewsStatPk(userId))
				.gsi1sk(today + "#QUIZ")
				.userId(userId)
				.articleId(articleId)
				.articleTitle(article.getTitle())
				.articleLevel(article.getLevel())
				.score(score)
				.totalPoints(totalPoints)
				.earnedPoints(earnedPoints)
				.answers(answerResults)
				.timeTaken(timeTaken)
				.submittedAt(now)
				.build();
		
		quizRepository.save(result);
		logger.info("퀴즈 제출 완료: userId={}, articleId={}, score={}", userId, articleId, score);

		// 알림 발행
		int correctCount = (int) answerResults.stream().filter(QuizAnswerResult::isCorrect).count();
		boolean isPerfect = score == 100;
		notificationPublisher.publishNewsQuizComplete(
				userId,
				articleId,
				article.getTitle(),
				score,
				correctCount,
				answerResults.size(),
				isPerfect
		);

		// 피드백 생성
		String feedback = generateFeedback(score);

		return QuizSubmitResult.builder()
				.score(score)
				.earnedPoints(earnedPoints)
				.totalPoints(totalPoints)
				.results(answerResults)
				.feedback(feedback)
				.build();
	}
	
	/**
	 * 사용자 퀴즈 결과 조회
	 */
	public Optional<NewsQuizResult> getQuizResult(String userId, String articleId) {
		return quizRepository.findByUserAndArticle(userId, articleId);
	}
	
	/**
	 * 사용자 퀴즈 기록 목록 조회
	 */
	public List<NewsQuizResult> getUserQuizHistory(String userId, int limit) {
		return quizRepository.getUserQuizResults(userId, limit);
	}
	
	/**
	 * 사용자 퀴즈 통계 조회
	 */
	public Map<String, Object> getUserQuizStats(String userId) {
		NewsQuizRepository.QuizStats stats = quizRepository.getUserQuizStats(userId);
		return Map.of(
				"totalQuizzes", stats.totalQuizzes(),
				"avgScore", stats.avgScore(),
				"perfectScores", stats.perfectScores()
		);
	}
	
	/**
	 * 피드백 생성
	 */
	private String generateFeedback(int score) {
		return NewsConfig.getFeedbackByScore(score);
	}
	
	/**
	 * 퀴즈 데이터 (정답 제외)
	 */
	@lombok.Data
	@lombok.Builder
	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class QuizData {
		private String articleId;
		private String articleTitle;
		private String level;
		private List<QuizQuestionView> questions;
		private int totalPoints;
		private boolean submitted;
	}
	
	/**
	 * 퀴즈 문제 뷰 (정답 제외)
	 */
	@lombok.Data
	@lombok.Builder
	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class QuizQuestionView {
		private String questionId;
		private String type;
		private String question;
		private List<String> options;
		private int points;
	}
	
	/**
	 * 사용자 답변
	 */
	public record QuizAnswer(String questionId, String answer) {
	}
	
	/**
	 * 퀴즈 제출 결과
	 */
	@lombok.Data
	@lombok.Builder
	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class QuizSubmitResult {
		private int score;
		private int earnedPoints;
		private int totalPoints;
		private List<QuizAnswerResult> results;
		private String feedback;
	}
}
