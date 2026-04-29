# URL 단축기 아키텍처 — Phase 2 & Phase 3

> URL 단축기 학습 (가상면접 사례로 배우는 대규모 시스템 설계 기초 8장) 진행 중 정리한 두 가지 아키텍처.
> 같은 문제(단축 URL 서비스)에 **다른 환경 제약**을 적용했을 때 설계가 어떻게 바뀌는지를 비교.

---

## Phase 2: 표준 아키텍처 (Redis 포함, 302)

**환경**: 모든 인프라 사용 가능 (Redis 등 캐시 서버 운용 가능)

```
   [Browser]
       │ GET /abc123
       ▼
   ┌────────────────┐
   │ Load Balancer  │
   └───────┬────────┘
           │
       ┌───┼───┬────┬────┐
       ▼   ▼   ▼    ▼    ▼
    ┌────┬────┬────┬────┬────┐
    │App1│App2│App3│ … │AppN│   ← 모든 클릭이 여기 거침
    └─┬──┴─┬──┴─┬──┴─┬──┴─┬──┘     → click count +1 (302 효과)
      │    │    │    │    │
      └────┴────┼────┴────┘
                ▼
        ┌────────────────┐  miss   ┌─────────────────┐
        │    Redis       │────────▶│   Origin DB     │
        │ (Cache-Aside)  │◀────────│  shortCode →    │
        │  LRU + TTL     │  fill   │  originalUrl    │
        └────────┬───────┘         │  + click_count  │
                 │ hit             └─────────────────┘
                 ▼
       응답: 302 Found
       Location: https://youtube.com/...
```

### 핵심 결정사항

- **302**: 모든 클릭이 App 서버 거침 → 실시간 click count 정확
- **Redis**: `shortCode → originalUrl` (key/value)
- **정책**: Cache-Aside + LRU eviction + TTL
- **Pareto**: 인기 URL이 자연스럽게 캐시에 남음 (hit rate 80~99%)
- **Multi-server 정합성**: 모든 App이 같은 Redis 봄 → 문제 없음

### Phase 2의 3가지 흐름 (꼭 분리해서 이해)

#### Flow 1: 단축 URL 생성 — `POST /shorten` (쓰기)

```
1. 사용자: POST /shorten { url: "youtube.com/..." }
2. App: 단축 코드 생성 (hash/random/auto_increment+base62)
        → DB INSERT (shortCode, originalUrl)
3. 응답: { shortUrl: "bit.ly/abc123" }
```

#### Flow 2: 리다이렉트 — `GET /:shortCode` (읽기)

```
1. 브라우저: GET /abc123  → 로드밸런서 → App 서버
2. App:
   a) Redis 조회 (GET abc123)
   b) cache HIT: originalUrl 바로 응답, DB 안 감
   c) cache MISS: DB SELECT → 응답 + Redis에 populate
3. 응답: 302 Found, Location: youtube.com/...
4. 브라우저: Location URL로 자동 이동
```

> ⚠️ cache miss 시 동작은 **SELECT (조회)** 이지 UPDATE가 아님.
> 매핑 데이터는 변경되지 않음 (write-once).

#### Flow 3: 클릭 카운트 (분석)

302 효과로 모든 클릭이 App을 거침 → 정확한 카운트 가능.

```
매 redirect 시 click_count +1. 저장 방식 (셋 중 하나):
  A. DB UPDATE click_count = click_count + 1   ← 단순, 느림
  B. Redis INCR + 주기적 DB sync               ← 빠름, 실무 흔함
  C. Kafka 큐 publish → 분석 파이프라인        ← 가장 확장적
```

본 다이어그램은 A 가정. B/C는 실무 변형.

> ⚠️ "Redis에 +1"은 **Flow 3 (클릭 카운트)** 의 옵션 B에 해당.
> Flow 2 (매핑 조회) 와는 별개 흐름.

---

## Phase 3: 제약 시나리오 — Redis 없이 (CDN + 브라우저, 301)

**환경 제약**: Redis/Memcached 같은 별도 캐시 인프라 사용 불가. 캐시 자원은 브라우저 + CDN만.

```
   [Browser]
       │ GET /abc123
       ▼
   ┌──────────────────────┐         (모든 요청, 비동기)
   │    CDN Edge          │─────────────────────┐
   │  (Cloudflare etc.)   │                     │
   │  TTL: 24h            │                     ▼
   │  Purge API ◀──admin  │             ┌──────────────┐
   └──┬──────────────┬────┘             │  Access Log  │
      │ hit          │ miss             │  (S3 / GCS)  │
      │              │                  └───────┬──────┘
      │              ▼                          │ (수분 단위 flush)
      │       ┌──────────────┐                  ▼
      │       │ Origin       │           ┌──────────────────┐
      │       │ Server       │           │ Batch Analysis   │
      │       └──────┬───────┘           │ (Spark/BigQuery) │
      │              ▼                   └──────┬───────────┘
      │       ┌──────────────┐                  │ (시간/일 단위)
      │       │  Origin DB   │                  ▼
      │       │  (매핑만,    │           ┌──────────────────┐
      │       │  count X)    │           │ Analytics DB     │
      │       └──────────────┘           │ (Redshift,       │
      │                                  │  ClickHouse 등)  │
      ▼                                  └──────┬───────────┘
   응답: 301 Moved Permanently                  │
   Location: https://youtube.com/...            ▼
                                          ┌──────────────┐
                                          │  Dashboard   │
                                          │  (마케터)     │
                                          └──────────────┘
```

### 핵심 변경 (Phase 2 대비)

- **302 → 301**: CDN 캐싱이 작동하려면 캐싱 가능한 응답 코드 필요
- **캐시 위치**: Redis → CDN 엣지 (지역별 자연 분산)
- **분석 방식**: 실시간(App 카운트) → 배치(CDN 로그 → 분석 DB)
- **Origin DB**: click_count 컬럼 없음. 분석 DB에만 존재
- **Takedown**: DB delete + CDN Purge API 호출 (비동기 + DLQ 패턴)
- **Hit rate 결정**: CDN 엣지의 LRU + Pareto (Phase 2의 Redis 역할 대체)

---

## 두 Phase의 핵심 차이 비교

| 항목 | Phase 2 (Redis) | Phase 3 (Redis 없이) |
|---|---|---|
| Redirect 코드 | 302 | 301 |
| 캐시 위치 | Redis (앱 서버 뒤) | CDN 엣지 |
| 클릭이 거치는 경로 | App 서버 (모든 요청) | CDN (대부분), Origin (cache miss만) |
| Click count 방식 | 실시간 (App에서) | 배치 (CDN 로그 → 분석 DB) |
| 분석 지연 | 즉시 | 수분~수시간 |
| Origin DB 부담 | 캐시 hit 시 0 | 캐시 hit 시 0 (CDN miss만 도달) |
| Cache invalidation | TTL + Redis DELETE | TTL + CDN Purge API |
| Multi-region 자연 분산 | ✗ (Redis 한 곳) | ✓ (CDN 엣지 자연 분산) |

---

## 학습 핵심 — 같은 문제, 다른 설계

같은 "URL 단축기"라도:
- **인프라 제약**이 어디 있느냐에 따라
- **trade-off의 위치**가 완전히 달라짐

Phase 2는 **실시간 분석**을 얻고 **별도 캐시 인프라 운영 부담**을 짐.
Phase 3은 **인프라 단순함**을 얻고 **분석 지연**을 받아들임.

→ 시스템 디자인은 "정답"이 아니라 "**제약 하의 최적 trade-off**"를 찾는 것.
