# URL 단축기 학습 (가상면접 사례로 배우는 대규모 시스템 설계 기초 8장)

> **다른 컴퓨터에서 이 학습 이어가는 법**: Claude Code에서 한 줄 입력
> ```
> @shorten-url.md @docs/architecture-phase2-3.md 읽고 이 학습 이어서 도와줘
> ```

---

## 학습 메타정보

- **시작일**: 2026-04-23
- **최근 진행일**: 2026-04-28
- **학습자**: 1년 5개월 차 주니어 백엔드 개발자 (Java)
- **교사 역할**: Facebook 스태프 엔지니어 (소크라틱 멘토링 + 빅테크 면접 스타일)
- **교재**: 가상 면접 사례로 배우는 대규모 시스템 설계 기초 1권 8장 (URL 단축기)
- **학습 목표**:
    - 책의 결론을 외우는 게 아니라, 그 결론에 도달하는 사고 과정 익히기
    - 시스템 디자인 설계 역량 기초 다지기

## 교육 방식 (이 방식 유지할 것)

- **빅테크 면접 스타일**: 환경 제약 시나리오 던지기 → 학습자가 그 안에서 설계 풀어내기. 알고리즘 디테일(해시 함수 선택, salt 전략 등) 딥다이브는 학습자가 명시적으로 거부함.
- 코드 직접 작성해주지 말 것. "질문 → 학습자 답 → 교정/심화 질문"의 소크라틱 사이클.
- 학습자가 먼저 설계 → 책의 정답과 비교 → 격차에서 학습.
- 직감은 칭찬, 부정확함은 정량화/구체화로 압박. "왜?" "얼마나?" 항상 묻기.
- 한 번에 너무 많이 던지지 말 것. 미답 질문 받고 다음 단계로.

## 사용자 컨텍스트

- 주니어 2년차 백엔드, DB를 Java로 직접 구현하며 학습 중 (`self-made` 프로젝트)
- 회사 프로젝트는 `mm-broker` (별개)
- 코드 대신 짜주지 말고 질문/가이드로 학습시킬 것

---

## 학습 진행 단계

### Phase 1: 기본 설계 (Round 1-2)

학습자 초기 설계 → 문제점 도출:
- PK 없음 / 인덱스 명시 없음 / 리다이렉트 플로우 누락 / "연산"이 블랙박스 / 동시성 문제

다룬 주제:
- **읽기/쓰기 비율**: 100:1 (read-heavy)
- **인덱스 trade-off**: 인덱스 ↑ → 읽기 ↑, 쓰기 ↓ (B-tree 재균형 비용)
- **단축 코드 생성 3가지 방식 비교**:
  - (A) `hash(URL)` 앞 7자 → 100% 충돌 (Birthday Paradox로 1억 URL이면 ~1420 충돌 쌍)
  - (B) auto_increment + base62 → enumeration attack 위험
  - (C) 랜덤 7자 → 충돌 시 재시도 가능 (해시보다 나음 — 결정적 vs 비결정적 차이)
- **충돌 처리**: salt 추가 (`hash(URL + attempt_number)`)

### Phase 2: 표준 아키텍처 (Redis 포함, 302)

상세 다이어그램: `docs/architecture-phase2-3.md` 참조.

**핵심 결정**:
- **302 채택** → 모든 클릭이 App 서버 거침 → 실시간 click count 가능
- **Cache-Aside 패턴**: 앱이 Redis 먼저 조회 → miss면 DB SELECT + Redis populate
- **LRU + TTL**: 인기 URL이 자연스럽게 캐시에 남음 (Pareto 80/20)
- **Multi-server 정합성**: 모든 App 인스턴스가 같은 Redis 공유 → 문제 없음

**3가지 흐름** (꼭 분리해서 이해):
1. **쓰기** (POST /shorten): App → DB INSERT
2. **읽기** (GET /:shortCode): Redis 조회 → (miss → DB SELECT + populate) → 302 + Location
3. **클릭 카운트**: DB UPDATE 또는 Redis INCR + sync 또는 Kafka 이벤트

**Phase 2의 실무 약점 (ROI 순)**:
1. 글로벌 latency (한 region에 몰림) → CDN 도입
2. Cache stampede (Redis cold/재시작 시 DB 폭주) → request coalescing, warming
3. Hot key (인기 URL의 click_count UPDATE 경합) → Redis INCR + batch sync
4. Abuse / Rate limiting (단축 URL은 phishing 핫스팟) → rate limit + 도메인 필터
5. DB connection pool 고갈 → pool sizing + circuit breaker
6. Observability 부재 → 메트릭/트레이싱/알림

### Phase 3: 제약 시나리오 #1 — Redis 없이 (CDN + 브라우저, 301)

상세 다이어그램: `docs/architecture-phase2-3.md` 참조.

**제약**: Redis/Memcached 같은 별도 캐시 인프라 사용 불가. 캐시는 브라우저 + CDN만.

**핵심 변경 (vs Phase 2)**:
- **302 → 301** (CDN 캐싱이 작동하려면 필수)
- 캐시 위치: Redis → CDN 엣지 (지역별 자연 분산)
- 분석: 실시간 → 배치 (CDN access log → S3 → Spark/BigQuery → 분석 DB → Dashboard)
- TTL: 24시간 + CDN Purge API (takedown 즉시 무효화)
- Origin DB에는 click_count 컬럼 없음

