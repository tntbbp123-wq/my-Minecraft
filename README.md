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
- 대장간 강화 — 전용 아이템 **강화석**을 소모해 아이템을 강화(성공률은 레벨이 오를수록 감소).
  **확률 강화 두루마리**(등급별)를 함께 넣으면 해당 시도의 성공 확률이 일시적으로 증가:
  일반 +10% / 레어 +20% / 에픽 +30% / 레전더리 +45% (`config.yml`의 `enhance.scrolls`에서 등급 추가/조정 가능)
- `/enhanceitem stone <player> [amount]`,
  `/enhanceitem scroll:<common|rare|epic|legendary> <player> [amount]`
  — 관리자가 강화석/등급별 확률 강화 두루마리 지급 (기본 op 권한)
- **레바테인 (Lævateinn)** — 신화 등급 커스텀 무기, `/laevateinn <player>` 로 지급 (관리자)
  - 영원한 불꽃: 공격 적중 시 시간 경과나 블록 설치로는 꺼지지 않는 저주받은 불꽃 부여
    (다량의 물 — 일정 시간 이상 물에 잠기거나 근처에 물 양동이를 사용 — 로만 해제)
  - 지옥의 화상: 방어력을 무시하는 지속 피해 + 불타는 동안 치유 효과 감소
  - 라그나로크의 숨결 (F키 발동, 재사용 대기 120초): 전방 부채꼴 범위에 화염을 내뿜어
    광역 피해 + 공중으로 띄우기 + 착지 후 10초간 지옥의 화상, 지나간 자리는 일정 시간
    마그마 블록으로 변함 (`config.yml`의 `laevateinn` 항목에서 세부 수치 조정 가능)

## GUI 배경

메뉴별로 서로 다른 바닐라 GUI 종류(각각 고유한 배경 텍스처를 가짐)를 사용해서
플러그인 메뉴들이 서로 구분되도록 했습니다 — 실제 상자(Chest)는 하나도 쓰지 않아서
일반 상자를 여는 화면과 겹치지 않습니다.

| 메뉴 | GUI 종류 | 칸 수 | 배경 테마 |
|---|---|---|---|
| `/menu` 메인 메뉴 | 호퍼 (Hopper) | 5 | 남색/은색 |
| `/home` 홈 목록 | 양조대 (Brewing Stand) | 5 | 짙은 녹색/청동 |
| 주식 거래소 | 디스펜서 (Dispenser) | 9 | 짙은 녹색/금색 |
| 대장간 강화 | 제련대 (Smithing Table) | 4 | 짙은 보라/주황(레바테인 톤) |

`resourcepack/assets/minecraft/textures/gui/container/`에 4개 배경(`hopper.png`,
`brewing_stand.png`, `dispenser.png`, `smithing.png`)을 직접 제작해 넣었습니다.
바닐라 원본 텍스처 파일 없이, 마인크래프트가 항상 고정으로 쓰는 표준 규격
(슬롯 18px 간격, 플레이어 인벤토리 칸은 항상 x=8부터 시작 등)을 기준으로 그렸습니다.
슬롯이 실제로 그려지는 위치는 게임 클라이언트에 고정되어 있어 텍스처와 약간 어긋나도
기능(클릭 등)에는 전혀 영향이 없고, 최악의 경우 슬롯 테두리가 아이템과 한두 픽셀
어긋나 보이는 정도입니다. 실제로 적용해보고 위치가 눈에 띄게 다르면 알려주시면
다시 맞춰드리겠습니다.

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

## 리소스팩 (전용 아이템 커스텀 텍스처)

`resourcepack/` 폴더(및 루트의 `MyMinecraft-ResourcePack.zip`)는 강화석, 두루마리 4등급,
레바테인이 전용 아트워크로 보이도록 만든 클라이언트 리소스팩입니다. `CustomModelData`로
동작하므로 리소스팩을 적용하지 않은 플레이어에게는 원래 아이콘(강화석→자수정 조각,
두루마리→종이, 레바테인→네더라이트 검)으로만 보이고 기능에는 영향이 없습니다.

**적용 방법**

이 저장소가 **공개(public)** 상태라면, GitHub가 그대로 리소스팩을 호스팅해줍니다.
`server.properties`에 아래 두 값을 설정하세요.

```
resource-pack=https://raw.githubusercontent.com/tntbbp123-wq/my-Minecraft/main/MyMinecraft-ResourcePack.zip
resource-pack-sha1=21dbe98859aa34c44273448b731739265e0a92fb
```

Minecraft 1.21.2 이후로는 아이템 텍스처 분기가 `assets/<ns>/models/item/*.json`의 `overrides`
방식에서 `assets/<ns>/items/*.json`의 `minecraft:range_dispatch` 방식으로 바뀌었습니다.
이 리소스팩은 두 방식을 모두 포함하고 있어서, 구버전(`overrides`)과 신버전(`items/*.json`)
클라이언트 모두에서 적용되도록 했습니다. (단, 1.21.4 이후의 정확한 스키마는 제가 확인할 수
없는 최신 버전이라 100% 보장은 못 드립니다 — 적용해보고 안 되면 알려주세요.)

`resource-pack-prompt`를 함께 쓴다면 반드시 JSON 형식이어야 합니다 (일반 텍스트를 그대로 넣으면
서버 시작 시 파싱 에러가 발생합니다):

```
resource-pack-prompt={"text":"이 서버는 필수 리소스팩이 있습니다. 다운로드 후 입장해주세요."}
```

해시는 `sha1sum MyMinecraft-ResourcePack.zip` 로 확인할 수 있습니다. 서버를 재시작하면
접속하는 플레이어에게 리소스팩 적용 여부를 묻는 창이 뜹니다.

저장소가 비공개(private)라면 위 raw 링크는 서버가 접근할 수 없으니, zip을 직접
다른 곳(자체 웹호스팅 등)에 올리고 그 URL을 `resource-pack`에 넣어주세요.

리소스팩 zip 파일을 수정한 뒤에는 반드시 해시를 다시 계산해 `resource-pack-sha1`도
갱신해야 합니다. (zip 내용이 바뀌면 해시가 달라집니다.) 이 링크는 `main` 브랜치를
가리키므로, `main`에 반영되지 않은 변경사항(개발 브랜치에만 있는 커밋)은 이 URL에
아직 나타나지 않습니다.
