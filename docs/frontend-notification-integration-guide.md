# 프론트엔드 실시간 알림 연동 가이드

## 개요

이 문서는 백엔드 알림 시스템과 프론트엔드를 연동하기 위한 가이드입니다.
**Server-Sent Events (SSE)** 를 사용하여 실시간 알림을 수신합니다.

---

## 연결 방식

### SSE (Server-Sent Events) 사용

- WebSocket과 달리 **단방향 통신** (서버 → 클라이언트)
- HTTP 기반으로 별도 프로토콜 핸들링 불필요
- 브라우저 `EventSource` API로 간단히 구현 가능
- 연결 끊김 시 자동 재연결 지원

---

## 연결 엔드포인트

```
GET {NOTIFICATION_FUNCTION_URL}?userId={userId}
```

| 파라미터 | 설명 | 예시 |
|---------|------|------|
| `userId` | 로그인한 사용자 ID | `user-123` |

> ⚠️ **NOTIFICATION_FUNCTION_URL**은 배포 환경별로 다릅니다. 환경변수로 관리하세요.

---

## 기본 연결 구현

### JavaScript (Vanilla)

```javascript
const connectNotifications = (userId) => {
  const url = `${NOTIFICATION_FUNCTION_URL}?userId=${userId}`;
  const eventSource = new EventSource(url);

  // 알림 수신
  eventSource.onmessage = (event) => {
    const notification = JSON.parse(event.data);
    handleNotification(notification);
  };

  // 연결 성공
  eventSource.onopen = () => {
    console.log('알림 연결 성공');
  };

  // 에러 처리
  eventSource.onerror = (error) => {
    console.error('알림 연결 에러:', error);
    // EventSource는 자동으로 재연결을 시도합니다
  };

  return eventSource;
};

// 연결 해제
const disconnect = (eventSource) => {
  eventSource.close();
};
```

### React Hook 예시

```typescript
import { useEffect, useCallback, useRef } from 'react';

interface Notification {
  notificationId: string;
  type: NotificationType;
  userId: string;
  payload: Record<string, any>;
  createdAt: string;
}

type NotificationType =
  | 'BADGE_EARNED'
  | 'DAILY_COMPLETE'
  | 'STREAK_REMINDER'
  | 'TEST_COMPLETE'
  | 'NEWS_QUIZ_COMPLETE'
  | 'GAME_END'
  | 'GAME_STREAK'
  | 'OPIC_COMPLETE';

export const useNotifications = (
  userId: string | null,
  onNotification: (notification: Notification) => void
) => {
  const eventSourceRef = useRef<EventSource | null>(null);

  const connect = useCallback(() => {
    if (!userId) return;

    const url = `${process.env.NEXT_PUBLIC_NOTIFICATION_URL}?userId=${userId}`;
    const eventSource = new EventSource(url);

    eventSource.onmessage = (event) => {
      // Heartbeat 무시
      if (event.data === 'HEARTBEAT') return;

      try {
        const notification: Notification = JSON.parse(event.data);
        onNotification(notification);
      } catch (e) {
        console.error('알림 파싱 실패:', e);
      }
    };

    eventSource.onerror = () => {
      console.log('알림 연결 끊김, 재연결 시도 중...');
    };

    eventSourceRef.current = eventSource;
  }, [userId, onNotification]);

  const disconnect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  }, []);

  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  return { disconnect, reconnect: connect };
};
```

### React 컴포넌트 사용 예시

```tsx
const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const handleNotification = useCallback((notification: Notification) => {
    setNotifications(prev => [notification, ...prev]);

    // 타입별 처리
    switch (notification.type) {
      case 'BADGE_EARNED':
        showBadgeToast(notification.payload);
        break;
      case 'DAILY_COMPLETE':
        showStreakCelebration(notification.payload);
        break;
      case 'GAME_END':
        showGameResult(notification.payload);
        break;
      // ... 기타 타입
    }
  }, []);

  useNotifications(user?.id ?? null, handleNotification);

  return (
    <NotificationContext.Provider value={{ notifications }}>
      {children}
    </NotificationContext.Provider>
  );
};
```

---

## 알림 타입 및 Payload 구조

### 공통 응답 구조

```typescript
interface Notification {
  notificationId: string;    // "notif-xxxxxxxx" 형식
  type: NotificationType;    // 알림 타입
  userId: string;            // 대상 사용자 ID
  payload: object;           // 타입별 상세 데이터
  createdAt: string;         // ISO-8601 형식 (예: "2024-01-15T09:30:00Z")
}
```

---

### 1. BADGE_EARNED (배지 획득)

사용자가 새로운 배지를 획득했을 때

