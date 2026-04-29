# 발표용 Excalidraw 파일

## 사용법

1. https://excalidraw.com 접속 (가입 불필요)
2. 좌상단 햄버거 메뉴 → **Open** (또는 Ctrl+O)
3. 이 폴더의 `.excalidraw` 파일 선택
4. 편집 후 **Export Image → PNG**로 슬라이드에 붙여넣기

## 파일 목록

| 파일 | 용도 | 추천 발표 위치 |
|------|------|----------------|
| `1-hash-ring.excalidraw` | 해시 링 개념도 | 슬라이드 3 (핵심 아이디어) |
| `2-rebalance-chart.excalidraw` | Simple vs Consistent 재배치율 | 슬라이드 2 (문제 제시 + 증명) |
| `3-virtual-node-chart.excalidraw` | 가상노드 개수별 표준편차 | 슬라이드 4 (가상노드 설명) |
| `4-cachemiss-chart.excalidraw` | 재배치율 vs 실제 캐시 미스율 (ADD) | 슬라이드 5 (심화 통찰) |
| `5-node-removal-chart.excalidraw` | 노드 제거 시 미스 건수 (REMOVE) | 슬라이드 6 (장애 시나리오) |

## 수정 팁

- **색 바꾸기**: 도형 클릭 → 왼쪽 패널에서 stroke/background 색 변경
- **글씨 키우기**: 텍스트 더블클릭 → 폰트 크기 조정
- **배치 미세조정**: Shift+드래그로 정교한 이동
- **로컬에 저장**: 메뉴 → Save to... → 이 폴더에 덮어쓰기
- **PNG 내보내기**: 메뉴 → Export image → PNG, 배경 투명 원하면 체크박스

## 발표 슬라이드 매핑

```
슬라이드 1 — 표지
슬라이드 2 — "왜 modulo가 망하는가"  → 2-rebalance-chart.png
슬라이드 3 — "해시 링의 아이디어"     → 1-hash-ring.png + 핵심 코드 5줄
슬라이드 4 — "가상 노드의 효과"       → 3-virtual-node-chart.png
슬라이드 5 — "실무 맥락"              → Redis/Discord/Hot key 얘기
슬라이드 6 — "배운 것 3가지"          → 마무리
```
