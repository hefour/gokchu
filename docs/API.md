# 곡추 (GokChu) API 명세

> 1주차(MVP) + 재생 흐름(큐 전환) + 관리자 인증 기준. 구현된 엔드포인트만 기록.
> 검색(2주차, 하루 100회 쿼터 제약으로 후순위), WebSocket 실시간 push(3주차), 신청 제한/통계(4주차)는 아직 없음.
> 지금은 TV 클라이언트가 `now-playing`을 짧은 주기로 폴링하는 방식을 전제로 함 — 폴링은 "WebSocket 끊기면 폴링 폴백" 요구사항과 동일 경로라 나중에 WebSocket을 얹어도 이 API들은 그대로 유지됨.

## 공통 사항

- Base URL: `http://localhost:8080` (로컬)
- 인증: 손님/TV용 엔드포인트는 없음 — 테이블 QR에 담긴 `tableToken`(UUID)이 리소스 접근 단위.
  관리자 전용 엔드포인트(신청곡 수동 스킵, 폴백 재생목록 관리)는 `POST /api/admin/login`으로 받은
  토큰을 `Authorization: Bearer <token>` 헤더로 보내야 함. 아래 각 섹션에 표시.
- Content-Type: `application/json`

### 공통 에러 응답

```json
{
  "code": "TABLE_TOKEN_NOT_FOUND",
  "message": "존재하지 않는 테이블입니다.",
  "timestamp": "2026-09-14T10:00:00"
}
```

| code | HTTP status | message |
|---|---|---|
| `TABLE_TOKEN_NOT_FOUND` | 404 | 존재하지 않는 테이블입니다. |
| `INVALID_YOUTUBE_LINK` | 400 | 올바른 유튜브 링크가 아닙니다. |
| `VIDEO_NOT_FOUND` | 404 | 해당 영상을 찾을 수 없습니다. |
| `VIDEO_NOT_PLAYABLE` | 422 | 이 영상은 매장에서 재생할 수 없습니다. (embeddable=false 또는 매장 지역이 region restriction에 걸림) |
| `YOUTUBE_API_ERROR` | 502 | 유튜브 API 호출에 실패했습니다. |
| `INVALID_REQUEST` | 400 | 요청 바디 검증 실패 시 필드별 메시지(예: "유튜브 링크를 입력해 주세요.") |
| `QUEUE_ITEM_NOT_FOUND` | 404 | 존재하지 않는 큐 항목입니다. (다른 매장 소속인 경우도 동일하게 취급) |
| `QUEUE_ITEM_NOT_PLAYING` | 409 | 현재 재생 중인 곡이 아닙니다. (`complete`를 재생중이 아닌 곡에 호출) |
| `QUEUE_ITEM_ALREADY_FINISHED` | 409 | 이미 종료된 큐 항목입니다. (`skip`을 DONE/SKIPPED 곡에 호출) |
| `FALLBACK_TRACK_NOT_FOUND` | 404 | 존재하지 않는 폴백 트랙입니다. (다른 매장 소속인 경우도 동일하게 취급) |
| `INVALID_ADMIN_PASSWORD` | 401 | 비밀번호가 올바르지 않습니다. (`/api/admin/login`) |
| `UNAUTHORIZED_ADMIN` | 401 | 관리자 인증이 필요합니다. (관리자 전용 엔드포인트에 유효한 토큰 없이 접근) |
| `STORE_NOT_FOUND` | 404 | 존재하지 않는 매장입니다. |
| `TOO_MANY_LOGIN_ATTEMPTS` | 429 | 로그인 시도가 너무 많습니다. (IP당 15분에 5회 초과, `ADMIN_LOGIN_MAX_ATTEMPTS`/`ADMIN_LOGIN_WINDOW_MINUTES`로 조정) |

---

## 관리자 인증

### 로그인

```
POST /api/admin/login
```

**Request Body**

```json
{ "password": "매장 관리자 비밀번호" }
```

**Response** `200 OK`

```json
{ "token": "1789485444.ylLrXIMwW..." }
```

토큰은 서버가 시크릿으로 서명한 `만료시각.서명` 형태(HMAC-SHA256), 기본 12시간 유효
(`ADMIN_TOKEN_TTL_SECONDS`로 조정 가능). DB에 저장하지 않는 stateless 토큰이라 서버
재시작에도 시크릿(`ADMIN_TOKEN_SECRET`)만 그대로면 유효.

이후 관리자 전용 엔드포인트 호출 시 `Authorization: Bearer <token>` 헤더로 사용.