```typescript
interface BadgeEarnedPayload {
  badgeType: string;      // 배지 타입 코드
  badgeName: string;      // 배지 이름
  description: string;    // 배지 설명
  iconUrl: string;        // 배지 아이콘 URL
}
```

**예시:**
```json
{
  "notificationId": "notif-a1b2c3d4",
  "type": "BADGE_EARNED",
  "userId": "user-123",
  "payload": {
    "badgeType": "STREAK_7",
    "badgeName": "7일 연속 학습",
    "description": "7일 연속으로 학습을 완료했습니다!",
    "iconUrl": "https://cdn.example.com/badges/streak-7.png"
  },
  "createdAt": "2024-01-15T09:30:00Z"
}
```

---

### 2. DAILY_COMPLETE (일일 학습 완료)

오늘의 단어 학습을 모두 완료했을 때

```typescript
interface DailyCompletePayload {
  date: string;           // 학습 완료 날짜 (YYYY-MM-DD)
  wordsLearned: number;   // 오늘 학습한 단어 수
  totalWords: number;     // 총 학습 단어 수
  currentStreak: number;  // 현재 연속 학습 일수
}
```

**예시:**
```json
{
  "notificationId": "notif-e5f6g7h8",
  "type": "DAILY_COMPLETE",
  "userId": "user-123",
  "payload": {
    "date": "2024-01-15",
    "wordsLearned": 20,
    "totalWords": 150,
    "currentStreak": 5
  },
  "createdAt": "2024-01-15T14:00:00Z"
}
```

---

### 3. STREAK_REMINDER (연속 학습 리마인더)

매일 21:00 KST에 오늘 학습을 아직 하지 않은 사용자에게 발송

```typescript
interface StreakReminderPayload {
  currentStreak: number;  // 현재 연속 학습 일수
  message: string;        // 리마인더 메시지
}
```

**예시:**
```json
{
  "notificationId": "notif-i9j0k1l2",
  "type": "STREAK_REMINDER",
  "userId": "user-123",
  "payload": {
    "currentStreak": 5,
    "message": "오늘 학습을 완료하고 6일 연속 학습을 달성하세요!"
  },
  "createdAt": "2024-01-15T12:00:00Z"
}
```

---

### 4. TEST_COMPLETE (단어 테스트 완료)

단어 테스트를 완료했을 때

```typescript
interface TestCompletePayload {
  testId: string;         // 테스트 ID
  score: number;          // 점수 (0-100)
  correctCount: number;   // 맞힌 문제 수
  totalCount: number;     // 전체 문제 수
  isPerfect: boolean;     // 만점 여부
}
```

**예시:**
```json
{
  "notificationId": "notif-m3n4o5p6",
  "type": "TEST_COMPLETE",
  "userId": "user-123",
  "payload": {
    "testId": "test-abc123",
    "score": 85,
    "correctCount": 17,
    "totalCount": 20,
    "isPerfect": false
  },
  "createdAt": "2024-01-15T10:30:00Z"
}
```

---

### 5. NEWS_QUIZ_COMPLETE (뉴스 퀴즈 완료)

뉴스 기사 퀴즈를 완료했을 때

```typescript
interface NewsQuizCompletePayload {
  articleId: string;      // 뉴스 기사 ID
  articleTitle: string;   // 기사 제목
  score: number;          // 점수 (0-100)
  correctCount: number;   // 맞힌 문제 수
  totalCount: number;     // 전체 문제 수
  isPerfect: boolean;     // 만점 여부
}
```

**예시:**
```json
{
  "notificationId": "notif-q7r8s9t0",
  "type": "NEWS_QUIZ_COMPLETE",
  "userId": "user-123",
  "payload": {
    "articleId": "article-xyz789",
    "articleTitle": "Tech Giants Report Strong Q4 Earnings",
    "score": 100,
    "correctCount": 5,
    "totalCount": 5,
    "isPerfect": true
  },
  "createdAt": "2024-01-15T11:00:00Z"
}
```

---

### 6. GAME_END (게임 종료)

캐치마인드 게임이 종료되었을 때

```typescript
interface GameEndPayload {
  roomId: string;           // 게임 방 ID
  gameSessionId: string;    // 게임 세션 ID
  rank: number;             // 최종 순위
  totalPlayers: number;     // 전체 플레이어 수
  score: number;            // 획득 점수
  isWinner: boolean;        // 1등 여부
}
```

**예시:**
```json
{
  "notificationId": "notif-u1v2w3x4",
  "type": "GAME_END",
  "userId": "user-123",
  "payload": {
    "roomId": "room-game-001",
    "gameSessionId": "session-abc",
    "rank": 1,
    "totalPlayers": 4,
    "score": 2500,
    "isWinner": true
  },
  "createdAt": "2024-01-15T15:30:00Z"
}
```

