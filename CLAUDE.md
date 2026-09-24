# GN Git 작업 규칙

이 저장소의 **원본은 GN Git**이다: https://gn.snrnsrk9901.com/GN/gn-plugin

GitHub(`tntbbp123-wq/my-Minecraft`)는 GN Git의 `main`과 `v*` 태그를 1분마다 받아 가는 **복사본**이다. Claude Code 클라우드 세션은 GitHub 복사본에서 시작되지만, 작업 결과는 GN Git에 올리고 병합 요청(PR)도 GN Git에 만든다.

## 1. 세션을 시작하면 (코드를 고치기 전에 한 번)

환경 변수 `GITEA_TOKEN`이 없으면 GN Git 작업을 하지 말고 사용자에게 "환경 변수 GITEA_TOKEN이 없어요"라고 알린다.

```bash
git remote get-url gitea >/dev/null 2>&1 || git remote add gitea https://gn.snrnsrk9901.com/GN/gn-plugin.git
git config credential.https://gn.snrnsrk9901.com.helper '!f() { test "$1" = get && printf "username=gn\npassword=%s\n" "$GITEA_TOKEN"; }; f'
git fetch gitea --prune --tags
```

- 작업은 항상 **`gitea/main` 기준**으로 한다. 지금 브랜치에 `gitea/main`이 포함돼 있지 않으면 `git rebase gitea/main`으로 맞춘 뒤 시작한다.
- `origin/main`(GitHub)과 `gitea/main`이 다르면 GN Git 쪽이 맞다. 단, GitHub 쪽이 GN Git보다 앞서 있으면 작업을 멈추고 사용자에게 알린다.

## 2. 작업을 올릴 때

1. 빌드를 확인한다: `mvn -B -q package` (Java 21). `resourcepack/`을 고쳤다면 `scripts/pack-resourcepack.sh`도 돌려 zip과 README의 해시를 맞춘 뒤 함께 커밋한다. 빌드 도구가 없으면 설치를 시도하고, 그래도 안 되면 병합 요청 본문에 "빌드 확인 못 함"이라고 적는다. 빌드가 실패하면 올리지 말고 원인을 알린다.
2. 커밋한 뒤 **작업 브랜치를 GN Git에 올린다**: `git push gitea HEAD:refs/heads/<지금 브랜치 이름>`
3. 그 브랜치로 열린 병합 요청이 GN Git에 없으면 API로 만든다.
   - 확인: `GET https://gn.snrnsrk9901.com/api/v1/repos/GN/gn-plugin/pulls?state=open` 결과에서 `head.ref`가 지금 브랜치인 것
   - 생성: `POST https://gn.snrnsrk9901.com/api/v1/repos/GN/gn-plugin/pulls`, 본문 `{"head": "<브랜치>", "base": "main", "title": "...", "body": "..."}`
   - 헤더: `Authorization: token $GITEA_TOKEN`, `Content-Type: application/json`
   - 한글이 들어간 JSON은 셸 따옴표로 조립하지 말고 `python3`의 `json` 모듈로 만든다.
   - 제목과 본문은 한국어로 쓴다. 본문에는 무엇을 왜 바꿨는지, 게임 안에서 어떻게 확인하는지를 적는다.
4. 마지막에 사용자에게 **GN Git 병합 요청 링크**(`html_url`)를 알려준다.

## 3. 하지 말 것

- GitHub에서 PR을 만들거나 병합하지 않는다. GitHub `main`이 GN Git과 달라지면 자동 복사가 멈춘다. (세션이 GitHub에 `claude/...` 브랜치를 자동으로 올리는 것은 괜찮다.)
- GN Git `main`에 직접 push하지 않는다. `main`은 병합 요청으로만 바꾼다. force push와 브랜치·태그 삭제도 하지 않는다.
- `GITEA_TOKEN` 값을 출력하거나 파일, 커밋, 원격 주소(URL)에 넣지 않는다.

## 4. 버전 올리기 (사용자가 요청할 때만)

버전은 `1.1.<업데이트 수>` 체계다. 사용자가 "버전 올려줘"라고 할 때마다 마지막 자리를 **1만** 올린다. 그 업데이트에 기능이 몇 개 들어갔는지는 세지 않는다.

