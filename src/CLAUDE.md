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

## 진행 상황 (2026-09-21 기준)

### 이번 세션 — 폴백 트랙 스킵 API 추가
- 배경: 관리자가 신청곡은 `skip`으로 건너뛸 수 있는데, 큐가 비어서 폴백(배경음악)이 재생중일
  땐 건너뛸 방법이 아예 없었음(관리자 페이지 "다음 곡 재생" 버튼도 큐에 `PLAYING` 항목이 없으면
  비활성화). TV의 `onError` 자동 스킵은 `completeNowPlaying`으로 우회 처리돼 있었지만, 그건
  "관리자가 지금 나오는 배경음악이 마음에 안 들어서 수동으로 바꾸고 싶다"는 케이스를 못 채움.
- `QueueService.skipFallback(tableToken)` 신규 — 지금 폴백이 재생중이 아니면(신청곡이 재생중이거나
  아무것도 없으면) `FALLBACK_NOT_PLAYING`(409) 예외. 재생중이면 방금 트랙을 제외하고 무작위로
  다음 폴백을 고름(`completeNowPlaying`의 폴백 전환 로직과 동일한 `startFallback` 재사용).
- `QueueController`: `POST /api/tables/{tableToken}/now-playing/skip-fallback` 추가, 관리자
  인증 필요(`WebConfig` 인터셉터 목록에 경로 추가). `ErrorCode.FALLBACK_NOT_PLAYING` 신규.
- 테스트 4건 추가(`QueueServiceTest`) — 정상 스킵, 트랙 1개뿐일 때 재생, 신청곡 재생중 예외,
  아무것도 재생중 아닐 때 예외. 전체 스위트 통과(`GokchuApplicationTests`는 로컬 Docker Postgres
  안 띄워서 이번에도 미실행 — 기존부터 있던 환경 제약, 이 변경과 무관).
- 프론트: `AdminPage`가 이제 `now-playing`도 폴링해서, 폴백 재생중일 땐 헤더 버튼과 별도 카드로
  "폴백 재생 중 (배경음악)" + 스킵 버튼을 보여줌. `api.ts`에 `skipFallbackTrack` 추가.
- `docs/API.md`에 새 엔드포인트/에러코드 반영.

## 진행 상황 (2026-09-15 기준)

### 이번 세션 — 관리자 인증 + 클라우드 배포 준비 착수
- 배경: 프론트 폴백 재생목록 관리 UI까지 만들고 나니, `tableToken`이 손님용 QR과 관리자
  액션(스킵, 폴백관리)에 동시에 쓰여서 **QR 스캔한 손님 누구나 관리자 기능을 쓸 수 있는 구멍**이
  발견됨. AWS EC2 배포를 앞두고 이걸 먼저 막기로 함.
- `com.gokchu.gokchu.admin` 패키지 신설:
  - `AdminTokenService` — HMAC-SHA256으로 서명한 stateless 토큰 발급/검증 (`만료시각.서명` 형태,
    DB 저장 없음, 기본 12시간 유효). 시크릿/만료시간은 `ADMIN_TOKEN_SECRET`/`ADMIN_TOKEN_TTL_SECONDS`.
  - `AdminAuthController` — `POST /api/admin/login`, `ADMIN_PASSWORD` 환경변수와 상수시간 비교.
  - `AdminAuthInterceptor` + `WebConfig.addInterceptors` — `/queue-items/*/skip`,
    `/fallback-tracks`(전체)를 관리자 토큰 없으면 401(`UNAUTHORIZED_ADMIN`)로 막음.
- **중요한 버그를 구현 중 발견**: TV의 `onError`(재생 불가 영상 자동 스킵)가 기존 `skip`
  엔드포인트를 썼는데, 그걸 관리자 전용으로 막아버리면 TV가 자기 화면도 못 넘김 — TV는 자격증명이
  없는 공용 기기라서. `POST /queue-items/{id}/report-unplayable`(공개, `skip`과 로직 동일)을
  새로 만들어서 분리. 관리자 수동 스킵 = `skip`(인증 필요), TV 자동 스킵 = `report-unplayable`(공개).
