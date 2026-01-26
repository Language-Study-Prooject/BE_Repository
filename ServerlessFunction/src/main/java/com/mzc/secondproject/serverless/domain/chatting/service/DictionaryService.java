package com.mzc.secondproject.serverless.domain.chatting.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 외부 사전 API 연동 서비스
 * Free Dictionary API (https://dictionaryapi.dev/) 사용
 */
public class DictionaryService {

	private static final Logger logger = LoggerFactory.getLogger(DictionaryService.class);
	private static final String API_BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/";
	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final HttpClient httpClient;
	private final Gson gson;

	// 간단한 인메모리 캐시 (Lambda 인스턴스 내에서만 유효)
	private final ConcurrentHashMap<String, DictionaryResult> cache;

	public DictionaryService() {
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(TIMEOUT)
				.build();
		this.gson = new Gson();
		this.cache = new ConcurrentHashMap<>();
	}

	/**
	 * 단어 검증 및 정의 조회
	 *
	 * @param word 검증할 단어
	 * @return 검증 결과 (유효 여부 + 정의)
	 */
	public DictionaryResult lookupWord(String word) {
		if (word == null || word.isBlank()) {
			return DictionaryResult.invalid("단어가 비어있습니다.");
		}

		String normalizedWord = word.trim().toLowerCase();

		// 캐시 확인
		if (cache.containsKey(normalizedWord)) {
			logger.debug("Cache hit for word: {}", normalizedWord);
			return cache.get(normalizedWord);
		}

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(API_BASE_URL + normalizedWord))
					.timeout(TIMEOUT)
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request,
					HttpResponse.BodyHandlers.ofString());

			DictionaryResult result = parseResponse(normalizedWord, response);

			// 캐시 저장
			cache.put(normalizedWord, result);

			return result;

		} catch (Exception e) {
			logger.error("Dictionary API error for word '{}': {}", normalizedWord, e.getMessage());
			// API 실패 시 일단 유효한 것으로 처리 (fallback)
			return DictionaryResult.validWithoutDefinition(normalizedWord);
		}
	}

	/**
	 * API 응답 파싱
	 */
	private DictionaryResult parseResponse(String word, HttpResponse<String> response) {
		if (response.statusCode() == 404) {
			return DictionaryResult.invalid("사전에 없는 단어입니다: " + word);
		}

		if (response.statusCode() != 200) {
			logger.warn("Unexpected API response: {} for word '{}'", response.statusCode(), word);
			return DictionaryResult.validWithoutDefinition(word);
		}

		try {
			JsonArray jsonArray = gson.fromJson(response.body(), JsonArray.class);
			if (jsonArray == null || jsonArray.isEmpty()) {
				return DictionaryResult.invalid("사전에 없는 단어입니다: " + word);
			}

			JsonObject firstEntry = jsonArray.get(0).getAsJsonObject();

			// 발음 추출 (있으면)
			String phonetic = extractPhonetic(firstEntry);

			// 첫 번째 정의 추출
			String definition = extractFirstDefinition(firstEntry);

			return DictionaryResult.valid(word, definition, phonetic);

		} catch (Exception e) {
			logger.error("Failed to parse dictionary response for '{}': {}", word, e.getMessage());
			return DictionaryResult.validWithoutDefinition(word);
		}
	}

	/**
	 * 발음 기호 추출
	 */
	private String extractPhonetic(JsonObject entry) {
		try {
			if (entry.has("phonetic")) {
				return entry.get("phonetic").getAsString();
			}
			if (entry.has("phonetics")) {
				JsonArray phonetics = entry.getAsJsonArray("phonetics");
				for (JsonElement p : phonetics) {
					JsonObject phoneticObj = p.getAsJsonObject();
					if (phoneticObj.has("text") && !phoneticObj.get("text").isJsonNull()) {
						String text = phoneticObj.get("text").getAsString();
						if (!text.isBlank()) {
							return text;
						}
					}
				}
			}
		} catch (Exception e) {
			logger.debug("Failed to extract phonetic: {}", e.getMessage());
		}
		return null;
	}

	/**
	 * 첫 번째 정의 추출
	 */
	private String extractFirstDefinition(JsonObject entry) {
		try {
			if (!entry.has("meanings")) {
				return null;
			}
			JsonArray meanings = entry.getAsJsonArray("meanings");
			if (meanings.isEmpty()) {
				return null;
			}

			JsonObject firstMeaning = meanings.get(0).getAsJsonObject();
			String partOfSpeech = firstMeaning.has("partOfSpeech")
					? firstMeaning.get("partOfSpeech").getAsString()
					: "";

			JsonArray definitions = firstMeaning.getAsJsonArray("definitions");
			if (definitions == null || definitions.isEmpty()) {
				return null;
			}

			String definition = definitions.get(0).getAsJsonObject()
					.get("definition").getAsString();

			return String.format("(%s) %s", partOfSpeech, definition);

		} catch (Exception e) {
			logger.debug("Failed to extract definition: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * 단어가 유효한지만 빠르게 확인 (정의 필요 없을 때)
	 */
	public boolean isValidWord(String word) {
		return lookupWord(word).isValid();
	}

	// ========== Result DTO ==========

	public record DictionaryResult(
			boolean valid,
			String word,
			String definition,
			String phonetic,
			String errorMessage
	) {
		public static DictionaryResult valid(String word, String definition, String phonetic) {
			return new DictionaryResult(true, word, definition, phonetic, null);
		}

		public static DictionaryResult validWithoutDefinition(String word) {
			return new DictionaryResult(true, word, null, null, null);
		}

		public static DictionaryResult invalid(String errorMessage) {
			return new DictionaryResult(false, null, null, null, errorMessage);
		}

		public boolean isValid() {
			return valid;
		}

		public Optional<String> getDefinition() {
			return Optional.ofNullable(definition);
		}

		public Optional<String> getPhonetic() {
			return Optional.ofNullable(phonetic);
		}
	}
}
