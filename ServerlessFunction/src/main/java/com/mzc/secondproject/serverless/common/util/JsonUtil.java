package com.mzc.secondproject.serverless.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 파싱 관련 공통 유틸리티
 */
public class JsonUtil {

	private static final Gson GSON = new GsonBuilder().create();

	private JsonUtil() {
	}

	/**
	 * 객체를 JSON 문자열로 변환
	 */
	public static String toJson(Object obj) {
		return GSON.toJson(obj);
	}

	/**
	 * JSON 문자열을 객체로 변환
	 */
	public static <T> T fromJson(String json, Class<T> clazz) {
		return GSON.fromJson(json, clazz);
	}
	
	// 응답에서 JSON 부분만 추출
	public static String extractJson(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		int start = response.indexOf('{');
		int end = response.lastIndexOf('}');
		if (start != -1 && end != -1 && end > start) {
			return response.substring(start, end + 1);
		}
		return response;
	}
	
	// JsonArray → List<String> 변환
	public static List<String> toStringList(JsonArray array) {
		List<String> result = new ArrayList<>();
		if (array != null) {
			for (JsonElement el : array) {
				result.add(el.getAsString());
			}
		}
		return result;
	}
}