**Phase 2 vs 3의 본질적 차이** = **redirect 코드 결정 (302 vs 301)**. 그게 모든 걸 도미노로 결정:
- 302 선택 → CDN 캐싱 못함 (HTTP 302는 비캐싱) → Redis 필수 → 모든 클릭 우리 서버 거침 → 실시간 분석 가능
- 301 선택 → CDN 캐싱 가능 → Redis 불필요 → 클릭이 우리 서버 안 거침 → 분석은 배치

→ "Phase 2 = Redis, Phase 3 = no Redis"는 **결과론적** 표현. 본질은 redirect 코드 결정.

### Phase 4: 제약 시나리오 #2 — DC 무중단 ← **현재 진행 중**

**배경**: "DC 1개 통째로 죽어도 무중단" 요구.

**다룬 개념**:
- **CAP Theorem**: C(Consistency) / A(Availability) / P(Partition tolerance) 셋 다 동시 만족 불가 (Brewer 정리). DC 죽음 = 큰 P → C/A 중 하나 포기 강제.
- Stateless vs Stateful 복제 difficulty
- **Split-brain 문제**: A↔B 네트워크 끊겼는데 둘 다 살아있을 때 → 둘 다 master 행세 → 데이터 갈라짐. 해결: leader election (Raft, Paxos)
- 도메인별 답이 다름 (은행=정합성 / SNS=가용성 / URL 단축=?)

**학습자의 등급 매김** (CAP/PACELC와 일치):
- (a) Click redirect = **A등급** (1초도 끊기면 안 됨, Availability 우선)
- (b) URL 생성 = **B등급** (수초~수분 끊김 OK + 데이터 손실 안 됨, Consistency 우선)
- (c) 분석 = **C등급** (일시적 손실/지연 OK, best-effort)

**3 옵션 비교 → Option 1 채택**:
- Option 1: **Single-master + Read replica** ← 채택 ★★★
- Option 2: Multi-master (충돌 처리 복잡, 도메인 대비 과함)
- Option 3: Single-region (failover 시 다운타임 — DNS propagation 5분~수시간, A등급 만족 X)

**왜 Option 1?**:
- (a) read replica 살아있어 무중단 read OK
- (b) auto-failover로 B등급 만족
- 도메인 특성(read-heavy 100:1 + write-once)에 best fit
- 업계 실제(bit.ly 등)도 이 패턴

### Phase 4 — 다음 결정 ★ **여기서부터 이어가면 됨**

**Q. 서울 master → 도쿄 replica 복제 방식: 동기(sync) vs 비동기(async)?**

| 방식 | 작동 | 장점 | 단점 |
|---|---|---|---|
| **동기** | master가 replica 응답 받은 후 사용자에게 OK | 데이터 손실 0 | 쓰기 느림 (cross-DC RTT). replica 다운 시 master도 멈춤 |
| **비동기** | master가 자기 쓰기 끝내자마자 OK, replica 백그라운드 | 빠름. replica 장애 무관 | master 죽으면 lag 만큼 데이터 손실 |

힌트:
- 학습자 (b) 등급 = "수초~수분 끊김 OK + 데이터 손실 안 됨"
- 동기 vs 비동기, 학습자 등급에 어느 게 맞는가?
- 동기의 단점("쓰기 느림")이 (b)의 또 다른 측면에 어떤 영향?
- 트레이드오프 — 한쪽 100% 만족 어려움. 어디서 타협?

**Phase 4 이후 다룰 내용**:
- 동기/비동기 trade-off + 절충안 (semi-sync replication, write-ahead log)
- Failover 자동화 (자동 vs 수동, 임계값)
- 트래픽 라우팅 (DNS / Anycast / GSLB)
- Split-brain 방지 (합의 메커니즘)
- Failback (살아난 DC에 데이터 재동기화)

---

## 다음 제약 시나리오 후보 (Phase 4 끝나면)

학습자 추천 진행 순서 (역량 측면):
- **3 (DC 무중단) ← 현재**
- 6 (보안 + 충돌 회피) — Round 2 미해결 마무리, enumeration attack + 0 충돌 동시 만족
- 4 (100ms 글로벌 응답) — latency budgeting, edge computing
- 5 (4자 코드 — 정량 사고) — Birthday Paradox 직접 계산
- 2 (단일 DB 최적화) — 인덱스/파티셔닝/read replica
- 1 (단일 서버 1억 URL) — 자원 제약 사고

---

## 진행 시 주의사항

- 학습자가 직접 답 쓰게 한 후 교정 (코드 대신 짜주지 말 것)
- 한 번에 너무 많은 개념 던지지 말 것
- 영어 용어는 한국어 설명과 병기
- 학습자의 **직감**은 칭찬, **부정확함**은 정량화로 잡기
- **빅테크 면접 스타일 (제약 시나리오 → 학습자 설계)** — 알고리즘 디테일 딥다이브는 학습자가 거부함
- 진도 끝나면 이 파일도 업데이트 권장 (덮어쓰기 OK)