## 5. 릴리스 (사용자가 요청할 때만)

병합이 끝난 `gitea/main` 커밋에 `pom.xml`의 `<version>`과 같은 `vX.Y.Z` 태그를 만들고 `git push gitea vX.Y.Z` 한다. 1분 안에 GitHub로 복사되고, GitHub Actions 릴리스(`.github/workflows/release.yml`)가 플러그인 jar와 리소스팩 해시(`.sha1`)를 첨부하고, 릴리스 노트에 `resource-pack-sha1` 값을 적는다.

## 6. 돈·아이템이 오가는 기능을 만들 때 (웹 관리자 거래 기록)

웹 관리자(`/_admin`)의 거래 기록은 플러그인이 남기는 `plugins/MyMinecraft/logs/trade-날짜.jsonl`을 읽는다. 그래서 G·아이템·주식이 플레이어에게 들어가거나 나가는 기능(개인 거래, 거래소·경매, 상점, 보상 지급 등)을 새로 만들면 **반드시 거래 기록을 남긴다.**

- 이미 있는 종류면 `plugin.getTradeLogger()`의 메서드를 쓴다(예: `coinBuy`, `shopCore`, `stockBuy`, `mailClaim`).
- 새 종류면 `plugin.getTradeLogger().record("종류_이름", actor, data)`로 남긴다. `data`에는 기존 종류와 같은 필드 이름을 쓴다: 플레이어 `uuid`·`name`, 금액 `amount`(단가·합계는 `unit_price`·`total`), 아이템 `item_name`·`count`. 그리고 두 사람 사이 거래면 양쪽 uuid를 모두 넣는다. 새 종류 이름과 필드는 병합 요청 본문에 적는다(웹 화면 표시 이름을 맞추기 위해).
- 기록은 돈·아이템이 **실제로 옮겨진 뒤**에 한 번만 남긴다(실패·취소는 남기지 않는다).
- 플레이어에게 아이템·G를 보내는 보상은 직접 인벤토리에 넣지 말고 우편 `plugin.getMailManager().send(...)`를 쓰면 기록과 인벤토리 가득 참 처리가 같이 된다.

## 7. config.yml 에 설정을 추가·이름 변경·삭제할 때 (웹 관리자 설정 카탈로그)

웹 관리자(`/_admin`)의 "서버 설정" 화면은 `config.yml`의 키마다 한국어 이름·설명을 붙여 보여 준다. 그 설명은 **`src/main/resources/admin-settings-catalog.yml`**(jar에 들어가고, 관리 사이트가 연결 통로 `GET /v1/settings-catalog`로 받아 감)에 있다. 카탈로그에 없는 키는 화면에 영어 키 이름 그대로 나온다. 그래서 `src/main/resources/config.yml`을 고치면 **같은 커밋에서 카탈로그도 같이 고친다.**

- **추가**: 새 말단 키마다 카탈로그 `keys:` 목록에 항목을 하나 추가한다. 같은 섹션 항목들 근처에 둔다.
- **이름 변경·이동**: 카탈로그 항목의 `key`도 새 경로로 바꾼다(설명은 그대로 둬도 됨).
- **삭제**: 카탈로그 항목도 지운다.
- **기본값·범위 변경**: 카탈로그의 `default`·`min`·`max`·`values`도 맞춘다.

**키 단위(말단 키)**: 맵은 점(`.`)으로 이어 펼친다(`economy.starting-balance`). 목록·빈 맵·글자·숫자·참거짓은 그 키 하나가 한 항목이다. 키 이름 안에 점이 있으면 `\.`로 적는다. 맵 자체(`economy`)에는 항목을 만들지 않는다.

**항목 필드** (형식 원본: gn-admin `docs/api-p5-plugins-settings.md` §7.1)

