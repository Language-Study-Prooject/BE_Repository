# Vocabulary Domain 세부 보고서

## 1. 개요

Vocabulary 도메인은 AWS Lambda와 DynamoDB를 기반으로 한 영어 단어 학습 시스템입니다. SM-2 Spaced Repetition 알고리즘과 CQRS 패턴을 적용하여 과학적이고 효율적인 단어
암기를 지원합니다.

---

## 2. 전체 아키텍처

```mermaid
flowchart TB
    subgraph Client["클라이언트"]
        APP[Mobile/Web App]
    end

    subgraph Gateway["API Gateway"]
        REST[REST API<br/>HTTP]
    end

    subgraph Lambda["Lambda Handlers"]
        direction TB
        WORD[WordHandler]
        USERWORD[UserWordHandler]
        DAILY[DailyStudyHandler]
        TEST[TestHandler]
        GROUP[WordGroupHandler]
        VOICE[VoiceHandler]
        STATS[StatisticsHandler<br/>SQS Consumer]
    end

    subgraph Services["서비스 레이어 (CQRS)"]
        direction TB
        CMD[Command Services<br/>쓰기 작업]
        QUERY[Query Services<br/>읽기 작업]
    end

    subgraph External["외부 서비스"]
        POLLY[AWS Polly<br/>TTS]
        SNS[AWS SNS]
        SQS[AWS SQS]
        S3[(S3<br/>음성 캐시)]
    end

    subgraph Storage["데이터 저장소"]
        DDB[(DynamoDB)]
    end

    APP --> REST
    REST --> WORD & USERWORD & DAILY & TEST & GROUP & VOICE
    WORD & USERWORD & DAILY & TEST & GROUP --> CMD & QUERY
    CMD & QUERY --> DDB
    VOICE --> POLLY --> S3
    TEST --> SNS --> SQS --> STATS
    STATS --> DDB
```

---

## 3. 일일 학습 시스템

### 3.1 일일 학습 흐름

```mermaid
flowchart TB
    subgraph DailyStudyFlow["일일 학습 흐름"]
        START[GET /vocab/daily] --> CHECK{기존 학습<br/>존재?}
        CHECK -->|Yes| RETURN[기존 학습 반환]
        CHECK -->|No| CREATE[새 학습 생성]
        CREATE --> REVIEW["복습 단어 5개 선정<br/>(nextReviewAt <= today)"]
        REVIEW --> NEW["신규 단어 50개 선정<br/>(미학습 + 해당 레벨)"]
        NEW --> SAVE[DailyStudy 저장]
        SAVE --> RETURN
        RETURN --> LEARN[학습 진행]
        LEARN --> MARK["POST .../learned<br/>단어별 학습 완료"]
        MARK --> PROGRESS{50개 완료?}
        PROGRESS -->|No| LEARN
        PROGRESS -->|Yes| COMPLETE["isCompleted = true<br/>배지 체크"]
    end
```

### 3.2 Daily Study API

| Method | Endpoint                            | 설명              |
|--------|-------------------------------------|-----------------|
| GET    | /vocab/daily                        | 오늘의 학습 단어 조회/생성 |
| POST   | /vocab/daily/words/{wordId}/learned | 단어 학습 완료 처리     |

### 3.3 응답 예시

```json
{
  "userId": "user123",
  "date": "2026-01-16",
  "newWordIds": [
    "word1",
    "word2",
    ...
  ],
  "reviewWordIds": [
    "word51",
    "word52",
    ...
  ],
  "learnedWordIds": [],
  "totalWords": 55,
  "learnedCount": 0,
  "isCompleted": false,
  "progress": {
    "percentage": 0,
    "learned": 0,
    "total": 55
  }
}
```

---

## 4. SM-2 Spaced Repetition 알고리즘

### 4.1 학습 상태 전이

```mermaid
stateDiagram-v2
    [*] --> NEW: 단어 추가
    NEW --> LEARNING: 첫 학습
    LEARNING --> LEARNING: 오답
    LEARNING --> REVIEWING: 2회 연속 정답
    REVIEWING --> LEARNING: 오답
    REVIEWING --> MASTERED: 5회 연속 정답
    MASTERED --> LEARNING: 오답
    MASTERED --> MASTERED: 정답 유지
```

### 4.2 상태별 로직

