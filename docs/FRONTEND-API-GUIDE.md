# 프론트엔드 전달사항 - 채팅/게임 API 가이드

## 1. 현재 아키텍처 구조

### 채팅방 = 게임방 (동일 엔티티)
```
ChatRoom 모델
├── 기본 정보: roomId, name, description, level
├── 멤버 관리: memberIds, currentMembers, maxMembers
└── 게임 상태: gameStatus, scores, currentRound, currentDrawerId...
```

**핵심**: 채팅방과 게임방이 **분리되지 않음**. 하나의 채팅방에서 게임을 시작/종료하는 구조.

---

## 2. 게임 상태 (gameStatus)

| 상태 | 설명 | 게임 시작 가능 |
|------|------|:-------------:|
| `NONE` / `null` | 일반 채팅방 (게임 안함) | O |
| `WAITING` | 게임 대기 중 | X |
| `PLAYING` | 게임 진행 중 | X |
| `ROUND_END` | 라운드 종료 (다음 라운드 대기) | X |
| `FINISHED` | 게임 종료됨 | O |

---

## 3. REST API 엔드포인트

### 채팅방 API (`/api/chat/rooms`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/rooms` | 채팅방 생성 |
| GET | `/rooms` | 채팅방 목록 조회 |
| GET | `/rooms/{roomId}` | 채팅방 상세 조회 |
| POST | `/rooms/{roomId}/join` | 채팅방 입장 (roomToken 발급) |
| POST | `/rooms/{roomId}/leave` | 채팅방 퇴장 |
| DELETE | `/rooms/{roomId}` | 채팅방 삭제 (방장만) |

### 게임 API (`/api/game`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/rooms/{roomId}/game/start` | 게임 시작 |
| POST | `/rooms/{roomId}/game/stop` | 게임 중단 |
| GET | `/rooms/{roomId}/game/status` | 게임 상태 조회 |
| GET | `/rooms/{roomId}/game/scores` | 점수판 조회 |

---

## 4. 채팅방 목록 조회 쿼리 파라미터

```
GET /api/chat/rooms?level=beginner&joined=true&limit=10&cursor=xxx
```

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `level` | string | 난이도 필터: `beginner`, `intermediate`, `advanced` |
| `joined` | boolean | `true`면 내가 참여한 방만 |
| `limit` | number | 조회 개수 (기본 10, 최대 20) |
| `cursor` | string | 페이지네이션 커서 |

### 응답 예시
```json
{
  "success": true,
  "message": "Rooms retrieved",
  "data": {
    "rooms": [
      {
        "roomId": "abc-123",
        "name": "초보자 영어 스터디",
        "level": "beginner",
        "currentMembers": 3,
        "maxMembers": 6,
        "gameStatus": "PLAYING",
        "currentRound": 2,
        "totalRounds": 5
      }
    ],
    "nextCursor": "eyJQSyI6Ik...",
    "hasMore": true
  }
}
```

---

## 5. 프론트엔드에서 게임/채팅 구분하는 방법

### 방법 1: 클라이언트 필터링 (현재 가능)
```javascript
// 채팅방 목록 조회 후 클라이언트에서 필터링
const allRooms = await fetchRooms();

// 게임 중인 방만
const gamingRooms = allRooms.filter(room =>
  room.gameStatus === 'PLAYING' || room.gameStatus === 'WAITING'
);

// 일반 채팅방만
const chatRooms = allRooms.filter(room =>
  !room.gameStatus || room.gameStatus === 'NONE' || room.gameStatus === 'FINISHED'
);
```

### 방법 2: 백엔드 필터 추가 요청 (추후 가능)
```
GET /api/chat/rooms?gameStatus=PLAYING  // 게임 중인 방
GET /api/chat/rooms?gameStatus=NONE     // 채팅만 하는 방
```
> 현재 미구현. 필요시 백엔드에 요청

---

## 6. WebSocket 연결

### 채팅 WebSocket
```
wss://t378dif43l.execute-api.ap-northeast-2.amazonaws.com/dev?roomToken={roomToken}
```

