# 곡추 (GokChu)

## 개요
캐주얼 바 등 매장용 QR 신청곡 서비스. "곡 추천"의 줄임말.
손님이 테이블 QR로 접속해 유튜브 링크/검색으로 곡 신청 → 매장 TV에서 순차 자동재생.
포트폴리오 겸 실사용(직장 매장 투입) 목적의 개인 프로젝트.

## 핵심 시나리오
- 손님: 테이블 QR 스캔 → 링크 붙여넣기 or 검색으로 신청 (제한 없음, 추후 추가 예정)
- TV: 큐 순서대로 유튜브 영상 전체화면 자동재생, 큐 비면 폴백 재생목록/셔플
- 관리자: 큐 관리, 스킵, 신청 일시중단, 통계 확인

## 스택
- Backend: Spring Boot 4.1.1, Java 21, Gradle(Groovy)
- DB: PostgreSQL (JPA 추상화라 전환 가능, 잠정 확정)
- Cache/Queue: Redis (2주차부터 도입 — 검색 캐싱, 이후 신청 제한 카운터)
- Realtime: Spring WebSocket + STOMP (3주차부터 도입)
- Frontend: React + Vite (손님용 / TV용 / 관리자용 라우트 분리), 배포 Vercel
- 배포: Docker Compose, Oracle Cloud (Always Free, 정책 변경 이슈 있으니 가입 시 실제 스펙 재확인 필요)

## 개발 순서 (로드맵)
1주차: 도메인/DB 설계, 링크 붙여넣기만으로 동작하는 MVP (검색 없음, videos.list만 사용, 신청 제한 없음)
2주차: YouTube 검색 연동 + Redis 캐싱, embeddable/regionRestriction 사전 검증
3주차: WebSocket 실시간 큐 동기화, TV 플레이어 화면
4주차: 관리자 대시보드, 신청 제한 로직(테이블당 N곡 + 단골 무제한), 배포

## 핵심 제약사항 (반드시 지킬 것)

### YouTube API 쿼터
- 일일 쿼터 10,000유닛. `search.list`=100유닛, `videos.list`=1유닛
- 검색은 자체 캐시(CachedVideo) 우선 조회 → 캐시 미스일 때만 search.list 호출
- 검색 결과는 maxResults=50으로 받아 전량 캐시에 저장 (역색인)
- 검색어 정규화 후 캐시 키로 사용 (공백/대소문자/초성 통일)
- 링크로 추가하는 경로(videos.list, 1유닛)를 항상 병행 제공

### 재생 가능 여부 검증
- 큐 등록 전 반드시 `videos.list`로 `status.embeddable`, `contentDetails.regionRestriction` 확인
- 실패 시 즉시 사용자에게 재생 불가 안내, 큐에 넣지 않음
- 조회한 videoId는 결과를 캐시해 재검증 API 호출 방지

### 실시간 동기화
- 플레이어(TV)가 단일 진실 원천(source of truth)
- YouTube IFrame Player의 `onStateChange`에서 ENDED 감지 → 서버에 완료 통보 → 다음 곡 push
- WebSocket 끊기면 폴링 폴백
- `relatedToVideoId`(유튜브 자동 추천 API)는 폐지됨 — 절대 사용 시도하지 말 것. 폴백은 매장이 미리 등록한 재생목록에서 셔플

## 도메인 모델
- `Store` — 매장. 폴백 재생목록, 신청 제한 정책
- `TableToken` — 테이블별 QR 토큰. label 등 (제한 로직은 보류, 필드는 존재)
- `QueueItem` — 큐 항목. videoId, 제목, 썸네일, tableToken, 상태(WAITING/PLAYING/DONE/SKIPPED), requestedAt
- `PlayHistory` — 재생 완료 이력 (통계용)
- `CachedVideo` — 검색/링크로 조회한 영상 메타데이터 캐시 (embeddable 여부 포함)

## 나중에 추가할 것 (설계는 끝났으나 구현 보류)
- 신청 제한: 테이블 QR 토큰(tableToken) 기준 N곡 제한, Redis INCR + 자정 TTL
- 단골: 관리자가 테이블별 무제한 토글
- 카운트는 "큐에 정상 등록된 시점" 기준, 삭제돼도 복구 안 함(어뷰징 방지) — 이 원칙은 나중에 붙일 때도 유지

## 안 할 것 (트랩 방지용 명시)
- YouTube API 키 여러 개 돌려서 쿼터 우회 — 약관 위반, 계정 정지 리스크
- yt-dlp 등 스크래핑으로 검색/재생 우회 — 약관 위반, 자주 깨짐
- 개인 식별 기반 제한(로그인, 기기 지문 등) — 이 프로젝트 스코프 아님, 테이블 토큰으로 충분
- `relatedToVideoId` 기반 추천 API 사용 — 이미 폐지된 기능

