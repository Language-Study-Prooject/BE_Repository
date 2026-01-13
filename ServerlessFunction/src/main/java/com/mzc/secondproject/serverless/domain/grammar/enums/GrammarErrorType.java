package com.mzc.secondproject.serverless.domain.grammar.enums;

public enum GrammarErrorType {
	VERB_TENSE("동사 시제", "Verb Tense"),
	SUBJECT_VERB_AGREEMENT("주어-동사 일치", "Subject-Verb Agreement"),
	ARTICLE("관사", "Article"),
	PREPOSITION("전치사", "Preposition"),
	WORD_ORDER("어순", "Word Order"),
	PLURAL_SINGULAR("단/복수", "Plural/Singular"),
	PRONOUN("대명사", "Pronoun"),
	SPELLING("철자", "Spelling"),
	PUNCTUATION("구두점", "Punctuation"),
	WORD_CHOICE("어휘 선택", "Word Choice"),
	SENTENCE_STRUCTURE("문장 구조", "Sentence Structure"),
	OTHER("기타", "Other");

	private final String koreanName;
	private final String englishName;

	GrammarErrorType(String koreanName, String englishName) {
		this.koreanName = koreanName;
		this.englishName = englishName;
	}

	public String getKoreanName() {
		return koreanName;
	}

	public String getEnglishName() {
		return englishName;
	}
}