- Spring 생성자 주입 관련 삽질: `AdminTokenService`에 프로덕션용 생성자(`@Value` 2개)와
  테스트용 생성자(secret/ttl/Clock 직접 주입, package-private)를 같이 뒀더니 Spring이 어느 걸
  쓸지 못 정해서 `NoSuchMethodException: <init>()`으로 컨텍스트 로딩이 깨짐 — 프로덕션 생성자에
  `@Autowired` 명시해서 해결. **여러 생성자 있는 빈은 반드시 하나에 `@Autowired` 명시할 것**.
- 테스트 8건 추가(`AdminTokenServiceTest` 6, `AdminAuthControllerTest` 2, `AdminAuthInterceptorTest` 3
  — 실제론 파일 3개 총 11개). 전체 48개 테스트 통과.
- 로컬 `.env`에 랜덤 `ADMIN_PASSWORD`/`ADMIN_TOKEN_SECRET` 생성해서 추가, `.env.example` 신설(리포에
  커밋되는 건 이쪽, 실제 값 없는 템플릿).
- 실 서버로 curl 검증: 미인증 스킵/폴백조회 401 → 로그인 → 토큰으로 재시도 200/204, TV의
  `report-unplayable`은 인증 없이 204 — 전부 의도대로.
- 프론트: `lib/adminAuth.ts`(세션스토리지 토큰), `AdminLogin` 컴포넌트, `AdminPage`가 토큰 없으면
  로그인 화면을 먼저 보여주도록 게이팅. `skipQueueItem`/`addFallbackTrack`/`deleteFallbackTrack`/
  `fetchFallbackTracks`는 `adminToken` 파라미터 필수로 시그니처 변경, `TvPage`는 새로 만든
  `reportUnplayableQueueItem`(공개)로 교체. `api.ts`의 `request()` 헤더 병합 버그도 같이 고침
  (`{headers: {...}, ...options}`면 `options.headers`가 기본 헤더를 통째로 덮어써서 Content-Type이
  날아가는 문제 — `{...options, headers: {기본값, ...options?.headers}}` 순서로 수정).
- **배포 방향 확정**: Oracle Cloud 대신 AWS EC2로 진행 (포트폴리오 가치 때문에 사용자가 직접 선택,
  Oracle은 국내 채용에서 거의 안 물어봄). 사용자의 AWS 계정은 2025년 정책 변경 이후 가입이라
  "12개월 무료 EC2 750시간" 구체계가 아니라 **크레딧 소진형 신체계** — 확인 시점 남은 크레딧
  $83.09, 이번 달 $13.77 예상 소진, 만료 2026-12-26. EC2 24시간 가동 얹으면 크레딧이 만료일보다
  먼저 바닥날 가능성 높음 — 그 이후엔 진짜 카드 청구됨. 배포 전에 Billing 콘솔 예산 알람 걸어두기 권장.
- 도메인은 없어서 무료 옵션(duckdns.org)으로 진행하기로 함.
- AWS 콘솔 작업(EC2 인스턴스, 보안그룹, Elastic IP, duckdns 연결)은 사용자가 직접 해야 함(자격증명
  없어서 대신 못 함) — 끝나면 퍼블릭 IP/도메인 받아서 SSH로 배포 진행 예정.

### 이번 세션 (2026-09-15, 후속) — 실제 배포 완료 + 발견된 문제들 + 최우선 3종 추가 구현

**배포 완료.** 백엔드 `https://gokchu.duckdns.org`(AWS EC2 t3.micro), 프론트
`https://gokchu-frontend.vercel.app`. 둘 다 실제로 붙어서 신청곡 등록→재생→관리자 로그인까지
엔드투엔드 확인됨.

**배포 중 겪은 문제 (다음에 또 배포할 때 참고)**
- SSH 세션 끊기면 원격 `docker compose up --build`도 같이 죽음 → `nohup ... &`로 세션과 분리해서 돌릴 것
- t3.micro 루트 디스크가 6.7GB뿐이라 서버에서 Gradle 빌드하면 디스크 꽉 참 → **로컬에서
  `./gradlew bootJar`로 jar 빌드 → scp로 전송 → 서버 Dockerfile은 그 jar만 COPY하는 단일 스테이지로
  전환**. 이후 배포는 항상 이 순서(로컬 빌드 → scp → `docker compose up -d --build`).