## 진행 상황 (2026-09-14 기준)

### 이번 세션 (2026-09-14) — 1주차 검증 완료
- Docker Desktop 기동 → `docker-compose down -v && up -d`로 Postgres 볼륨 재생성 (지난 세션의 비밀번호 불일치 해결)
- **신규 발견**: 이 머신에 네이티브 `postgresql-x64-17` Windows 서비스가 이미 포트 5432를 점유하고 있어서,
  Docker 컨테이너로 포트가 매핑돼도 앱이 실제로는 네이티브 Postgres에 연결되고 있었음(그 안엔 빈 DB라 스키마 에러 발생 경로가 헷갈리게 나왔음).
  → `docker-compose.yml` 포트를 `5433:5432`로 변경, 로컬 실행 시 `DB_URL=jdbc:postgresql://localhost:5433/gokchu` 환경변수 필요.
  네이티브 서비스는 다른 프로젝트가 쓰고 있을 수 있어 중지하지 않음.
- **버그 발견 및 수정**: Spring Boot 4.1.1부터 Flyway 자동설정이 `spring-boot-autoconfigure`에서 분리되어
  `org.springframework.boot:spring-boot-flyway` 모듈을 명시적으로 추가해야 동작함(`flyway-core`+`flyway-database-postgresql`만으로는
  FlywayAutoConfiguration 자체가 클래스패스에 없어서 조용히 미실행되고, Hibernate `ddl-auto: validate`가 빈 스키마를 보고 실패).
  `build.gradle`에 `implementation 'org.springframework.boot:spring-boot-flyway'` 추가로 해결.
- `./gradlew test` (`GokchuApplicationTests`, `YoutubeLinkParserTest`) 전체 통과 확인. Flyway 마이그레이션도 정상 적용됨(7개 테이블 생성 확인).
- YouTube Data API 키: **발급 완료**. Google Cloud 프로젝트 `gokchu` 생성 → YouTube Data API v3 사용 설정 →
  API 키 생성(`gokchu-youtube-key`, API 제한사항을 YouTube Data API v3로 제한). `videos.list` 실제 호출로 스모크 테스트 통과
  (`dQw4w9WgXcQ` 조회 성공). 애플리케이션 제한사항(IP 등)은 배포 시점에 서버 IP로 추가 예정, 현재는 "없음".
  키 발급 과정에서 이 Google 계정의 2단계 인증이 꺼져 있어 Cloud Console 접근이 막혔었고, 사용자가 직접 켠 뒤 진행함.
- Redis 로컬 실행 방식: 여전히 미정

### 로컬 실행 시 주의사항 (신규)
- 이 머신은 포트 5432에 네이티브 PostgreSQL 17 서비스가 떠 있음. Docker Postgres는 5433 포트를 사용하도록 맞춰놨음.
- 로컬 실행/테스트 전 `DB_URL`, `YOUTUBE_API_KEY` 환경변수 필요. 리포 루트의 `.env`(gitignore됨)에 넣어뒀음:
  `export $(grep -v '^#' .env | xargs)` 로 셸에 로드하거나 IDE 실행 설정에 동일하게 등록.

### 이번 세션 추가 (2026-09-14, 후속) — 재생 흐름(큐 상태 전환) 구현
- 로드맵 순서를 바꿔서 검색(2주차, YouTube search.list는 하루 최대 100회 쿼터라 캐시 없이도 급하지 않음)보다
  재생 흐름(원래 3주차 일부)을 먼저 구현하기로 결정. WebSocket은 나중으로 미루고 우선 REST 폴링 기반으로 구현.
- `QueueItem`에 `play()`/`finish()` 추가(`skip()`은 기존에 이미 있었음, 미사용 상태였음).
- `QueueService`: 큐 등록 시 매장에 재생중인 곡이 없으면 즉시 `PLAYING`으로 등록(자동 시작).
  `completeCurrent`(곡 종료 통보 → 다음 대기곡 승격), `skip`(스킵 → 재생중이었으면 다음 곡 승격) 추가.
  한 매장에 `PLAYING`은 최대 1개라는 불변식을 서비스 계층에서 보장.
- `QueueController`: `GET /now-playing`, `POST /queue-items/{id}/complete`, `POST /queue-items/{id}/skip` 추가.
  베이스 경로를 `/api/tables/{tableToken}`으로 올리고 기존 두 엔드포인트는 `/queue-items` 하위로 이동(URL은 동일).
