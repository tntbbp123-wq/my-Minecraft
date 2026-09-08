# MyMinecraft

Paper 서버용 유틸리티 플러그인입니다. (대상: Paper 1.21.x, Java 21)

## 기능

- `/tpa <player>`, `/tpaccept`, `/tpdeny` — 플레이어 간 텔레포트 요청
- `/home` — 저장된 홈을 GUI로 보여주고 클릭 이동(좌클릭)/삭제(우클릭)
- `/sethome <name>`, `/delhome <name>` — 홈 저장/삭제 (기본 최대 5개, `config.yml`에서 조정)
- `/ec` — 어디서든 엔더상자 열기
- `/menu` — 메인 메뉴 GUI (스폰 / 엔더상자 / 주식 / 랜덤 TP / 대장간 강화)
- `/lobby`, `/lobby set` — 로비 이동 / 설정(관리자)
- `/spawn`, `/spawn set` — 스폰 이동 / 설정(관리자)
- `/rt` — 월드 스폰 기준 중심 500x500 지역을 제외한 랜덤 위치로 이동 (쿨다운 존재)
- 주식(모의 투자) — 메뉴에서 가상 종목을 내부 포인트로 매수/매도, 주기적으로 가격 변동
- 대장간 강화 — 전용 아이템 **강화석**을 소모해 아이템을 강화(성공률은 레벨이 오를수록 감소),
  **확률 강화 두루마리**를 함께 넣으면 해당 시도의 성공 확률이 일시적으로 증가
- `/enhanceitem <stone|scroll> <player> [amount]` — 관리자가 강화석/확률 강화 두루마리 지급 (기본 op 권한)

## 빌드

```
mvn clean package
```

`target/MyMinecraft-1.0.0.jar` 를 서버의 `plugins/` 폴더에 넣으면 됩니다.

## 설정

`config.yml`에서 경제 시작 포인트, 홈 최대 개수, tpa 타임아웃, 랜덤tp 반경/제외 크기/쿨다운,
주식 종목 목록 및 변동성, 강화 재료/성공확률/비용 등을 조정할 수 있습니다.

관리자 권한(`myminecraft.admin`, 기본 op)이 있는 플레이어만 `/spawn set`, `/lobby set`을
사용할 수 있습니다.