| 상태            | 조건          | 정답 시                        | 오답 시                      |
|---------------|-------------|-----------------------------|---------------------------|
| **NEW**       | 신규 단어       | LEARNING, rep=1, interval=1 | LEARNING, easeFactor-=0.2 |
| **LEARNING**  | rep < 2     | rep++, interval 계산          | rep=0, interval=1         |
| **REVIEWING** | 2 ≤ rep < 5 | rep++, interval 증가          | rep=0, LEARNING           |
| **MASTERED**  | rep ≥ 5     | interval 증가, 유지             | rep=0, REVIEWING          |

### 4.3 복습 간격 계산

```mermaid
flowchart LR
    REP1["rep = 1<br/>interval = 1일"]
    REP2["rep = 2<br/>interval = 6일"]
    REP3["rep >= 3<br/>interval = interval × easeFactor"]
    REP1 --> REP2 --> REP3
```

**핵심 변수:**

- `repetitions`: 연속 정답 횟수 (0~∞)
- `interval`: 복습 간격 (일 단위)
- `easeFactor`: 난이도 계수 (1.3~2.5, 기본 2.5)
- `nextReviewAt`: 다음 복습 예정일

---

## 5. 테스트 시스템

### 5.1 테스트 흐름

```mermaid
sequenceDiagram
    participant Client
    participant Handler as TestHandler
    participant Service as TestCommandService
    participant DB as DynamoDB
    participant SNS as AWS SNS
    Client ->> Handler: POST /vocab/tests/start
    Handler ->> Service: startTest(userId, testType)
    Service ->> DB: 오늘의 학습 단어 조회
    Service ->> Service: 4지선다 문제 생성
    Service -->> Client: 문제 목록 반환
    Note over Client: 사용자 답변 입력
    Client ->> Handler: POST /vocab/tests/submit
    Handler ->> Service: submitTest(answers)
    Service ->> DB: 결과 저장
    Service ->> SNS: 결과 발행 (비동기)
    Service -->> Client: 테스트 결과
    Note over SNS, DB: 비동기 통계 처리
    SNS ->> DB: 통계 업데이트
```

### 5.2 문제 생성 알고리즘

```mermaid
flowchart TB
    START[문제 생성 시작] --> WORDS[일일 학습 단어 로드]
    WORDS --> GROUP[레벨별 그룹화]
    GROUP --> LOOP[각 단어마다]
    LOOP --> CORRECT["정답 = 해당 단어의<br/>한국어 뜻"]
    CORRECT --> DIST["오답 3개 선정<br/>(동일 레벨 단어)"]
    DIST --> SHUFFLE[4개 보기 셔플]
    SHUFFLE --> NEXT{다음 단어?}
    NEXT -->|Yes| LOOP
    NEXT -->|No| RETURN[문제 목록 반환]
```

### 5.3 Test API

| Method | Endpoint                      | 설명         |
|--------|-------------------------------|------------|
| POST   | /vocab/tests/start            | 테스트 시작     |
| POST   | /vocab/tests/submit           | 테스트 제출     |
| GET    | /vocab/tests/results          | 테스트 결과 목록  |
| GET    | /vocab/tests/results/{testId} | 테스트 상세 결과  |
| GET    | /vocab/tests/tested-words     | 최근 테스트된 단어 |

---

## 6. 단어 관리 시스템

### 6.1 Word API

| Method | Endpoint               | 설명                         |
|--------|------------------------|----------------------------|
| GET    | /vocab/words           | 단어 목록 (level, category 필터) |
| POST   | /vocab/words           | 단어 등록                      |
| GET    | /vocab/words/{wordId}  | 단어 상세                      |
| PUT    | /vocab/words/{wordId}  | 단어 수정                      |
| DELETE | /vocab/words/{wordId}  | 단어 삭제                      |
| GET    | /vocab/words/search    | 키워드 검색                     |
| POST   | /vocab/words/batch     | 배치 등록 (최대 100개)            |
| POST   | /vocab/words/batch/get | 배치 조회                      |

### 6.2 User Word API

| Method | Endpoint                          | 설명          |
|--------|-----------------------------------|-------------|
| GET    | /vocab/user-words                 | 사용자 단어 목록   |
| GET    | /vocab/user-words/{wordId}        | 사용자 단어 상세   |
| PUT    | /vocab/user-words/{wordId}        | 정답/오답 기록    |
| PATCH  | /vocab/user-words/{wordId}/tag    | 북마크, 난이도 설정 |
| PATCH  | /vocab/user-words/{wordId}/status | 상태 수동 변경    |
| GET    | /vocab/wrong-answers              | 오답 단어 목록    |

