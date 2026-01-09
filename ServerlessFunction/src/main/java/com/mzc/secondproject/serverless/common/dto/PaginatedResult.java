package com.mzc.secondproject.serverless.common.dto;

import java.util.List;

/**
 * 페이지네이션 결과를 담는 제네릭 레코드
 *
 * @param items 결과 아이템 목록
 * @param nextCursor 다음 페이지 커서 (없으면 null)
 */
public record PaginatedResult<T>(
        List<T> items,
        String nextCursor
) {

    public boolean hasMore() {
        return nextCursor != null;
    }
}