- `ErrorCode`에 `QUEUE_ITEM_NOT_FOUND`(404), `QUEUE_ITEM_NOT_PLAYING`(409), `QUEUE_ITEM_ALREADY_FINISHED`(409) 추가.
- `QueueServiceTest` 신규 작성(13케이스, Mockito). 전체 테스트 24개 통과.
- 실제 서버 띄워서 등록→자동재생→종료→다음곡 승격→스킵→큐 소진까지 curl로 엔드투엔드 검증 완료.
- `docs/API.md`에 신규 엔드포인트 3개 반영.

### 이번 세션 추가 (2026-09-14, 후속2) — 폴백 재생목록 구현
- 요구사항: 신청곡이 없으면(오픈 직후/큐 소진) 매장이 미리 등록한 폴백 재생목록에서 셔플로 재생.
  신청곡이 들어와도 **현재 재생중인 곡(폴백이든 신청곡이든)이 끝날 때까지는 끊지 않음**.
  신청곡이 다 끝나면 다시 폴백으로 복귀. (단골손님 개인 플레이리스트는 "추후" — 이번엔 구현 안 함,
  폴백 재생목록과 억지로 같은 추상화로 묶지 않음 — YAGNI)
- `V2__fallback_playlist.sql`: `fallback_tracks` 테이블 신설 + `stores`에 `current_fallback_video_id`/
  `title`/`thumbnail_url` 컬럼 추가. **신청곡과 달리 폴백 트랙은 `queue_items`에 행을 만들지 않고,
  "지금 재생중인 폴백"은 매장에 붙은 이 상태값 하나로만 추적**(그래야 `now-playing`을 반복 폴링해도
  같은 곡이 유지됨).
- `com.gokchu.gokchu.fallback` 패키지 신설: `FallbackTrack`, `FallbackTrackRepository`,
  `FallbackTrackService`(+Controller, DTO) — 큐 등록과 동일하게 `VideoValidationService`로
  embeddable/region 검증 후 저장. `POST/GET /api/tables/{tableToken}/fallback-tracks`.
- `Store`에 `hasFallbackPlaying()`/`startFallback(track)`/`clearFallback()` 추가.
- `QueueService` 재설계:
  - `getNowPlaying`: PLAYING 신청곡 > 이미 재생중인 폴백(있으면 그대로 유지, idempotent) > 폴백
    새로 시작 > (폴백 트랙도 없으면) 빈값. **이제 `@Transactional`**(폴백을 lazy하게 시작시킬 수 있어서 readOnly 아님).
  - `completeCurrent(tableToken, id)` → **`completeNowPlaying(tableToken)`로 시그니처 변경, id 안 받음**.
    서버가 "지금 뭐가 재생중인지" 자체를 알고 있으므로 TV는 "끝났다"고만 통보. 신청곡 종료 → 대기곡
    있으면 승격, 없으면 폴백 시작(직전에 끝난 게 폴백이었다면 그 곡은 후보에서 제외해 연속재생 방지).
  - `skip`은 그대로 id 기반 유지(특정 신청곡을 지목해서 스킵하는 거라 폴백과 성격이 다름), 내부적으로
    큐 소진 시 폴백 시작 로직 재사용.
  - 응답 타입을 `QueueItemResponse`에서 **`NowPlayingResponse`**(`source: QUEUE|FALLBACK`,
    `queueItemId`는 FALLBACK일 때 null)로 통일.
- 컨트롤러: `POST /queue-items/{id}/complete` **제거**, `POST /now-playing/complete`(id 없음)로 대체.
  `GET /now-playing` 응답 타입도 `NowPlayingResponse`로 변경.
- `DevDataInitializer`에 데모 매장용 폴백 트랙 2개 시드 추가(신규 스토어 생성 시에만).
- 테스트: `QueueServiceTest` 전면 재작성(폴백 시나리오 포함 18케이스), `FallbackTrackServiceTest` 신규(3케이스).
  전체 33개 테스트 통과.
- 실제 서버로 전체 흐름 curl 검증: 폴백 자동시작 → 폴링 idempotent 확인 → 폴백 재생중 신청곡 등록해도
  안 끊김 확인 → 폴백 종료 통보 → 신청곡으로 전환 → 신청곡 종료 통보 → 다른 폴백 트랙으로 복귀. 전부 의도대로 동작.
- `docs/API.md`에 재생/폴백 섹션 전면 갱신.

### 다음 세션 시작 시 순서
1. 프론트엔드(React+Vite) 착수 — 손님용(링크 신청 + 큐 조회)과 TV용(now-playing 폴링 + IFrame Player +
   ENDED 시 `now-playing/complete` 호출) 화면 우선. 검색 UI, 관리자 화면(폴백 트랙 관리 포함)은
   백엔드 보호가 없는 상태라 스켈레톤만 두거나 최소 폼으로.