### 6.3 Word Group API

| Method | Endpoint                               | 설명     |
|--------|----------------------------------------|--------|
| POST   | /vocab/groups                          | 단어장 생성 |
| GET    | /vocab/groups                          | 단어장 목록 |
| GET    | /vocab/groups/{groupId}                | 단어장 상세 |
| PUT    | /vocab/groups/{groupId}                | 단어장 수정 |
| DELETE | /vocab/groups/{groupId}                | 단어장 삭제 |
| POST   | /vocab/groups/{groupId}/words/{wordId} | 단어 추가  |
| DELETE | /vocab/groups/{groupId}/words/{wordId} | 단어 제거  |

---

## 7. TTS 음성 합성

### 7.1 음성 생성 흐름

```mermaid
flowchart TB
    REQUEST["POST /vocab/synthesize<br/>{wordId, voice, type}"]
    CHECK{S3 캐시<br/>존재?}
    REQUEST --> CHECK
    CHECK -->|Yes| PRESIGN[Presigned URL 생성]
    CHECK -->|No| POLLY[AWS Polly 호출]
    POLLY --> SAVE[S3 저장]
    SAVE --> PRESIGN
    PRESIGN --> RESPONSE[URL 반환]
```

### 7.2 Voice API

```json
// Request
{
  "wordId": "uuid",
  "voice": "MALE",
  // MALE | FEMALE
  "type": "WORD"
  // WORD | EXAMPLE
}

// Response
{
  "url": "https://s3...presigned-url",
  "expiresIn": 3600
}
```

---

## 8. 데이터 모델

### 8.1 Word

```java

@DynamoDbBean
public class Word {
	String wordId;            // UUID
	String english;           // 영어 단어
	String korean;            // 한국어 뜻
	String example;           // 예문
	String level;             // BEGINNER | INTERMEDIATE | ADVANCED
	String category;          // DAILY | BUSINESS | ACADEMIC | TRAVEL | TECHNOLOGY
	String maleVoiceKey;      // S3 음성 키
	String femaleVoiceKey;
	String maleExampleVoiceKey;
	String femaleExampleVoiceKey;
}
```

**DynamoDB Keys:**

| Key    | 패턴                  | 용도       |
|--------|---------------------|----------|
| PK     | WORD#{wordId}       | 기본 조회    |
| SK     | METADATA            | -        |
| GSI1PK | LEVEL#{level}       | 레벨별 조회   |
| GSI2PK | CATEGORY#{category} | 카테고리별 조회 |

### 8.2 UserWord

```java

@DynamoDbBean
public class UserWord {
	String userId;
	String wordId;
	String status;            // NEW | LEARNING | REVIEWING | MASTERED
	
	// SM-2 알고리즘 필드
	Integer interval;         // 복습 간격 (일)
	Double easeFactor;        // 난이도 계수 (1.3~2.5)
	Integer repetitions;      // 연속 정답 횟수
	String nextReviewAt;      // 다음 복습일 (YYYY-MM-DD)
	
	// 통계
	Integer correctCount;     // 누적 정답
	Integer incorrectCount;   // 누적 오답
	
	// 사용자 설정
	Boolean bookmarked;       // 북마크
	Boolean favorite;         // 즐겨찾기
	String difficulty;        // EASY | NORMAL | HARD
}
```

**DynamoDB Keys:**

| Key    | 패턴                       | 용도           |
|--------|--------------------------|--------------|
| PK     | USER#{userId}            | 기본 조회        |
| SK     | WORD#{wordId}            | -            |
| GSI1PK | USER#{userId}#REVIEW     | 복습 예정 단어     |
| GSI1SK | DATE#{nextReviewAt}      | -            |
| GSI2PK | USER#{userId}#STATUS     | 상태별 조회       |
| GSI2SK | STATUS#{status}          | -            |
| GSI3PK | USER#{userId}#BOOKMARKED | 북마크 (Sparse) |

### 8.3 DailyStudy