| 필드 | 필수 | 설명 |
|---|---|---|
| `key` | 필수 | 말단 키의 점 경로. config.yml과 글자 하나까지 같아야 한다. |
| `label` | 필수 | 화면에 나오는 짧은 한국어 이름(예: "시작 잔액"). |
| `desc` | 필수 | 한두 문장 설명. **쉬운 해요체 한국어**로, 게임을 모르는 관리자도 알게 쓴다. 숫자면 **단위를 꼭 적는다**(초·틱(1초=20틱)·G·%·블록·개·밀리초). 0이나 -1 같은 특별한 값이 있으면 그 뜻도 적는다. |
| `type` | 필수 | `string`·`int`·`float`·`bool`·`enum`·`list`·`secret` 중 하나. config.yml 값이 `1000.0`이면 `float`, `5`면 `int`. 글자 목록은 `list`. |
| `values` | enum일 때 필수 | 고를 수 있는 값 목록. |
| `min`·`max`·`step` | 선택 | 숫자 범위. 음수가 말이 안 되면 `min: 0`. |
| `max_len`·`pattern` | 선택 | 글자 길이 상한·정규식. |
| `default` | 선택(권장) | config.yml에 적힌 기본값과 같게. 비밀값은 `""`, 묶음 값(`editable: false`)에는 적지 않는다. |
| `restart` | 선택(기본 `true`) | 바꾼 뒤 서버 재시작이 필요하면 `true`. 이 플러그인은 대부분 시작할 때 읽으니 확실히 즉시 반영되는 게 아니면 `true`. |
| `live_command` | 선택 | `restart: false`인 enum·bool·int·float에만. 즉시 적용 명령(`{value}`·`{on_off}` 치환). 보통 쓰지 않는다. |
| `secret` | 선택(기본 `false`) | **토큰·비밀번호·API 키·웹훅 주소·`env:` 값은 반드시 `true`**(화면에서 값이 안 보이고 쓰기만 됨). `default`는 빈 글자(`""`)로 두고 실제 값을 적지 않는다. |
| `risk` | 선택(기본 `low`) | `low`·`medium`·`high`. 경제 균형이 크게 흔들리거나(시작 잔액·보상 배율) 잘못 넣으면 기능이 멈추는 값은 `medium`, 연결 통로·디스코드 연결처럼 서버 운영이 끊길 수 있는 값은 `high`. |
| `editable` | 선택(기본 `true`) | **맵·객체(맵) 목록 같은 묶음 값은 `false`**(화면에서 보기만 하고, 고칠 때는 원문 편집을 쓴다). 사람이 손대면 안 되는 값(`config-version` 등)도 `false`. |
| `verify` | 선택(기본 `false`) | 실제 서버에서 동작을 확인 못 했으면 `true`. |
| `note` | 선택 | 관리자용 짧은 메모(주의점 등). |

**예시** — config.yml에 `mail.max-per-player: 50`과 `mail.rewards`(아이템·개수 객체의 목록, 예: `- {item: 강화석, count: 3}`)를 추가했다면:

```yaml
      - key: mail.max-per-player
        label: "우편함 최대 개수"
        desc: "한 플레이어 우편함에 쌓일 수 있는 일반 우편 수(개)예요. 가득 차면 새 우편은 보류돼요. 관리자 우편은 세지 않아요."
        type: int
        min: 1
        max: 500
        default: 50
        restart: true
        risk: low

      - key: mail.rewards
        label: "우편 보상 목록"
        desc: "우편으로 보낼 보상 아이템과 개수 목록이에요. 여러 값이 묶여 있어 화면에서는 볼 수만 있고, 고칠 때는 원문 편집을 써요."
        type: list
        restart: true
        editable: false
```

**검사**: `mvn -B test -Dtest=AdminSettingsCatalogTest -Dgn.catalog.strict=true`

- 형식 오류, config.yml에 없는 카탈로그 키(이름 변경·삭제 뒤 남은 항목), 비밀 같은 키의 `secret` 누락, 묶음 값의 `editable: false` 누락은 **평소 빌드에서도 실패**한다.
- `-Dgn.catalog.strict=true`를 붙이면 config.yml의 말단 키 중 카탈로그에 **빠진 키**도 실패로 알려 준다(카탈로그를 다 채우기 전까지는 평소 빌드에서는 목록만 출력하고 건너뜀). config.yml을 고쳤다면 이 명령으로 **내가 추가한 키가 빠진 목록에 없는지** 확인한다.
- 병합 요청 본문에 "설정 카탈로그도 같이 고침(키 N개)"이라고 적는다.