2. 이후 2주차(YouTube 검색 연동, DB 기반 캐시) — Redis는 4주차 신청 제한 카운터 시점까지 보류.
3. 단골손님 개인 플레이리스트는 스키마조차 아직 안 정함 — 실제로 붙일 때 폴백 재생목록과 관계를
   어떻게 가져갈지(완전 별개 테이블 vs 공통 "재생목록" 개념) 다시 논의 필요.

## 진행 상황 (2026-09-11 기준)

### 이번 세션 (2026-09-11) — 1주차 검증 착수, 중단
- 로컬 Docker 데몬 켜고 `docker-compose up -d`로 Postgres 기동 완료
- `./gradlew test` 실행 → `GokchuApplicationTests#contextLoads()` 실패
  - 원인: `gokchu_pgdata` 볼륨이 이전에 이미 초기화되어 있어서, `docker-compose.yml`의
    `POSTGRES_PASSWORD` 환경변수가 반영되지 않고 볼륨에 저장된 예전 비밀번호로
    인증 시도 → `password authentication failed for user "gokchu"`
  - **다음 세션에서 먼저 할 일**: 이 볼륨을 `docker-compose down -v`로 삭제하고
    재생성할지(데모 데이터는 `DevDataInitializer`가 재기동 시 자동 재생성됨, 안전),
    아니면 볼륨에 남아있는 기존 비밀번호를 찾아 설정을 맞출지 결정 필요.
    사용자 의견: 재생성 쪽으로 기울어 있었으나 최종 확정 전 세션 종료.
- `YoutubeLinkParserTest`는 이 세션에서 재확인 안 함 (지난 세션에 10건 통과 확인됨)
- YouTube Data API 키: **아직 발급 안 됨**. 다음 세션에서 Google Cloud Console
  발급 절차 안내부터 시작할 것 (2주차 검색 연동 + 1주차 videos.list 실제 호출 테스트에 필요)
- Redis 로컬 실행 방식: 아직 결정 보류 유지 (사용자가 나중에 정하겠다고 확인)

### 다음 세션 시작 시 순서
1. Postgres 볼륨 인증 문제 해결 (위 내용 참고, 재생성이 유력)
2. `./gradlew test`로 `GokchuApplicationTests` 통과 확인 (1주차 최종 검증 마무리)
3. YouTube Data API 키 발급 안내 및 로컬 환경변수(`YOUTUBE_API_KEY`) 설정
4. 키 발급 후 `videos.list` 실제 호출 스모크 테스트
5. 이후 2주차(검색 연동 + Redis 캐싱) 착수

## 진행 상황 (2026-08-30 기준)

### 1주차 — 완료
도메인/DB 설계 + 링크 붙여넣기 MVP 백엔드 구현 완료.
- 엔티티: `Store`, `TableToken`(UUID), `QueueItem`(WAITING/PLAYING/DONE/SKIPPED), `CachedVideo`(embeddable + allowed/blocked region) + Repository
- `V1__init.sql` Flyway 마이그레이션, `ddl-auto: validate`
- 공통 예외 처리: `ErrorCode`/`GokchuException`/`GlobalExceptionHandler`
- YouTube 연동: `YoutubeLinkParser`(링크/videoId 파싱), `YoutubeApiClient`(`videos.list`), `VideoValidationService`(캐시 우선 조회 → 미스 시에만 API 호출 → embeddable/regionRestriction 검증)
- API: `POST/GET /api/tables/{tableToken}/queue-items`
- 로컬 개발환경: `docker-compose.yml`(Postgres), `DevDataInitializer`(local 프로필 데모 매장/테이블 생성)
- 검증: 컴파일 성공, `YoutubeLinkParserTest` 10건 통과. **`GokchuApplicationTests`(전체 컨텍스트 로딩, DB 연동)는 로컬 Docker 데몬이 꺼져 있어서 미실행 — 다음 세션에서 Docker 켜고 먼저 확인할 것**
- 검색/Redis/WebSocket/신청제한/관리자/`relatedToVideoId`는 의도적으로 미구현 (로드맵상 2~4주차)

### 다음 착수: 2주차 (YouTube 검색 연동 + Redis 캐싱)
시작 전 확인 필요:
1. YouTube Data API 키 발급 여부 (1주차 videos.list 실제 호출도 이 키 없으면 테스트 불가)
2. 로컬 Docker 데몬 켜서 `docker-compose up -d`로 Postgres 통합 테스트 먼저 돌려보기
3. Redis 로컬 실행 방식 결정 (docker-compose에 추가 vs 별도 설치)

### 아직 결정 안 된 것
- Oracle Cloud 가입 시점에 Always Free 스펙 재확인 필요 (정책 변경 이슈, 4주차 배포 직전에 확인)
- React+Vite 프론트엔드 착수 시점 — 백엔드 주차별 진행과 병행할지, 나중에 몰아서 할지 미정