**IP당 15분에 5회로 시도 제한**(인메모리, 인스턴스 재시작하면 초기화됨). 실패할 때마다
카운트되고 성공하면 초기화. 6번째부터는 비밀번호가 맞아도 확인 없이 바로 `429`.

**에러**

| 상황 | code |
|---|---|
| 비밀번호 불일치 | `INVALID_ADMIN_PASSWORD` |
| 같은 IP에서 시간창 내 5회 초과 시도 | `TOO_MANY_LOGIN_ATTEMPTS` |

---

## 매장 관리 — 전부 관리자 전용, `Authorization: Bearer <token>` 필요

지금은 앱이 매장 하나만 가정하고 만들어졌지만, 매장/테이블 자체는 여러 개 만들 수 있다.
초기 셋업(매장 개설, 테이블별 QR용 토큰 발급)에 사용.

### 매장 생성

```
POST /api/stores
```

**Request Body**

```json
{ "name": "매장 이름", "regionCode": "KR" }
```

`regionCode`는 생략 가능(기본 `KR`).

**Response** `201 Created`

```json
{ "id": 1, "name": "매장 이름", "regionCode": "KR" }
```

---

### 테이블 생성 (QR용 토큰 발급)

```
POST /api/stores/{storeId}/tables
```

**Request Body**

```json
{ "label": "1번 테이블" }
```

**Response** `201 Created`

```json
{ "id": 1, "token": "dfe98382-becd-4a5e-bb63-534d47adea56", "label": "1번 테이블" }
```

응답의 `token`이 손님용 QR에 심을 `tableToken`(`/guest?tableToken=...` 등).

**에러**

| 상황 | code |
|---|---|
| storeId가 존재하지 않음 | `STORE_NOT_FOUND` |

---

### 매장의 테이블 목록 조회

```
GET /api/stores/{storeId}/tables
```

**Response** `200 OK` — `TableTokenResponse` 배열.

**에러**

| 상황 | code |
|---|---|
| storeId가 존재하지 않음 | `STORE_NOT_FOUND` |

---

## 큐 (Queue)

### 신청곡 등록

```
POST /api/tables/{tableToken}/queue-items
```

손님이 유튜브 링크를 붙여넣어 신청곡을 큐에 등록. 서버에서 videoId 추출 →
캐시(`CachedVideo`) 조회 또는 YouTube `videos.list` 호출로 재생 가능 여부(embeddable,
regionRestriction)를 검증한 뒤 큐에 저장. **해당 매장에 현재 재생중(`PLAYING`)인 곡이
없으면 등록과 동시에 바로 `PLAYING` 상태로 시작한다** (있으면 `WAITING`으로 대기).

**Path Parameters**

| 이름 | 타입 | 설명 |
|---|---|---|
| `tableToken` | UUID | 테이블 QR에 담긴 토큰 |

**Request Body**

