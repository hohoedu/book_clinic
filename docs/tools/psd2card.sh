#!/usr/bin/env bash
#
# 학생증 QR 카드 디자인 → 인쇄용 PNG 변환
#
# 디자이너가 준 앞면/뒷면 시안을 카드 인쇄 해상도(600dpi) PNG로 변환해
# src/main/resources/static/images/card/{front,back}.png 에 놓는다.
# 이 파일이 있으면 학생정보 화면의 "QR 카드 출력"이 자동으로 배경에 깔아 인쇄한다.
#
# 입력 포맷은 앞/뒷면이 서로 달라도 된다 — PSD, PNG, JPEG, TIFF 등 sips가 읽는 건 전부 받는다.
# (현재 실제 파일: 앞면 PSD, 뒷면 PNG)
#
# 사용법:
#   ./docs/tools/psd2card.sh 앞면.psd 뒷면.png
#   ./docs/tools/psd2card.sh 앞면.psd                  # 앞면만 교체
#   ./docs/tools/psd2card.sh --back 뒷면.png           # 뒷면만 교체
#
# 변환 규격 — CR-80(ISO ID-1) 가로 85.60 x 53.98mm @ 600dpi = 2022 x 1275px (비율 1.5858 : 1)
#   원본이 이 비율과 다르면 경고를 띄운 뒤 카드 해상도로 늘려 맞춘다(비율 왜곡, 좌표는 유지). 경고가 나오면
#   재단선 기준으로 자른 시안을 다시 받는 편이 안전하다(블리드 포함 시안이면 특히).
#
# macOS 내장 sips를 쓴다. PSD는 레이어가 병합된 합성 이미지(composite)로 읽힌다 —
# 레이어별 제어가 필요하면 Photoshop/Affinity에서 직접 PNG로 내보내는 게 낫다.

set -euo pipefail

W=2022
H=1275
RATIO_TOLERANCE=0.02   # 2% 이내 차이는 무시
OUT_DIR="$(cd "$(dirname "$0")/../.." && pwd)/src/main/resources/static/images/card"

if ! command -v sips >/dev/null 2>&1; then
  echo "오류: sips 를 찾을 수 없습니다 (macOS 전용 스크립트입니다)." >&2
  echo "      Photoshop에서 ${W} x ${H}px(가로형) PNG로 직접 내보내 ${OUT_DIR} 에 넣어주세요." >&2
  exit 1
fi

px() { sips -g "$1" "$2" | tail -1 | awk '{print $2}'; }

convert_one() {
  local src="$1" name="$2"
  if [ ! -f "$src" ]; then
    echo "오류: 파일이 없습니다 — $src" >&2
    exit 1
  fi
  mkdir -p "$OUT_DIR"
  local dst="$OUT_DIR/$name.png"

  local sw sh
  sw=$(px pixelWidth "$src") || true
  sh=$(px pixelHeight "$src") || true
  if [ -z "${sw:-}" ] || [ -z "${sh:-}" ]; then
    echo "오류: 이미지로 읽을 수 없습니다 — $src" >&2
    exit 1
  fi

  echo "변환: $src (${sw}x${sh}) → $dst (${W}x${H})"

  # 카드 비율(1 : 1.5858)과 얼마나 벗어나는지 확인
  awk -v sw="$sw" -v sh="$sh" -v w="$W" -v h="$H" -v tol="$RATIO_TOLERANCE" '
    BEGIN {
      target = w / h; actual = sw / sh;
      diff = (actual - target) / target; if (diff < 0) diff = -diff;
      if (diff > tol) {
        printf "  ⚠ 비율 경고: 원본 %.4f:1, 카드 %.4f:1 (%.1f%% 차이)\n", actual, target, diff * 100;
        print  "    카드 해상도로 늘려 맞추므로 그만큼 이미지가 왜곡됩니다(좌표는 밀리지 않습니다).";
        print  "    블리드 포함 시안이면 재단선 기준으로 자른 파일을 다시 받으세요.";
      }
    }'

  # PSD/PNG/JPEG 무엇이 들어와도 PNG로 통일한 뒤 카드 해상도로 리샘플
  sips -s format png "$src" --out "$dst" >/dev/null
  sips -z "$H" "$W" "$dst" >/dev/null
  echo "  완료: $(px pixelWidth "$dst")x$(px pixelHeight "$dst")px"
}

FRONT=""
BACK=""
case "${1:-}" in
  "")
    echo "사용법: $0 <앞면파일> [뒷면파일]" >&2
    echo "        $0 --back <뒷면파일>        # 뒷면만 교체" >&2
    exit 1
    ;;
  --back)
    BACK="${2:-}"
    [ -n "$BACK" ] || { echo "오류: --back 뒤에 파일을 지정하세요." >&2; exit 1; }
    ;;
  *)
    FRONT="$1"
    BACK="${2:-}"
    ;;
esac

[ -n "$FRONT" ] && convert_one "$FRONT" front
[ -n "$BACK" ]  && convert_one "$BACK"  back

echo ""
echo "끝났습니다. 학생정보 화면 → QR 카드 출력 → '디자인 포함' + '양면' 으로 미리보기 확인하세요."
