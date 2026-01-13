package com.mzc.secondproject.serverless.domain.vocabulary.state;

import com.mzc.secondproject.serverless.common.config.StudyConfig;

/**
 * Spaced Repetition 알고리즘에 필요한 컨텍스트.
 * State 객체가 상태 전이 및 간격 계산 시 참조한다.
 */
public class SpacedRepetitionContext {
	
	private int repetitions;
	private int interval;
	private double easeFactor;
	private int correctCount;
	private int incorrectCount;
	
	public SpacedRepetitionContext() {
		this.repetitions = StudyConfig.INITIAL_REPETITIONS;
		this.interval = StudyConfig.INITIAL_INTERVAL_DAYS;
		this.easeFactor = StudyConfig.DEFAULT_EASE_FACTOR;
		this.correctCount = StudyConfig.INITIAL_CORRECT_COUNT;
		this.incorrectCount = StudyConfig.INITIAL_INCORRECT_COUNT;
	}
	
	public SpacedRepetitionContext(int repetitions, int interval, double easeFactor,
	                               int correctCount, int incorrectCount) {
		this.repetitions = repetitions;
		this.interval = interval;
		this.easeFactor = easeFactor;
		this.correctCount = correctCount;
		this.incorrectCount = incorrectCount;
	}
	
	public void incrementRepetitions() {
		this.repetitions++;
	}
	
	public void resetRepetitions() {
		this.repetitions = StudyConfig.INITIAL_REPETITIONS;
	}
	
	public void incrementCorrectCount() {
		this.correctCount++;
	}
	
	public void incrementIncorrectCount() {
		this.incorrectCount++;
	}
	
	public void updateInterval(int newInterval) {
		this.interval = newInterval;
	}
	
	public void resetInterval() {
		this.interval = StudyConfig.INITIAL_INTERVAL_DAYS;
	}
	
	public void decreaseEaseFactor() {
		double newEaseFactor = this.easeFactor - 0.2;
		this.easeFactor = Math.max(StudyConfig.MIN_EASE_FACTOR, newEaseFactor);
	}
	
	public int calculateNextInterval() {
		return (int) Math.round(this.interval * this.easeFactor);
	}
	
	// Getters
	public int getRepetitions() {
		return repetitions;
	}
	
	public int getInterval() {
		return interval;
	}
	
	public double getEaseFactor() {
		return easeFactor;
	}
	
	public int getCorrectCount() {
		return correctCount;
	}
	
	public int getIncorrectCount() {
		return incorrectCount;
	}
}
