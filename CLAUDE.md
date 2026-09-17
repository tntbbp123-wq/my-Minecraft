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

병합이 끝난 `gitea/main` 커밋에 `pom.xml`의 `<version>`과 같은 `vX.Y.Z` 태그를 만들고 `git push gitea vX.Y.Z` 한다. 1분 안에 GitHub로 복사되고, GitHub Actions 릴리스(`.github/workflows/release.yml`)가 jar와 리소스팩을 첨부한다.
