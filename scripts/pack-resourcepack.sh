#!/usr/bin/env bash
#
# 리소스팩(resourcepack/)을 zip으로 다시 묶고 SHA-1을 뽑아, 그 값을 바로 복사해 쓸 수 있게
# 출력하면서 README의 resource-pack-sha1 값도 같이 맞춰준다.
#
#   scripts/pack-resourcepack.sh           팩을 다시 묶고 README까지 갱신 (팩을 고쳤을 때)
#   scripts/pack-resourcepack.sh --print   지금 zip의 SHA-1만 보여준다 (아무것도 안 고침)
#   scripts/pack-resourcepack.sh --check   zip과 README의 값이 어긋났는지만 확인 (CI용, 어긋나면 실패)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/resourcepack"
ZIP="$ROOT/MyMinecraft-ResourcePack.zip"
README="$ROOT/README.md"
KEY='resource-pack-sha1='

die() { echo "오류: $*" >&2; exit 1; }

sha1_of() {
    if command -v sha1sum >/dev/null 2>&1; then sha1sum "$1" | cut -d' ' -f1
    else shasum -a 1 "$1" | cut -d' ' -f1    # macOS
    fi
}

readme_sha1() {
    sed -n "s/^${KEY}\([0-9a-f]\{40\}\)\$/\1/p" "$README" | head -1
}

# server.properties에 그대로 붙여넣을 수 있는 형태로 보여준다.
show() {
    local sha1="$1"
    cat <<OUT

  리소스팩 SHA-1

    $sha1

  server.properties 에 넣을 두 줄

    resource-pack=https://raw.githubusercontent.com/tntbbp123-wq/my-Minecraft/main/MyMinecraft-ResourcePack.zip
    resource-pack-sha1=$sha1

OUT
}

mode="${1:-pack}"
case "$mode" in
    --print|--check|pack) ;;
    -h|--help) sed -n '3,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "모르는 옵션: $mode (--print, --check 중 하나거나 옵션 없이)" ;;
esac

[ -f "$ZIP" ] || die "$ZIP 이 없습니다."

if [ "$mode" = "--print" ]; then
    show "$(sha1_of "$ZIP")"
    exit 0
fi

if [ "$mode" = "--check" ]; then
    zip_sha1="$(sha1_of "$ZIP")"
    doc_sha1="$(readme_sha1)"
    [ -n "$doc_sha1" ] || die "README.md에서 ${KEY}<40자리> 줄을 못 찾았습니다."
    if [ "$zip_sha1" != "$doc_sha1" ]; then
        echo "리소스팩 zip과 README의 SHA-1이 다릅니다." >&2
        echo "  zip    : $zip_sha1" >&2
        echo "  README : $doc_sha1" >&2
        echo "scripts/pack-resourcepack.sh 를 돌려 맞춘 뒤 다시 커밋하세요." >&2
        exit 1
    fi
    echo "리소스팩 SHA-1 일치: $zip_sha1"
    exit 0
fi

# ── 여기부터 다시 묶기 ───────────────────────────────────────────────
[ -d "$SRC" ] || die "$SRC 폴더가 없습니다."
command -v zip >/dev/null 2>&1 || die "zip 명령이 없습니다. (apt install zip)"

old_sha1="$(sha1_of "$ZIP")"
rm -f "$ZIP"
# -X: 타임스탬프 외의 OS 부가 정보를 넣지 않아 같은 내용이면 결과가 덜 흔들린다
( cd "$SRC" && zip -qrX "$ZIP" . -x '.*' -x '*/.*' )
new_sha1="$(sha1_of "$ZIP")"

if [ "$new_sha1" = "$old_sha1" ]; then
    echo "팩 내용이 그대로라 SHA-1도 그대로입니다."
else
    echo "팩을 다시 묶었습니다. $old_sha1 -> $new_sha1"
fi

doc_sha1="$(readme_sha1)"
if [ "$doc_sha1" = "$new_sha1" ]; then
    echo "README는 이미 같은 값입니다."
else
    # 값만 바꾸고 나머지 줄은 건드리지 않는다
    tmp="$(mktemp)"
    sed "s/^${KEY}[0-9a-f]\{40\}\$/${KEY}${new_sha1}/" "$README" > "$tmp"
    mv "$tmp" "$README"
    echo "README의 ${KEY} 값을 갱신했습니다."
fi

show "$new_sha1"
echo "  zip과 README를 함께 커밋하세요."
echo
