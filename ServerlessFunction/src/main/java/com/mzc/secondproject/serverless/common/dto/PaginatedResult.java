package com.mzc.secondproject.serverless.common.dto;

import java.util.List;

/**
 * 페이지네이션 결과를 담는 제네릭 클래스
 * 모든 Repository에서 공통으로 사용
 *
 * @param <T> 결과 아이템 타입
 */
public class PaginatedResult<T> {

    private final List<T> items;
    private final String nextCursor;

    public PaginatedResult(List<T> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }

    public List<T> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean hasMore() {
        return nextCursor != null;
    }
}
