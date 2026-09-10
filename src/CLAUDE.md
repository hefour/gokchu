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