---

### 7. GAME_STREAK (게임 연속 정답)

게임 중 연속 정답을 달성했을 때

```typescript
interface GameStreakPayload {
  roomId: string;           // 게임 방 ID
  streakCount: number;      // 연속 정답 횟수
  bonusPoints: number;      // 보너스 점수
}
```

**예시:**
```json
{
  "notificationId": "notif-y5z6a7b8",
  "type": "GAME_STREAK",
  "userId": "user-123",
  "payload": {
    "roomId": "room-game-001",
    "streakCount": 5,
    "bonusPoints": 500
  },
  "createdAt": "2024-01-15T15:25:00Z"
}
```

---

### 8. OPIC_COMPLETE (OPIc 연습 완료)

OPIc 스피킹 연습 세션을 완료했을 때

```typescript
interface OpicCompletePayload {
  sessionId: string;          // 세션 ID
  estimatedLevel: string;     // 예상 등급 (IM1, IM2, IH, AL 등)
  questionsAnswered: number;  // 답변한 문제 수
  feedbackSummary: string;    // 피드백 요약
}
```

**예시:**
```json
{
  "notificationId": "notif-c9d0e1f2",
  "type": "OPIC_COMPLETE",
  "userId": "user-123",
  "payload": {
    "sessionId": "opic-session-456",
    "estimatedLevel": "IM2",
    "questionsAnswered": 15,
    "feedbackSummary": "발음과 유창성이 좋습니다. 문법적 정확성을 더 연습하세요."
  },
  "createdAt": "2024-01-15T16:00:00Z"
}
```

---

## 특수 이벤트

### HEARTBEAT (하트비트)

서버에서 연결 유지를 위해 1초마다 전송합니다. 무시하면 됩니다.

```javascript
eventSource.onmessage = (event) => {
  if (event.data === 'HEARTBEAT') return; // 무시
  // ...
};
```

### STREAM_END (스트림 종료)

서버가 연결을 종료할 때 전송됩니다. (최대 14분 후)
`EventSource`는 자동으로 재연결을 시도합니다.

---

## 연결 관리 권장사항

### 1. 연결 시점

```typescript
// 로그인 후 연결
const handleLoginSuccess = (user: User) => {
  connectNotifications(user.id);
};

// 페이지 로드 시 (이미 로그인된 경우)
useEffect(() => {
  if (isAuthenticated && user) {
    connectNotifications(user.id);
  }
}, [isAuthenticated, user]);
```

### 2. 연결 해제 시점

```typescript
// 로그아웃 시
const handleLogout = () => {
  disconnectNotifications();
  // ...
};

// 페이지 언마운트 시 (SPA)
useEffect(() => {
  return () => disconnectNotifications();
}, []);
```

### 3. 재연결 처리

`EventSource`는 연결 끊김 시 자동 재연결을 시도합니다.
추가적인 재연결 로직이 필요한 경우:

```typescript
const MAX_RETRY_COUNT = 5;
let retryCount = 0;

eventSource.onerror = () => {
  retryCount++;

  if (retryCount >= MAX_RETRY_COUNT) {
    eventSource.close();
    showErrorMessage('알림 서버 연결에 실패했습니다. 새로고침해주세요.');
  }
};

eventSource.onopen = () => {
  retryCount = 0; // 연결 성공 시 초기화
};
```

---

## UI 처리 권장사항

### 토스트 알림

```typescript
const showNotificationToast = (notification: Notification) => {
  const config = getToastConfig(notification.type);

  toast({
    title: config.title,
    description: formatPayload(notification.payload),
    icon: config.icon,
    duration: config.duration,
  });
};

const getToastConfig = (type: NotificationType) => {
  switch (type) {
    case 'BADGE_EARNED':
      return { title: '🏆 배지 획득!', icon: 'trophy', duration: 5000 };
    case 'DAILY_COMPLETE':
      return { title: '✅ 오늘의 학습 완료!', icon: 'check', duration: 4000 };
    case 'STREAK_REMINDER':
      return { title: '⏰ 학습 리마인더', icon: 'clock', duration: 6000 };
    case 'TEST_COMPLETE':
      return { title: '📝 테스트 완료', icon: 'file', duration: 3000 };
    case 'GAME_END':
      return { title: '🎮 게임 종료', icon: 'gamepad', duration: 4000 };
    default:
      return { title: '알림', icon: 'bell', duration: 3000 };
  }
};
```

### 알림 센터

