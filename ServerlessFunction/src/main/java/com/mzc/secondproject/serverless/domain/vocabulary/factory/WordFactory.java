package com.mzc.secondproject.serverless.domain.vocabulary.factory;

import com.mzc.secondproject.serverless.common.constants.DynamoDbKey;
import com.mzc.secondproject.serverless.domain.vocabulary.constants.VocabKey;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;

import java.time.Instant;
import java.util.UUID;

/**
 * Word 엔티티 생성 Factory
 * 객체 생성 로직을 중앙 집중화하여 일관성 유지
 */
public class WordFactory {

	private static final String DEFAULT_LEVEL = "BEGINNER";
	private static final String DEFAULT_CATEGORY = "DAILY";

	/**
	 * 새 Word 엔티티 생성
	 */
	public Word create(String english, String korean, String example, String level, String category) {
		String wordId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		String resolvedLevel = level != null ? level : DEFAULT_LEVEL;
		String resolvedCategory = category != null ? category : DEFAULT_CATEGORY;

		return Word.builder()
				.pk(VocabKey.wordPk(wordId))
				.sk(DynamoDbKey.METADATA)
				.gsi1pk(VocabKey.levelPk(resolvedLevel))
				.gsi1sk(VocabKey.wordSk(wordId))
				.gsi2pk(VocabKey.categoryPk(resolvedCategory))
				.gsi2sk(VocabKey.wordSk(wordId))
				.wordId(wordId)
				.english(english)
				.korean(korean)
				.example(example)
				.level(resolvedLevel)
				.category(resolvedCategory)
				.createdAt(now)
				.build();
	}

	/**
	 * 기본값으로 Word 생성
	 */
	public Word create(String english, String korean, String example) {
		return create(english, korean, example, DEFAULT_LEVEL, DEFAULT_CATEGORY);
	}

	/**
	 * Word 엔티티 업데이트 (GSI 키 자동 갱신)
	 */
	public Word updateFields(Word word, String english, String korean, String example, String level, String category) {
		if (english != null) {
			word.setEnglish(english);
		}
		if (korean != null) {
			word.setKorean(korean);
		}
		if (example != null) {
			word.setExample(example);
		}
		if (level != null) {
			word.setLevel(level);
			word.setGsi1pk(VocabKey.levelPk(level));
		}
		if (category != null) {
			word.setCategory(category);
			word.setGsi2pk(VocabKey.categoryPk(category));
		}
		return word;
	}
}