```json
{
  "link": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `link` | string | Y | 유튜브 링크 (watch URL, youtu.be, 순수 videoId 등 `YoutubeLinkParser`가 지원하는 형식) |

**Response** `201 Created`

```json
{
  "id": 1,
  "videoId": "dQw4w9WgXcQ",
  "title": "Rick Astley - Never Gonna Give You Up",
  "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
  "status": "WAITING",
  "requestedAt": "2026-09-14T10:00:00"
}
```

**에러**

| 상황 | code |
|---|---|
| tableToken이 존재하지 않음 | `TABLE_TOKEN_NOT_FOUND` |
| link가 비어있음 | `INVALID_REQUEST` |
| 링크에서 videoId를 추출할 수 없음 | `INVALID_YOUTUBE_LINK` |
| YouTube에 해당 videoId가 없음 | `VIDEO_NOT_FOUND` |
| embeddable=false 이거나 매장 지역이 재생 제한 지역임 | `VIDEO_NOT_PLAYABLE` |
| YouTube API 호출 실패(쿼터 초과, 네트워크 등) | `YOUTUBE_API_ERROR` |

---

### 큐 조회

```
GET /api/tables/{tableToken}/queue-items
```

해당 테이블이 속한 매장의 대기중(`WAITING`) + 재생중(`PLAYING`) 큐 항목을
신청 시각 오름차순으로 반환. `DONE`/`SKIPPED` 항목은 포함하지 않음.

**Path Parameters**

| 이름 | 타입 | 설명 |
|---|---|---|
| `tableToken` | UUID | 테이블 QR에 담긴 토큰 (조회는 매장 단위이므로 같은 매장 내 어느 테이블 토큰으로 조회해도 결과 동일) |

**Response** `200 OK`

```json
[
  {
    "id": 1,
    "videoId": "dQw4w9WgXcQ",
    "title": "Rick Astley - Never Gonna Give You Up",
    "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
    "status": "WAITING",
    "requestedAt": "2026-09-14T10:00:00"
  }
]
```

**에러**

| 상황 | code |
|---|---|
| tableToken이 존재하지 않음 | `TABLE_TOKEN_NOT_FOUND` |

---

## 재생 (Playback)

재생 우선순위: **신청곡(QUEUE) > 폴백 재생목록(FALLBACK)**. 매장에 신청곡이
하나도 없을 때(오픈 직후, 또는 큐 소진 후)는 폴백 재생목록에서 한 곡을 셔플로
틀고, 신청곡이 들어와도 **현재 재생중인 곡(폴백이든 신청곡이든)이 끝날 때까지는
끊지 않는다.** 신청곡이 없으면 다시 폴백으로 돌아간다.

### 현재 재생곡 조회

```
GET /api/tables/{tableToken}/now-playing
```

TV 클라이언트가 폴링으로 호출. 다음 우선순위로 판단해 응답한다.

1. 매장에 `PLAYING` 상태 신청곡이 있으면 그것을 반환 (`source: "QUEUE"`)
2. 없고 이미 재생중인 폴백 트랙이 있으면 그걸 그대로 반환 (`source: "FALLBACK"`, 폴링마다 곡이
   안 바뀌도록 매장에 "현재 폴백 트랙" 상태를 들고 있음)
3. 아무것도 재생중인 게 없으면(매장을 처음 열었을 때 등) 폴백 목록에서 한 곡을 셔플로
   골라 재생을 시작시키고 반환
4. 폴백 트랙도 하나도 등록돼 있지 않으면 `204 No Content`

**Response** `200 OK`

```json
{
  "source": "QUEUE",
  "queueItemId": 4,
  "videoId": "dQw4w9WgXcQ",
  "title": "Rick Astley - Never Gonna Give You Up",
  "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
}
```

`source`가 `"FALLBACK"`이면 `queueItemId`는 `null`.

**Response** `204 No Content` — 신청곡도 없고 폴백 트랙도 하나도 등록돼 있지 않음.

**에러**

| 상황 | code |
|---|---|
| tableToken이 존재하지 않음 | `TABLE_TOKEN_NOT_FOUND` |

---

### 재생 종료 통보 (다음으로 전환)

```
POST /api/tables/{tableToken}/now-playing/complete
```

TV의 YouTube IFrame Player가 `onStateChange`에서 `ENDED`를 감지했을 때 호출.
**`queueItemId`를 받지 않는다** — 서버가 "지금 뭐가 재생중인지"(신청곡인지 폴백인지)를
직접 알고 있으므로, TV는 그냥 "끝났다"고만 알리면 된다. 처리 순서:

1. 지금 재생중이던 게 신청곡이면 `DONE` 처리, 폴백 트랙이면 "현재 폴백" 상태를 해제
2. 대기중인 신청곡(`WAITING`, 신청 시각 오름차순 최선참)이 있으면 그걸 `PLAYING`으로
   승격해서 응답
3. 없으면 폴백 목록에서 한 곡을 골라 재생 시작 (방금 끝난 게 폴백 트랙이었다면 같은
   곡이 연달아 나오지 않도록 그 곡은 후보에서 제외하고 고름)
4. 폴백 트랙도 없으면 `204 No Content`

**Response**: `GET /now-playing`과 동일한 `NowPlayingResponse` 형태, 또는 `204 No Content`.

**에러**

| 상황 | code |
|---|---|
| tableToken이 존재하지 않음 | `TABLE_TOKEN_NOT_FOUND` |

---

### 곡 스킵 (관리자 전용 — `Authorization: Bearer <token>` 필요)

```
POST /api/tables/{tableToken}/queue-items/{queueItemId}/skip
```

특정 신청곡 `queueItemId`를 `SKIPPED`로 변경. 스킵 대상이 현재 재생중이었다면
`now-playing/complete`와 동일한 승격/폴백 전환 로직을 탄다. 대기중인 곡을 스킵한
경우 현재 재생중인 것(신청곡이든 폴백이든)은 바뀌지 않는다. 폴백 트랙 자체를
스킵하는 기능은 아직 없음(신청곡 전용).

**Response**: `NowPlayingResponse` 또는 `204 No Content`(스킵 후 재생중인 게 없음).

**에러**

| 상황 | code |
|---|---|
| tableToken이 존재하지 않음 | `TABLE_TOKEN_NOT_FOUND` |
| Authorization 헤더가 없거나 토큰이 유효하지 않음/만료됨 | `UNAUTHORIZED_ADMIN` |
| queueItemId가 존재하지 않거나 다른 매장 소속 | `QUEUE_ITEM_NOT_FOUND` |
| 이미 `DONE` 또는 `SKIPPED` 상태인 항목 | `QUEUE_ITEM_ALREADY_FINISHED` |

---

### 재생 불가 신고 (TV 전용, 공개)

```
POST /api/tables/{tableToken}/queue-items/{queueItemId}/report-unplayable
```

TV의 YouTube IFrame Player가 `onError`를 만났을 때(사전 검증을 뚫고 들어온 재생 불가
영상) 호출. 로직은 `skip`과 완전히 동일하지만 **인증이 필요 없다** — TV는 자격 증명이
없는 공용 기기라서 관리자 전용 엔드포인트를 쓸 수 없기 때문에 별도로 공개해둔 경로.
응답/에러는 `skip`과 동일(단, `UNAUTHORIZED_ADMIN`은 해당 없음).

---

## 폴백 재생목록 (Fallback) — 전부 관리자 전용, `Authorization: Bearer <token>` 필요

매장이 미리 등록해둔 재생목록. 신청곡이 없을 때 여기서 셔플로 재생된다.

### 폴백 트랙 등록

```
POST /api/tables/{tableToken}/fallback-tracks
```

**Request Body**

```json
{ "link": "https://www.youtube.com/watch?v=jNQXAC9IVRw" }
```

**Response** `201 Created`

```json
{
  "id": 1,
  "videoId": "jNQXAC9IVRw",
  "title": "Me at the zoo",
  "thumbnailUrl": "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg"
}
```

큐 등록과 동일하게 embeddable/regionRestriction 검증을 거친다. 에러 코드도 큐
등록(`POST /queue-items`)과 동일.

---

### 폴백 트랙 목록 조회

```
GET /api/tables/{tableToken}/fallback-tracks
```

**Response** `200 OK` — `FallbackTrackResponse` 배열.

---

### 폴백 트랙 삭제

```
DELETE /api/tables/{tableToken}/fallback-tracks/{fallbackTrackId}
```

**Response** `204 No Content`. 삭제 대상이 매장에서 지금 재생중인 폴백 트랙이었다면
매장의 "현재 폴백" 상태도 같이 초기화한다(다음 `now-playing` 호출 때 새로 하나 골라 시작).

**에러**

| 상황 | code |
|---|---|
| tableToken이 존재하지 않음 | `TABLE_TOKEN_NOT_FOUND` |
| fallbackTrackId가 존재하지 않거나 다른 매장 소속 | `FALLBACK_TRACK_NOT_FOUND` |

---

## 데이터 모델 참고

### QueueItemStatus

`WAITING` → `PLAYING` → `DONE` (또는 `WAITING`/`PLAYING` → `SKIPPED`).
전이는 등록 시 자동 승격(재생중인 게 아무것도 없을 때) 또는 `now-playing/complete`,
`skip` 호출로만 발생하며, 한 매장에 동시에 `PLAYING` 상태 항목은 최대 1개라는
불변식을 서비스 계층에서 보장한다.

### 매장의 "현재 폴백 트랙" 상태

`Store`가 `currentFallbackVideoId`/`Title`/`ThumbnailUrl`을 직접 들고 있다.
신청곡과 달리 폴백 트랙은 `queue_items` 테이블에 행으로 남지 않고, 매장에 붙은
이 상태값 하나로만 "지금 뭘 틀고 있는지"를 추적한다 — 그래서 `now-playing`을
반복 폴링해도 같은 곡이 유지된다.

---

## 아직 없는 것 (다음 주차 예정)

- `GET /api/tables/{tableToken}/search?q=...` — YouTube 검색 (2주차, 하루 100회 쿼터 제약으로 후순위)
- WebSocket `/ws` — `now-playing` 폴링을 대체할 실시간 push (3주차)
- 단골손님 개인 플레이리스트 저장/재생 (언급만 됨, 스키마 미정 — 폴백 재생목록과는
  별개 개념으로 나중에 설계)
- 관리자 API (신청 일시중단, 통계) (4주차) — 스킵/폴백 관리는 인증 완료됨
- 신청 제한(테이블당 N곡) 관련 응답/에러 코드 (4주차)