```java

@DynamoDbBean
public class DailyStudy {
	String userId;
	String date;              // YYYY-MM-DD
	List<String> newWordIds;      // 신규 단어 50개
	List<String> reviewWordIds;   // 복습 단어 5개
	List<String> learnedWordIds;  // 학습 완료 단어
	Integer totalWords;       // 총 단어 수 (55)
	Integer learnedCount;     // 학습 완료 수
	Boolean isCompleted;      // 완료 여부
}
```

### 8.4 TestResult

```java

@DynamoDbBean
public class TestResult {
	String testId;
	String userId;
	String testType;          // DAILY | WEEKLY | CUSTOM
	Integer totalQuestions;
	Integer correctAnswers;
	Integer incorrectAnswers;
	Double successRate;
	List<String> testedWordIds;
	List<String> incorrectWordIds;
	String startedAt;
	String completedAt;
}
```

---

## 9. 서비스 아키텍처 (CQRS)

### 9.1 Command Services (쓰기)

```mermaid
flowchart TB
    subgraph Commands["Command Services"]
        WC[WordCommandService<br/>단어 생성/수정/삭제]
        UC[UserWordCommandService<br/>학습 상태 업데이트]
        DC[DailyStudyCommandService<br/>일일 학습 관리]
        TC[TestCommandService<br/>테스트 생성/제출]
        GC[WordGroupCommandService<br/>단어장 관리]
    end
```

### 9.2 Query Services (읽기)

```mermaid
flowchart TB
    subgraph Queries["Query Services"]
        WQ[WordQueryService<br/>단어 조회/검색]
        UQ[UserWordQueryService<br/>학습 현황 조회]
        DQ[DailyStudyQueryService<br/>일일 학습 조회]
        TQ[TestQueryService<br/>테스트 결과 조회]
    end
```

---

## 10. 성능 최적화

| 최적화                 | 기법                     | 효과              |
|---------------------|------------------------|-----------------|
| N+1 방지              | BatchGetItem (100개 단위) | DB 호출 90% 감소    |
| TTS 캐싱              | S3 + Presigned URL     | Polly 호출 90% 절감 |
| 페이지네이션              | Cursor 기반 (Base64)     | 대용량 데이터 처리      |
| Sparse Index        | GSI3 (북마크 전용)          | 인덱스 크기 최소화      |
| 비동기 통계              | SNS/SQS                | API 응답 속도 향상    |
| Strongly Consistent | DailyStudy 조회          | 데이터 정합성         |

---

## 11. 파일 구조

```
domain/vocabulary/
├── handler/
│   ├── WordHandler.java
│   ├── UserWordHandler.java
│   ├── DailyStudyHandler.java
│   ├── TestHandler.java
│   ├── WordGroupHandler.java
│   ├── VoiceHandler.java
│   ├── StatsHandler.java
│   └── StatisticsHandler.java (SQS)
├── service/
│   ├── WordCommandService.java
│   ├── WordQueryService.java
│   ├── UserWordCommandService.java
│   ├── UserWordQueryService.java
│   ├── TestCommandService.java
│   ├── TestQueryService.java
│   ├── DailyStudyCommandService.java
│   ├── DailyStudyQueryService.java
│   ├── WordGroupCommandService.java
│   ├── StatsService.java
│   └── StatisticsService.java
├── repository/
│   ├── WordRepository.java
│   ├── UserWordRepository.java
│   ├── DailyStudyRepository.java
│   ├── TestResultRepository.java
│   └── WordGroupRepository.java
├── model/
│   ├── Word.java
│   ├── UserWord.java
│   ├── DailyStudy.java
│   ├── TestResult.java
│   └── WordGroup.java
├── state/
│   ├── WordState.java (interface)
│   ├── NewState.java
│   ├── LearningState.java
│   ├── ReviewingState.java
│   ├── MasteredState.java
│   ├── SpacedRepetitionContext.java
│   └── WordStateFactory.java
└── enums/
    ├── WordStatus.java
    ├── WordCategory.java
    └── TestType.java
```

---

## 12. 기술 스택

- **Runtime:** AWS Lambda (Java 21)
- **Database:** DynamoDB (Single Table Design)
- **TTS:** AWS Polly (남성/여성 음성)
- **Storage:** S3 (음성 캐시)
- **Messaging:** SNS/SQS (비동기 통계)
- **Pattern:** CQRS, State, Repository, Factory
