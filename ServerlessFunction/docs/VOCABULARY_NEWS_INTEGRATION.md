# 단어장 - 뉴스 연동 기능 프론트엔드 가이드

> 마지막 업데이트: 2025-01-23

## 목차
1. [뉴스 단어 수집 흐름](#1-뉴스-단어-수집-흐름)
2. [API 엔드포인트](#2-api-엔드포인트)
3. [카테고리 필터링](#3-카테고리-필터링)
4. [응답 예시](#4-응답-예시)
5. [프론트엔드 구현 가이드](#5-프론트엔드-구현-가이드)

---

## 1. 뉴스 단어 수집 흐름

### 자동 연동 프로세스

```
사용자가 뉴스 기사에서 "단어 가져오기" 클릭
              ↓
      POST /news/{articleId}/words
              ↓
┌─────────────────────────────────────────┐
│  1. 기사 키워드에서 한국어 뜻 추출      │
│  2. Word 테이블에 자동 저장 (NEWS 카테고리) │
│  3. UserWord에 자동 추가 (NEW 상태)     │
│  4. NewsWordCollect 기록 저장           │
└─────────────────────────────────────────┘
              ↓
   단어장(/user-words)에서 바로 확인 가능!
```

### 핵심 포인트
- **별도의 "연동" 버튼 불필요**: 단어 수집 시 자동으로 단어장에 추가됨
- **카테고리 자동 설정**: 뉴스에서 수집한 단어는 `NEWS` 카테고리로 저장
- **한국어 뜻 자동 포함**: 기사 AI 분석 결과에서 `meaningKo` 추출

---

## 2. API 엔드포인트

### 뉴스 단어 수집 API

#### 단어 수집 (단어 가져오기)
```http
POST /news/{articleId}/words
Authorization: Bearer {token}
Content-Type: application/json

{
  "word": "economy",
  "context": "The economy is growing rapidly"  // 선택사항
}
```

**응답:**
```json
{
  "success": true,
  "message": "단어 수집 성공",
  "data": {
    "wordCollect": {
      "word": "economy",
      "meaning": "경제",
      "articleId": "abc123",
      "articleTitle": "Global Economic Outlook",
      "collectedAt": "2025-01-23T12:00:00Z",
      "syncedToVocab": true,
      "vocabUserWordId": "economy"
    },
    "newBadges": []
  }
}
```

#### 뉴스에서 수집한 단어 목록
```http
GET /news/words?limit=20
Authorization: Bearer {token}
```

**응답:**
```json
{
  "success": true,
  "data": {
    "words": [
      {
        "word": "economy",
        "meaning": "경제",
        "articleId": "abc123",
        "articleTitle": "Global Economic Outlook",
        "context": "The economy is growing",
        "collectedAt": "2025-01-23T12:00:00Z",
        "syncedToVocab": true
      }
    ],
    "stats": {
      "totalCollected": 15,
      "syncedToVocab": 15
    },
    "count": 1
  }
}
```

---

### 단어장 API (카테고리 필터 추가됨)

#### 내 단어장 조회
```http
GET /user-words?category=NEWS&limit=20
Authorization: Bearer {token}
```

**쿼리 파라미터:**

| 파라미터 | 타입 | 설명 | 예시 |
|----------|------|------|------|
| `category` | string | 카테고리 필터 **(신규)** | `NEWS`, `DAILY`, `BUSINESS` |
| `status` | string | 학습 상태 필터 | `NEW`, `LEARNING`, `REVIEWING`, `MASTERED` |
| `bookmarked` | boolean | 북마크 필터 | `true` |
| `incorrectOnly` | boolean | 오답만 | `true` |
| `limit` | number | 조회 개수 (최대 50) | `20` |
| `cursor` | string | 페이지네이션 커서 | `eyJ...` |

---

## 3. 카테고리 필터링

### 사용 가능한 카테고리

| 카테고리 | 코드 | 설명 |
|----------|------|------|
| 일상 | `DAILY` | 일상 생활 단어 |
| 비즈니스 | `BUSINESS` | 비즈니스/업무 단어 |
| 학술 | `ACADEMIC` | 학술/전문 단어 |
| 여행 | `TRAVEL` | 여행 관련 단어 |
| 기술 | `TECHNOLOGY` | IT/기술 단어 |
| **뉴스** | `NEWS` | **뉴스에서 수집한 단어 (신규)** |

### 필터 조합 예시

```
# 뉴스에서 수집한 모든 단어
GET /user-words?category=NEWS

# 뉴스 단어 중 학습 중인 것만
GET /user-words?category=NEWS&status=LEARNING

# 뉴스 단어 중 북마크한 것만
GET /user-words?category=NEWS&bookmarked=true

# 뉴스 단어 중 틀린 것만
GET /user-words?category=NEWS&incorrectOnly=true

# 모든 카테고리의 북마크 단어
GET /user-words?bookmarked=true
```

---

## 4. 응답 예시

### 단어장 조회 응답 (GET /user-words?category=NEWS)

```json
{
  "success": true,
  "message": "User words retrieved",
  "data": {
    "userWords": [
      {
        "wordId": "economy",
        "userId": "user-123",
        "status": "NEW",
        "correctCount": 0,
        "incorrectCount": 0,
        "bookmarked": false,
        "favorite": false,
        "difficulty": null,
        "nextReviewAt": null,
        "lastReviewedAt": null,
        "repetitions": 0,
        "interval": 0,
        "english": "economy",
        "korean": "경제",
        "level": "INTERMEDIATE",
        "category": "NEWS",
        "example": "The economy is growing steadily.",
        "maleVoiceKey": null,
        "femaleVoiceKey": null
      },
      {
        "wordId": "regulation",
        "userId": "user-123",
        "status": "LEARNING",
        "correctCount": 2,
        "incorrectCount": 1,
        "bookmarked": true,
        "favorite": false,
        "difficulty": "HARD",
        "english": "regulation",
        "korean": "규제",
        "level": "ADVANCED",
        "category": "NEWS",
        "example": "New regulation will take effect."
      }
    ],
    "nextCursor": "eyJwayI6IlVTRVIjdXNlci0xMjMiLCJzayI6IldPUkQjcmVndWxhdGlvbiJ9",
    "hasMore": true
  }
}
```

---

## 5. 프론트엔드 구현 가이드

### 단어장 UI 변경사항

#### 1. 카테고리 탭/필터 추가
```
[전체] [일상] [비즈니스] [학술] [여행] [기술] [뉴스]
                                              ↑ 신규
```

#### 2. 뉴스 단어 표시
- 뉴스에서 수집한 단어는 `category: "NEWS"` 표시
- 출처 표시 가능 (NewsWordCollect의 articleTitle 활용)

#### 3. 단어 수집 후 UI 업데이트
```javascript
// 단어 수집 API 호출
const response = await fetch(`/news/${articleId}/words`, {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ word: selectedWord })
});

const result = await response.json();

if (result.success) {
  // syncedToVocab: true 이므로 단어장에 자동 추가됨
  showToast('단어가 단어장에 추가되었습니다!');

  // 새 배지 획득 시 알림
  if (result.data.newBadges?.length > 0) {
    showBadgeNotification(result.data.newBadges);
  }
}
```

### 체크리스트

#### 단어장 페이지
- [ ] 카테고리 필터 UI 추가 (탭 또는 드롭다운)
- [ ] `NEWS` 카테고리 옵션 추가
- [ ] API 호출 시 `category` 파라미터 전달
- [ ] 카테고리별 단어 개수 표시 (선택사항)

#### 뉴스 상세 페이지
- [ ] "단어 가져오기" 버튼 동작 확인
- [ ] 수집 성공 시 토스트 메시지
- [ ] 이미 수집된 단어 표시 (비활성화 또는 체크 아이콘)

#### 뉴스 키워드 표시
- [ ] `keywords` 배열의 `meaningKo` 필드 표시
- [ ] 각 키워드 클릭 시 수집 가능하도록 UI 구성

---

## 데이터 흐름 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                        뉴스 기사 상세                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Keywords:                                                │   │
│  │  [economy: 경제] [regulation: 규제] [summit: 정상회담]   │   │
│  │       ↓ 클릭                                             │   │
│  │  "단어 가져오기" → POST /news/{id}/words                 │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
                    자동으로 단어장에 추가
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                         단어장                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 카테고리: [전체] [일상] [비즈니스] ... [뉴스✓]           │   │
│  │                                                          │   │
│  │  economy     경제      NEW      뉴스                     │   │
│  │  regulation  규제      LEARNING 뉴스  ⭐                 │   │
│  │  summit      정상회담  NEW      뉴스                     │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 질문 및 문의

백엔드 관련 문의사항이 있으면 연락주세요.