- 그래도 디스크 부족 → 2GB 스왑 제거(`swapoff && rm /swapfile`)해서 겨우 공간 확보. **디스크
  계속 빠듯함, 언젠가 EBS 볼륨 늘려야 함**
- Vercel에 React Router(BrowserRouter) 배포하면 `/guest` 같은 경로가 404 → `vercel.json`에
  전체 경로를 `/index.html`로 rewrite하는 규칙 추가해서 해결
- 사용자가 이동하면서 작업해서 IP가 계속 바뀜 → EC2 보안그룹 SSH(22) 규칙을 "내 IP" 대신
  0.0.0.0/0으로 열어둠(어차피 `.pem` 키 기반 인증이라 무차별 대입 위험 낮음, 비밀번호 로그인
  아님)
- 관리자 비밀번호를 `4820`(4자리)으로 사용자가 직접 선택 → 이번 세션에서 로그인 rate limit
  추가로 리스크 완화(아래)
- 랜딩 페이지가 없어서 TV/관리자 URL을 매번 외워야 하는 문제 → `HomePage` 추가,
  `VITE_DEFAULT_TABLE_TOKEN` 환경변수로 홈에서 버튼 클릭만으로 TV/관리자/손님 화면 이동

**이번에 추가 구현한 것 (우선순위 최상위 3개)**
1. **매장/테이블 생성 API** — `com.gokchu.gokchu.store` 패키지에 `StoreController`/`StoreService`
   신설. `POST /api/stores`, `POST /api/stores/{storeId}/tables`, `GET /api/stores/{storeId}/tables`,
   전부 관리자 인증 필요(`WebConfig` 인터셉터 패턴에 `/api/stores`, `/api/stores/*/tables` 추가).
   `TableTokenRepository.findByStoreId` 신규. 이제 SQL 직접 안 쳐도 테이블(QR용 토큰) 발급 가능.
2. **YouTube API 키 IP 제한** — Google Cloud Console에서 `gokchu-youtube-key`에 애플리케이션
   제한사항 "IP 주소" → `54.116.217.9`(EC2 Elastic IP) 추가. 적용까지 최대 5분 소요.
3. **관리자 로그인 rate limiting** — `AdminLoginRateLimiter`(인메모리, IP별 실패 횟수 추적,
   기본 15분에 5회, `ADMIN_LOGIN_MAX_ATTEMPTS`/`ADMIN_LOGIN_WINDOW_MINUTES`로 조정) 신설.
   `AdminAuthController.login()`에서 X-Forwarded-For 헤더로 클라이언트 IP 추출(Caddy 뒤에 있어서
   `getRemoteAddr()`만 쓰면 Caddy 컨테이너 IP가 나옴), 5회 실패 시 6번째부터 비밀번호 안 보고
   바로 429(`TOO_MANY_LOGIN_ATTEMPTS`). 인스턴스 재시작하면 카운트 초기화되는 한계는 있음
   (지금 규모에선 충분, 커지면 Redis로 옮길 것).
   실서버 curl로 6번째 시도에서 429 뜨는 것까지 확인함.
- 이 세 가지 모두 로컬 curl + 유닛테스트로 검증 완료(전체 테스트 스위트 통과). 아직 프로덕션에는
  재배포 안 함 — 다음에 할 일.

### 다음 세션 시작 시 순서
1. 로컬에서 `./gradlew bootJar` → jar를 서버로 scp → `docker compose -f docker-compose.prod.yml
   --env-file .env.prod up -d --build`로 재배포 (매장/테이블 API + rate limiting 반영)
2. 재배포 후 실제 매장/테이블을 API로 만들어서(더 이상 SQL 직접 안 쳐도 됨) 데모 데이터 정리
3. YouTube 키 IP 제한이 실제로 걸렸는지 재배포 후 `videos.list` 스모크 테스트로 재확인
4. 이후 순위: 2주차 검색 연동, EBS 볼륨 확장, 배포 자동화(지금은 매번 수동), 3~4주차 항목들

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