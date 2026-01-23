# News API 트러블슈팅 가이드

## 개요
2026-01-23 뉴스 기능 프론트엔드 연동 과정에서 발생한 이슈들과 해결 방법을 정리합니다.

---

## 1. GET /news/{articleId} 응답이 기사가 아닌 읽기 기록 반환

### 증상
```javascript
// 예상 응답
{ articleId: "e644d491", title: "...", summary: "...", ... }

// 실제 응답
{ pk: "USER#64983d3c-...#NEWS", sk: "READ#e644d491", articleId: "e644d491" }
```

### 원인
`NewsArticleRepository.findById()`가 테이블 전체를 스캔하면서 `articleId`만 필터링했습니다.
뉴스 테이블에는 기사(`ARTICLE#`)와 사용자 기록(`READ#`, `BOOKMARK#`)이 함께 저장되어 있어서,
`UserNewsRecord`가 먼저 매칭되어 반환되었습니다.

### 해결
`findById`에서 SK가 `ARTICLE#`로 시작하는 것만 필터링하도록 수정:

```java
// Before
Expression filterExpression = Expression.builder()
    .expression("articleId = :articleId")
    .putExpressionValue(":articleId", AttributeValue.builder().s(articleId).build())
    .build();

// After
Expression filterExpression = Expression.builder()
    .expression("articleId = :articleId AND begins_with(SK, :skPrefix)")
    .putExpressionValue(":articleId", AttributeValue.builder().s(articleId).build())
    .putExpressionValue(":skPrefix", AttributeValue.builder().s("ARTICLE#").build())
    .build();
```

### 파일
- `NewsArticleRepository.java` - `findById()` 메서드

---

## 2. 기사에 category 필드 누락

### 증상
```json
{
  "articleId": "2b4e42f9",
  "title": "...",
  "category": null  // 누락
}
```

### 원인
`NewsAnalysisService`에서 Bedrock AI 분석 시 category 분류 로직이 없었습니다.

### 해결
1. Bedrock 프롬프트에 category 분류 요청 추가
2. `AnalysisResult` 레코드에 category 필드 추가
3. 파싱 및 저장 로직 추가

```java
// 프롬프트에 추가
"category": "WORLD",
...
For category, choose EXACTLY ONE from: WORLD, POLITICS, BUSINESS, TECH, SCIENCE, HEALTH, SPORTS, ENTERTAINMENT, LIFESTYLE
```

### 파일
- `NewsAnalysisService.java` - `generateSummaryAndQuiz()`, `parseAnalysisResult()`, `AnalysisResult` 레코드

### 주의
기존 기사에는 category가 없으므로, **기사 삭제 후 재수집** 필요

---

## 3. /stats/dashboard CORS 에러

### 증상
```
Access to fetch at '.../stats/dashboard' has been blocked by CORS policy:
Response to preflight request doesn't pass access control check
```

### 원인
새로 추가한 `/stats/dashboard` 엔드포인트가 `template.yaml`에 정의되지 않았습니다.

### 해결
`template.yaml`의 `UserStatsFunction` Events에 엔드포인트 추가:

```yaml
Events:
  GetDashboardStats:
    Type: Api
    Properties:
      RestApiId: !Ref MainApi
      Path: /stats/dashboard
      Method: GET
      Auth:
        Authorizer: CognitoAuthorizer
```

### 파일
- `template.yaml` - UserStatsFunction Events

---

## 4. 북마크 API가 기사 정보 없이 반환

### 증상
```json
// GET /news/bookmarks 응답
{
  "bookmarks": [
    { "pk": "USER#...", "sk": "BOOKMARK#...", "articleId": "..." }
  ]
}
```

### 원인
`NewsLearningService.getUserBookmarks()`가 북마크 레코드만 반환하고 기사 정보를 조회하지 않았습니다.

### 해결
북마크 레코드에서 articleId로 기사 정보를 조회하여 함께 반환:

```java
public List<Map<String, Object>> getUserBookmarks(String userId, int limit) {
    List<UserNewsRecord> bookmarks = userNewsRepository.getUserBookmarks(userId, limit);
    List<Map<String, Object>> result = new ArrayList<>();

    for (UserNewsRecord bookmark : bookmarks) {
        Optional<NewsArticle> articleOpt = articleRepository.findById(bookmark.getArticleId());
        if (articleOpt.isPresent()) {
            NewsArticle article = articleOpt.get();
            Map<String, Object> bookmarkWithArticle = new HashMap<>();
            bookmarkWithArticle.put("articleId", article.getArticleId());
            bookmarkWithArticle.put("title", article.getTitle());
            bookmarkWithArticle.put("summary", article.getSummary());
            // ... 기타 필드
            result.add(bookmarkWithArticle);
        }
    }
    return result;
}
```

### 파일
- `NewsLearningService.java` - `getUserBookmarks()`
- `NewsHandler.java` - `getBookmarks()`

---

## 5. POST /news/{articleId}/words 500 에러

### 증상
```
java.lang.NullPointerException: Cannot invoke "JsonElement.getAsString()"
because the return value of "JsonObject.get(String)" is null
at NewsHandler.collectWord(NewsHandler.java:416)
```

### 원인
요청 body에 `word` 필드가 없거나 null일 때 검증 없이 바로 접근했습니다.

### 해결
null 체크 추가 및 `INVALID_REQUEST` 에러 코드 정의:

```java
JsonObject body = gson.fromJson(request.getBody(), JsonObject.class);
if (body == null || !body.has("word") || body.get("word").isJsonNull()) {
    return ResponseGenerator.fail(NewsErrorCode.INVALID_REQUEST);
}
```

### 파일
- `NewsHandler.java` - `collectWord()`
- `NewsErrorCode.java` - `INVALID_REQUEST` 추가

---

## 6. DAILY 통계에 뉴스 관련 필드 누락

### 증상
- TOTAL 통계: `newsRead: 5` ✅
- DAILY 통계: `newsRead` 필드 없음 ❌

### 원인
`incrementNewsReadStats()` 등의 메서드가 TOTAL 통계만 업데이트하고 DAILY 통계는 업데이트하지 않았습니다.

### 해결
각 뉴스 통계 업데이트 메서드에서 DAILY 통계도 함께 업데이트:

```java
// TOTAL 업데이트 후 DAILY도 업데이트
Map<String, AttributeValue> dailyKey = new HashMap<>();
dailyKey.put("PK", AttributeValue.builder().s(pk).build());
dailyKey.put("SK", AttributeValue.builder().s(StatsKey.statsDailySk(today)).build());
// ... DAILY 업데이트 로직
```

### 파일
- `UserStatsRepository.java` - `incrementNewsReadStats()`, `incrementNewsQuizStats()`, `incrementNewsWordStats()`

---

## 체크리스트

새로운 API 엔드포인트 추가 시:
- [ ] Handler에 라우트 추가
- [ ] `template.yaml`에 Events 추가
- [ ] CORS 설정 확인

DynamoDB 단일 테이블 설계 주의:
- [ ] 쿼리 시 PK/SK 패턴 명확히 구분
- [ ] Scan 사용 시 적절한 필터 표현식 사용

통계 업데이트 시:
- [ ] TOTAL과 DAILY 모두 업데이트

API 요청 처리 시:
- [ ] 요청 body null 체크
- [ ] 필수 필드 존재 여부 검증
