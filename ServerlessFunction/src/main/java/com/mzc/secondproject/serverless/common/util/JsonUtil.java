package com.mzc.secondproject.serverless.common.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 파싱 관련 공통 유틸리티
 */
public class JsonUtil {
	
	private JsonUtil() {
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