```typescript
const NotificationCenter: React.FC = () => {
  const { notifications } = useNotificationContext();
  const [unreadCount, setUnreadCount] = useState(0);

  return (
    <Dropdown>
      <DropdownTrigger>
        <Bell />
        {unreadCount > 0 && <Badge count={unreadCount} />}
      </DropdownTrigger>
      <DropdownContent>
        {notifications.map(notif => (
          <NotificationItem
            key={notif.notificationId}
            notification={notif}
          />
        ))}
      </DropdownContent>
    </Dropdown>
  );
};
```

---

## TypeScript 타입 정의 (복사용)

```typescript
// types/notification.ts

export type NotificationType =
  | 'BADGE_EARNED'
  | 'DAILY_COMPLETE'
  | 'STREAK_REMINDER'
  | 'TEST_COMPLETE'
  | 'NEWS_QUIZ_COMPLETE'
  | 'GAME_END'
  | 'GAME_STREAK'
  | 'OPIC_COMPLETE';

export interface BaseNotification<T extends NotificationType, P> {
  notificationId: string;
  type: T;
  userId: string;
  payload: P;
  createdAt: string;
}

export interface BadgeEarnedPayload {
  badgeType: string;
  badgeName: string;
  description: string;
  iconUrl: string;
}

export interface DailyCompletePayload {
  date: string;
  wordsLearned: number;
  totalWords: number;
  currentStreak: number;
}

export interface StreakReminderPayload {
  currentStreak: number;
  message: string;
}

export interface TestCompletePayload {
  testId: string;
  score: number;
  correctCount: number;
  totalCount: number;
  isPerfect: boolean;
}

export interface NewsQuizCompletePayload {
  articleId: string;
  articleTitle: string;
  score: number;
  correctCount: number;
  totalCount: number;
  isPerfect: boolean;
}

export interface GameEndPayload {
  roomId: string;
  gameSessionId: string;
  rank: number;
  totalPlayers: number;
  score: number;
  isWinner: boolean;
}

export interface GameStreakPayload {
  roomId: string;
  streakCount: number;
  bonusPoints: number;
}

export interface OpicCompletePayload {
  sessionId: string;
  estimatedLevel: string;
  questionsAnswered: number;
  feedbackSummary: string;
}

export type Notification =
  | BaseNotification<'BADGE_EARNED', BadgeEarnedPayload>
  | BaseNotification<'DAILY_COMPLETE', DailyCompletePayload>
  | BaseNotification<'STREAK_REMINDER', StreakReminderPayload>
  | BaseNotification<'TEST_COMPLETE', TestCompletePayload>
  | BaseNotification<'NEWS_QUIZ_COMPLETE', NewsQuizCompletePayload>
  | BaseNotification<'GAME_END', GameEndPayload>
  | BaseNotification<'GAME_STREAK', GameStreakPayload>
  | BaseNotification<'OPIC_COMPLETE', OpicCompletePayload>;
```

---

## 환경 설정

### 환경 변수

| 환경 | URL |
|------|-----|
| **Test** | `https://flhf42jd6xgrh26wrqgwxmbmee0zmjnv.lambda-url.ap-northeast-2.on.aws/` |
| **Prod** | (배포 후 업데이트 예정) |

```env
# .env.local (Next.js)
NEXT_PUBLIC_NOTIFICATION_URL=https://flhf42jd6xgrh26wrqgwxmbmee0zmjnv.lambda-url.ap-northeast-2.on.aws

# .env (Vite)
VITE_NOTIFICATION_URL=https://flhf42jd6xgrh26wrqgwxmbmee0zmjnv.lambda-url.ap-northeast-2.on.aws
```

---

## 테스트 방법

### 개발 환경에서 테스트

1. 브라우저 개발자 도구 → Network 탭 열기
2. EventStream 필터 선택
3. 로그인 후 알림 연결 확인
4. 학습 완료, 테스트 제출 등의 액션 수행
5. 실시간으로 알림 수신 확인

### Mock SSE 서버 (로컬 테스트용)

```javascript
// mock-sse-server.js
const http = require('http');

http.createServer((req, res) => {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'Access-Control-Allow-Origin': '*',
  });

  // 테스트 알림 전송
  setInterval(() => {
    const notification = {
      notificationId: `notif-${Date.now()}`,
      type: 'BADGE_EARNED',
      userId: 'test-user',
      payload: {
        badgeType: 'TEST_BADGE',
        badgeName: '테스트 배지',
        description: '테스트용 배지입니다',
        iconUrl: 'https://example.com/badge.png',
      },
      createdAt: new Date().toISOString(),
    };
    res.write(`data: ${JSON.stringify(notification)}\n\n`);
  }, 5000);
}).listen(3001);

console.log('Mock SSE server running on http://localhost:3001');
```

---

## 문의

백엔드 알림 시스템 관련 문의: **[백엔드 담당자 이름/연락처]**