### Grammar WebSocket
```
wss://ltrccmteo8.execute-api.ap-northeast-2.amazonaws.com/dev?token={jwtToken}
```

### 연결 순서
1. `POST /rooms/{roomId}/join` → `roomToken` 발급
2. WebSocket 연결 시 `roomToken` 쿼리 파라미터로 전달

---

## 7. WebSocket 메시지 타입 (messageType)

| 코드 | 타입 | 설명 |
|------|------|------|
| `MSG` | 일반 메시지 | 일반 채팅 메시지 |
| `VOICE` | 음성 메시지 | 음성 채팅 |
| `JOIN` | 입장 알림 | 사용자 입장 |
| `LEAVE` | 퇴장 알림 | 사용자 퇴장 |
| `GAME_START` | 게임 시작 | 게임 시작 알림 |
| `GAME_END` | 게임 종료 | 게임 종료 + 최종 순위 |
| `ROUND_START` | 라운드 시작 | 새 라운드 시작 |
| `ROUND_END` | 라운드 종료 | 정답 공개 |
| `ANSWER_CORRECT` | 정답 | 정답 맞춤 |
| `HINT` | 힌트 | 힌트 제공 |
| `SKIP` | 스킵 | 라운드 스킵 |
| `SYSTEM` | 시스템 | 시스템 메시지 |

---

## 8. 게임 명령어 (WebSocket)

채팅 메시지로 게임 명령어 전송:

| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/start` | 게임 시작 | 누구나 (2명 이상 접속 시) |
| `/stop` | 게임 중단 | 방장 또는 게임 시작자 |
| `/skip` | 라운드 스킵 | 누구나 |
| `/hint` | 힌트 제공 | 출제자만 |
| `/score` | 점수 확인 | 누구나 |

---

## 9. 게임 시작 응답 예시

```json
{
  "messageId": "uuid",
  "roomId": "abc-123",
  "userId": "SYSTEM",
  "content": "🎮 게임 시작!\n총 5 라운드\n\n라운드 1 시작!\n출제자: user-456",
  "messageType": "GAME_START",
  "createdAt": "2026-01-22T10:00:00Z",
  "gameStatus": "PLAYING",
  "currentRound": 1,
  "totalRounds": 5,
  "currentDrawerId": "user-456",
  "drawerOrder": ["user-456", "user-789", "user-123"]
}
```

---

## 10. 정답 체크 로직

- **한국어** 또는 **영어** 둘 다 정답으로 인정
- 대소문자 구분 없음
- 공백 무시

### 점수 계산
```
기본 점수: 10점
시간 보너스: (제한시간 - 경과시간) * 0.5
연속 정답 보너스: 연속정답수 * 2

총점 = 기본점수 + 시간보너스 + 연속정답보너스
```

---

## 11. 주의사항

1. **roomToken은 한 번만 사용**: 재연결 시 새로 발급 필요
2. **WebSocket 연결 실패 시**: `POST /rooms/{roomId}/join`으로 새 토큰 발급
3. **게임 중 퇴장**: 자동으로 다음 출제자로 넘어감 (2명 미만 시 게임 종료)
4. **출제자는 정답 입력 불가**: 본인이 출제자일 때 채팅해도 정답 체크 안됨

---

## 12. 에러 코드

| 코드 | 설명 |
|------|------|
| `ROOM_NOT_FOUND` | 채팅방 없음 |
| `ROOM_FULL` | 채팅방 인원 초과 |
| `ALREADY_JOINED` | 이미 참여 중 |
| `WRONG_PASSWORD` | 비밀번호 틀림 |
| `NOT_MEMBER` | 채팅방 멤버 아님 |
| `GAME_START_FAILED` | 게임 시작 실패 |
| `GAME_STOP_FAILED` | 게임 중단 실패 |

---

## 13. 추후 개선 예정 (백엔드)

- [ ] `gameStatus` 필터 파라미터 추가
- [ ] 게임 전용 방 타입 분리 (선택적)
- [ ] 관전 모드 지원
