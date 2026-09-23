/*
 * 학생 정보 — 목록/필터/학년옵션/상세정보(전체·기본정보 탭)·독서이력·예약현황은 /admin/students/* API로
 * 실데이터를 쓴다(2026-08-26). 수강 정보 탭도 2026-09-15부터 erp_bookstore_assign 실데이터 + 저장이다
 * (담당선생님·교재비는 대응 컬럼이 없어 아예 뺐다). "학생과의 관계"만 아직 목업으로 남아있다.
 */

document.addEventListener("DOMContentLoaded", () => {
  initFilterBar();
  initStudentModal();
  loadGradeOptions();
  loadStudentList();
  document.getElementById("btnPrintQrCard").addEventListener("click", printQrCards);
});

const RESULT_LABEL = { DONE_KING: "독서왕", DONE_FRIEND: "통과", PENDING: "재도전" };

/* 회원가입 화면 링크 — 경로는 아직 확정 전이라 임시다(추후 수정 예정).
   origin은 현재 접속 도메인, centerCode는 로그인 직원 센터(body[data-center-code])를 붙인다 */
const SIGNUP_LINK_PATH = "/signup";

/* 저장 요청(수강 정보 탭)용 CSRF 헤더 — payment-review.js 와 같은 방식 */
const CSRF_HEADER = "X-XSRF-TOKEN";

/* ===================== 목록 조회 ===================== */

async function fetchJson(url) {
  const res = await fetch(url);
  const body = await res.json();
  if (!res.ok || body.success === false) {
    throw new Error(body?.error?.message || "요청 처리 중 오류가 발생했습니다.");
  }
  return body.response;
}

/* 수강 정보 탭 저장용 — CSRF 헤더를 실어 JSON 본문을 보낸다 */
async function putJson(url, payload) {
  const res = await fetch(url, {
    method: "PUT",
    headers: { "Content-Type": "application/json", [CSRF_HEADER]: getCsrfToken() },
    body: JSON.stringify(payload),
  });
  const body = await res.json();
  if (!res.ok || body.success === false) {
    throw new Error(body?.error?.message || "저장 중 오류가 발생했습니다.");
  }
  return body.response;
}

function getCsrfToken() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}

async function loadGradeOptions() {
  try {
    const options = await fetchJson("/admin/students/grade-options");
    const select = document.getElementById("filterGrade");
    options.forEach((opt) => {
      const el = document.createElement("option");
      el.value = opt.code;
      el.textContent = opt.codeNm;
      select.appendChild(el);
    });
  } catch (e) {
    console.error("학년 옵션 조회 실패", e);
  }
}

function currentFilters() {
  return {
    grade: document.getElementById("filterGrade").value,
    status: document.getElementById("filterStatus").value,
    keyword: document.getElementById("filterKeyword").value.trim(),
  };
}

async function loadStudentList() {
  const listBody = document.getElementById("studentListBody");
  const { grade, status, keyword } = currentFilters();
  const params = new URLSearchParams();
  if (grade) params.set("grade", grade);
  if (status) params.set("status", status);
  if (keyword) params.set("keyword", keyword);

  try {
    const students = await fetchJson(`/admin/students/list?${params.toString()}`);
    renderStudentList(students);
  } catch (e) {
    console.error("학생 목록 조회 실패", e);
    listBody.innerHTML = `<tr><td colspan="9" class="empty-row">목록을 불러오지 못했습니다: ${escapeHtml(e.message)}</td></tr>`;
  }
}

function renderStudentList(students) {
  const listBody = document.getElementById("studentListBody");
  if (!students.length) {
    listBody.innerHTML = `<tr><td colspan="9" class="empty-row">조회된 학생이 없습니다.</td></tr>`;
    return;
  }

  listBody.innerHTML = students.map((s, idx) => {
    const levelNo = s.levelNo ?? 1;
    const medalSrc = gradeMedalSrc(s.clinicGradeKey);
    const statusLabel = s.statusKey === "WITHDRAWN" ? "탈퇴" : "이용중";
    const statusClass = s.statusKey === "WITHDRAWN" ? "status-withdrawn" : "status-active";
    return `
      <tr class="student-row"
          data-student-id="${escapeHtml(s.studentId)}"
          data-name="${escapeHtml(s.studentName)}"
          data-grade="${escapeHtml(s.gradeName ?? "")}"
          data-phone="${escapeHtml(s.billingPhone ?? "")}"
          data-reg-date="${escapeHtml(s.registeredAt ?? "")}"
          data-visit-date="${escapeHtml(s.lastVisitDate ?? "-")}"
          data-books="${s.totalDoneBooks ?? 0}"
          data-level="${levelNo}"
          data-status="${statusLabel}"
          data-status-class="${statusClass}">
        <td class="col-no">${idx + 1}</td>
        <td class="col-name">${escapeHtml(s.studentName)}</td>
        <td>${escapeHtml(s.gradeName ?? "-")}</td>
        <td>${escapeHtml(s.billingPhone ?? "-")}</td>
        <td class="col-date">${escapeHtml(s.registeredAt ?? "-")}</td>
        <td class="col-date">${escapeHtml(s.lastVisitDate ?? "-")}</td>
        <td class="col-books">${s.totalDoneBooks ?? 0}</td>
        <td class="col-level">${medalSrc ? `<img class="level-medal" src="${medalSrc}" alt=""
              title="독서 학년 ${escapeHtml(s.clinicGradeName ?? "-")}">` : ""}Lv. ${levelNo}</td>
        <td><span class="status ${statusClass}">${statusLabel}</span></td>
      </tr>
    `;
  }).join("");
}

/* 메달은 "학년" 칸의 진짜 학년(grade_key)이 아니라 독서 학년(clinic_grade_key) 기준이다 — 둘은 다를 수 있다.
   clinic_grade_key('01'~'06' = 초1~초6)가 곧 medal_1~6.png 의 번호다. 메달 그림에 학년 숫자가
   그려져 있어서, 이미지가 없는 중등('07')이나 학년 미지정은 엉뚱한 숫자를 보여주느니 메달 없이 "Lv. n" 만 노출한다.

   images/medal_sm/ 은 목록 표시용 축소본(104x104)이다 — 원본 images/medal_N.png 는 1254x1254, 장당
   1.3MB 라 26px 아이콘 6종에 8MB 를 받게 돼서 따로 뒀다. 크게 쓰는 화면은 원본을 그대로 쓰면 된다. */
function gradeMedalSrc(clinicGradeKey) {
  const n = Number(clinicGradeKey);
  return Number.isInteger(n) && n >= 1 && n <= 6 ? `/images/medal_sm/medal_${n}.png` : null;
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/* "yyyy-MM-dd" -> "yyyy년 MM월 dd일" (그 외 형식/빈 값은 원본 그대로 노출) */
function formatKoreanDate(value) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value ?? "");
  return m ? `${m[1]}년 ${m[2]}월 ${m[3]}일` : (value ?? "-");
}

/* ===================== 필터바 ===================== */

function initFilterBar() {
  document.getElementById("btnSearch").addEventListener("click", loadStudentList);
  document.getElementById("filterKeyword").addEventListener("keydown", (event) => {
    if (event.key === "Enter") loadStudentList();
  });

  // 셀렉트는 고르는 즉시 조회 (조회 버튼은 이름/연락처 검색어 입력용으로 남겨둔다) — 도서 데이터 관리와 동일한 방식
  ["filterGrade", "filterStatus"].forEach((id) => {
    document.getElementById(id).addEventListener("change", loadStudentList);
  });

  document.getElementById("btnFilterReset").addEventListener("click", () => {
    document.getElementById("filterGrade").value = "";
    document.getElementById("filterStatus").value = "";
    document.getElementById("filterKeyword").value = "";
    loadStudentList();
  });
  document.getElementById("btnCopySignupLink").addEventListener("click", copySignupLink);
}

async function copySignupLink() {
  const centerCode = document.body.dataset.centerCode || "";
  const query = centerCode ? `?centerCode=${encodeURIComponent(centerCode)}` : "";
  const url = window.location.origin + SIGNUP_LINK_PATH + query;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url);
    } else {
      const temp = document.createElement("textarea");
      temp.value = url;
      temp.style.position = "fixed";
      temp.style.opacity = "0";
      document.body.appendChild(temp);
      temp.select();
      document.execCommand("copy");
      document.body.removeChild(temp);
    }
    alert("가입 링크를 복사했습니다.");
  } catch (e) {
    alert("링크 복사에 실패했습니다. 아래 주소를 직접 복사해주세요.\n" + url);
  }
}

/* ===================== 모달 열기/닫기/탭 ===================== */

let currentDetail = null;

function initStudentModal() {
  const modal = document.getElementById("studentModal");
  const listBody = document.getElementById("studentListBody");

  listBody.addEventListener("click", (event) => {
    const row = event.target.closest(".student-row");
    if (!row || !row.dataset.studentId) return;
    openStudentModal(row.dataset.studentId, row.dataset.name);
  });

  document.getElementById("btnCloseStudentModal").addEventListener("click", closeStudentModal);
  modal.addEventListener("click", (event) => {
    if (event.target === modal) closeStudentModal();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !modal.hidden) closeStudentModal();
  });

  document.getElementById("studentTabs").addEventListener("click", (event) => {
    const tabBtn = event.target.closest("button[data-tab]");
    if (!tabBtn) return;
    switchTab(tabBtn.dataset.tab);
  });
}

async function openStudentModal(studentId, studentName) {
  document.getElementById("studentModalTitle").textContent = `${studentName} 학생 상세정보`;
  document.getElementById("studentModalBody").innerHTML = `<p class="modal-loading">불러오는 중...</p>`;
  document.getElementById("studentModal").hidden = false;

  try {
    const [detail, readingHistory, reservations, assign] = await Promise.all([
      fetchJson(`/admin/students/${encodeURIComponent(studentId)}`),
      fetchJson(`/admin/students/${encodeURIComponent(studentId)}/reading-history`),
      fetchJson(`/admin/students/${encodeURIComponent(studentId)}/reservations`),
      fetchJson(`/admin/students/${encodeURIComponent(studentId)}/bookstore-assign`),
    ]);
    currentDetail = { ...detail, readingHistory, reservations, assign };
    switchTab("all");
  } catch (e) {
    console.error("학생 상세 조회 실패", e);
    document.getElementById("studentModalBody").innerHTML = `<p class="modal-loading">상세정보를 불러오지 못했습니다: ${escapeHtml(e.message)}</p>`;
  }
}

function closeStudentModal() {
  document.getElementById("studentModal").hidden = true;
  currentDetail = null;
}

/* ===================== QR 카드 출력 ===================== */

/*
 * 학생증 QR 카드 출력 — 앞면/뒷면 양면 지원.
 *
 * 카드 디자인은 디자이너가 준 PSD를 PNG로 변환해 아래 두 경로에 놓으면 그대로 배경으로 깔린다.
 *   앞면: /images/card/front.png
 *   뒷면: /images/card/back.png
 * 변환은 docs/tools/psd2card.sh 참고 (macOS sips 기반, 600dpi = 1275 x 2022px 로 리샘플).
 * 파일이 없으면 배경 없이(= 예전처럼 선인쇄 원판 오버프린트용으로) 그대로 동작한다.
 *
 * 인쇄 창 상단 토글 4가지:
 *   - 용지   : A4 시트(2x5 = 10장/장, 점선 재단선) / 카드 낱장(@page 86x54mm 가로)
 *   - 면     : 앞면만 / 뒷면만 / 양면
 *   - 디자인 : 포함(백지 PVC·일반 용지에 전면 인쇄) / 제외(선인쇄 원판에 가변정보만 오버프린트)
 *   - 뒷면   : 그대로 / 180° 회전
 *
 * 양면 인쇄는 프린터에 따라 두 갈래다:
 *   - 양면 모듈 있는 카드 프린터(IDP SMART-51D 등): 드라이버를 "Both sides"로 두고 "카드 낱장 +
 *     양면"으로 보내면 1페이지=앞, 2페이지=뒤로 알아서 뒤집어 찍는다.
 *   - 단면 프린터(51S 등): 자동이 안 되므로 "앞면만" 출력 → 카드를 뒤집어 재투입 → "뒷면만" 출력의
 *     수동 2패스로 간다. 뒤집는 방향 때문에 뒷면이 거꾸로 나오면 "뒷면 180° 회전"을 켠다.
 * A4 + 양면이면 앞면 페이지 전부 → 뒷면 페이지가 뒤따르고, 뒷면은 각 행을 좌우 반전해 배치한다
 * (출력물을 좌우로 뒤집어 재급지했을 때 앞뒷면이 맞아떨어지도록).
 * 카드 낱장 + 양면이면 앞1 → 뒤1 → 앞2 → 뒤2 … 순서로 페이지가 나간다(카드 프린터 양면 급지 순서).
 *
 * 가변정보(QR·지점명·ID) 좌표는 인쇄 doc 안 .qr / .center-name / .serial 규칙에서 조정한다.
 * 현재 값은 디자인 시안(앞면 PNG)과 완성 목업에서 실측해 카드 크기 비율로 환산한 것이다 —
 * QR은 시안의 흰 둥근박스 정중앙, 지점명은 좌하단 연녹색 박스 안, ID는 우측 중앙.
 * QR 원문은 시리얼 넘버 원문 그대로다 — 학생 로그인/스캔이 app_id 또는 serial_num 어느 쪽으로도
 * 학생을 찾으므로(StudentMapper.findByAppId 참고), 실물 카드에는 임의 발급한 시리얼 넘버를 싣는다.
 *
 * 아래 QR_CARD_SERIALS는 지금은 테스트용 고정 배치다. 지금은 100260001 ~ 100260005 5장을 뽑는다 —
 * 장수가 달라지면 범위만 바꾸면 된다.
 * 실제 발급 플로우(임의 시리얼 생성 → 학생 등록)를 붙일 땐 이 배열을 그 결과로 바꾸면 된다.
 */
const QR_CARD_SERIALS = serialRange(100260001, 100260005);
const QR_CARD_CENTER_NAME = "부산 센텀점";

// 디자이너 PSD → PNG 변환 결과를 놓는 자리. 없으면 배경 없이 인쇄된다.
const QR_CARD_DESIGN = {
  front: "/images/card/front.png",
  back: "/images/card/back.png",
};

function serialRange(from, to) {
  const list = [];
  for (let n = from; n <= to; n += 1) list.push(String(n));
  return list;
}

function makeQrDataUrl(text) {
  const holder = document.createElement("div");
  holder.style.display = "none";
  document.body.appendChild(holder);
  // eslint-disable-next-line no-new
  new QRCode(holder, { text, width: 480, height: 480, correctLevel: QRCode.CorrectLevel.M });
  const canvas = holder.querySelector("canvas");
  const dataUrl = canvas ? canvas.toDataURL("image/png") : (holder.querySelector("img") || {}).src;
  holder.remove();
  return dataUrl;
}

function designUrl(path) {
  const url = new URL(path, window.location.origin);
  url.searchParams.set("v", Date.now());
  return url.href;
}

function printQrCards() {
  if (typeof QRCode !== "function") {
    alert("QR 생성 모듈을 불러오지 못했습니다. 네트워크 상태를 확인한 뒤 다시 시도해주세요.");
    return;
  }

  // 카드 데이터(시리얼 + QR 이미지)는 여기서 만들어 인쇄 창 스크립트에 넘긴다.
  // 인쇄 창은 토글을 누를 때마다 이 배열로 DOM을 다시 그린다(면/용지 조합마다 페이지 구성이 달라서).
  const cardData = QR_CARD_SERIALS.map((serial) => ({
    serial,
    qr: makeQrDataUrl(serial),
  }));

  const win = window.open("", "_blank", "width=960,height=800");
  if (!win) {
    alert("팝업이 차단되어 있습니다. 팝업 허용 후 다시 시도해주세요.");
    return;
  }

  const payload = {
    cards: cardData,
    centerName: QR_CARD_CENTER_NAME,
    // sw.js가 /images/ 를 cache-first로 잡고 있어 디자인을 교체해도 옛 응답(없던 시절의 실패 포함)을
    // 계속 물 수 있다. 인쇄 창을 열 때마다 쿼리를 새로 붙여 캐시를 우회한다.
    design: {
      front: designUrl(QR_CARD_DESIGN.front),
      back: designUrl(QR_CARD_DESIGN.back),
    },
  };

  win.document.write(`<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>학생증 QR 카드 (${QR_CARD_SERIALS.length}장)</title>
<style id="pageRule">@page { size: A4 portrait; margin: 8mm; }</style>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: "Malgun Gothic", "Apple SD Gothic Neo", -apple-system, sans-serif; -webkit-print-color-adjust: exact; print-color-adjust: exact; background: #e9ecef; }

  /* 상단 컨트롤 바 — 인쇄 시에는 숨김 */
  .toolbar {
    position: sticky; top: 0; z-index: 10;
    display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
    padding: 10px 14px; background: #fff; border-bottom: 1px solid #ddd;
  }
  .toolbar .group { display: flex; align-items: center; gap: 4px; }
  .toolbar .group + .group { margin-left: 10px; padding-left: 10px; border-left: 1px solid #e3e3e3; }
  .toolbar .label { font-size: 12px; color: #666; margin-right: 2px; }
  .toolbar button {
    padding: 6px 12px; font-size: 13px; font-weight: 600;
    border: 1px solid #1c3aa1; border-radius: 6px;
    background: #fff; color: #1c3aa1; cursor: pointer;
  }
  .toolbar button.active { background: #1c3aa1; color: #fff; }
  .toolbar .spacer { flex: 1; }
  .toolbar .hint { font-size: 12px; color: #888; font-weight: 400; flex-basis: 100%; }
  .toolbar .warn { color: #c0392b; }
  .toolbar .print-btn { background: #1c3aa1; color: #fff; }
  .toolbar .adjust label { font-size: 12px; color: #555; }
  .toolbar .adjust input {
    width: 52px; padding: 4px 5px; margin: 0 1px;
    font-size: 12px; border: 1px solid #ccc; border-radius: 4px;
  }
  .toolbar .adjust button { padding: 4px 8px; font-size: 12px; }

  /* 한 페이지 = .page. A4 모드에선 카드 9장(3x3), 카드 낱장 모드에선 1장. */
  .page { page-break-after: always; }
  .page:last-child { page-break-after: auto; }

  body.mode-a4 .page { display: flex; flex-wrap: wrap; align-content: flex-start; padding: 16px; }
  body.mode-a4 .card { outline: 0.2mm dashed #b0b0b0; }

  body.mode-card .page { padding: 16px; }
  body.mode-card .card { margin: 0 auto; }

  @media screen {
    .card { box-shadow: 0 1px 6px rgba(0, 0, 0, .18); }
    .page { background: #fff; margin: 0 auto 18px; width: max-content; }
  }
  @media print {
    body { background: #fff; }
    .toolbar { display: none; }
    .page { padding: 0 !important; margin: 0; background: none; width: auto; }
    .card { box-shadow: none; }
  }

  /*
   * 가로형 카드. CR-80(ISO ID-1) 가로 = 85.60 x 53.98mm — IDP SMART-51 카드 크기와 정확히 맞춰야 함.
   * .card-bg = 디자이너가 준 앞/뒷면 디자인 PNG(디자인 제외 모드에서는 숨김).
   *   object-fit: fill — 시안 비율이 카드 비율(1.5858)과 미세하게 달라도 좌표가 밀리지 않도록
   *   잘라내지 않고 늘려 채운다. 시안을 정확한 비율로 받으면 왜곡 없이 그대로 맞는다.
   * 가변정보는 그 위 레이어. 좌표는 전부 "요소 중심"이라 translate(-50%, -50%)로 잡는다.
   * ▼ 시안이 바뀌면 아래 mm 값만 고치면 된다.
   */
  .card {
    position: relative;
    width: 85.6mm; height: 53.98mm;
    background: transparent;
    overflow: hidden;
  }
  /* 카드 내용 전체(배경 + 가변정보)를 감싸는 레이어. 프린터마다 인쇄 시작점이 조금씩 달라
     결과물이 밀리거나 작게 나오는데, 여기에 오프셋(mm)과 배율(%)을 걸어 실물을 보며 맞춘다.
     --ox/--oy/--sc 는 툴바에서 주입한다. */
  .card-inner {
    position: absolute; inset: 0;
    transform: translate(var(--ox, 0mm), var(--oy, 0mm)) scale(var(--sc, 1));
    transform-origin: center center;
  }
  /* 카드 프린터는 헤드가 카드 끝까지 닿지 않아 가장자리에 흰 테두리가 남는다(풀블리드 불가).
     배경만 살짝 키워 카드 밖으로 넘기면(.card 의 overflow:hidden 이 잘라냄) 테두리가 사라진다.
     가변정보(QR·지점명·ID)는 확대 대상이 아니라서 좌표가 그대로 유지된다. */
  .card-bg {
    position: absolute; inset: 0;
    width: 100%; height: 100%;
    object-fit: fill;
    transform: scale(var(--bgsc, 1));
    transform-origin: center center;
  }
  body.design-off .card-bg { display: none; }
  /* 단면 카드 프린터로 수동 2패스 양면을 찍을 때, 카드를 뒤집어 재투입하는 방향에 따라
     뒷면이 거꾸로 나오는 경우가 있다. 그때 이 토글로 뒷면만 180° 돌려 내보낸다. */
  body.flip-180 .card.back { transform: rotate(180deg); }

  /* 보정용 눈금 — 카드 테두리, 5mm 간격 눈금, 중앙 십자. 위치 보정값을 잡을 때만 켠다.
     .card-inner 안에 있으므로 보정값을 바꾸면 눈금도 같이 움직인다(= 실제 인쇄 위치를 그대로 보여줌). */
  .guide { display: none; }
  body.guide-on .guide { display: block; }
  .guide-frame {
    position: absolute; inset: 0;
    border: 0.3mm solid #e01b24;
  }
  .guide-cross-h, .guide-cross-v {
    position: absolute; background: #e01b24;
  }
  .guide-cross-h { left: 0; right: 0; top: 50%; height: 0.2mm; }
  .guide-cross-v { top: 0; bottom: 0; left: 50%; width: 0.2mm; }
  .guide-tick {
    position: absolute; background: #e01b24;
  }
  .guide-tick.h { top: 0; width: 0.2mm; height: 2mm; }
  .guide-tick.v { left: 0; height: 0.2mm; width: 2mm; }
  .guide-label {
    position: absolute; font-size: 2.2mm; color: #e01b24;
    top: 2.4mm; transform: translateX(-50%);
  }

  .bg-error {
    position: absolute; left: 0; right: 0; top: 50%; transform: translateY(-50%);
    text-align: center; font-size: 3mm; color: #c0392b;
  }

  /* QR — 앞면 시안 좌상단 흰 둥근박스의 정중앙. 박스가 이미 quiet zone이라 흰 배경을 덧대지 않는다. */
  .qr {
    position: absolute; left: 25.6mm; top: 21.4mm;
    transform: translate(-50%, -50%);
    width: 23mm; height: 23mm;
    image-rendering: pixelated;
  }
  /* 지점명 — 좌하단 연녹색 박스 안 중앙 */
  .center-name {
    position: absolute; left: 24.9mm; top: 41.7mm;
    transform: translate(-50%, -50%);
    font-size: 4mm; font-weight: 700; color: #000000;
    white-space: nowrap;
  }
  /* ID — 우측 중앙, 캐릭터 띠 아래 */
  .serial {
    position: absolute; left: 64.5mm; top: 32.8mm;
    transform: translate(-50%, -50%);
    font-size: 3.4mm; color: #000000;
    white-space: nowrap;
  }

  /* 디자인 PNG가 아직 없을 때만, 화면에서 위치 감을 잡도록 옅은 상단 밴드 표시 (인쇄 안 됨) */
  @media screen {
    body.design-missing .card { background: #fff; }
    body.design-missing .card::before {
      content: ""; position: absolute; left: 0; right: 0; top: 0; height: 26.5mm;
      background: #79bc43; opacity: .18;
    }
  }
</style>
</head>
<body class="mode-a4 side-front design-on">
  <div class="toolbar">
    <div class="group">
      <span class="label">용지</span>
      <button type="button" data-mode="a4" class="active">A4 시트</button>
      <button type="button" data-mode="card">카드 낱장</button>
    </div>
    <div class="group">
      <span class="label">면</span>
      <button type="button" data-side="front" class="active">앞면만</button>
      <button type="button" data-side="back">뒷면만</button>
      <button type="button" data-side="both">양면</button>
    </div>
    <div class="group">
      <span class="label">디자인</span>
      <button type="button" data-design="on" class="active">포함</button>
      <button type="button" data-design="off">제외(오버프린트)</button>
    </div>
    <div class="group">
      <span class="label">뒷면</span>
      <button type="button" data-flip="0" class="active">그대로</button>
      <button type="button" data-flip="180">180° 회전</button>
    </div>
    <div class="group adjust">
      <span class="label">위치 보정</span>
      <label>X <input type="number" id="adjX" step="0.1" value="0">mm</label>
      <label>Y <input type="number" id="adjY" step="0.1" value="0">mm</label>
      <label>배율 <input type="number" id="adjS" step="0.5" value="100">%</label>
      <label>배경확대 <input type="number" id="adjB" step="0.5" value="100">%</label>
      <button type="button" id="btnAdjReset">초기화</button>
      <button type="button" id="btnGuide">눈금 표시</button>
    </div>
    <span class="spacer"></span>
    <button type="button" class="print-btn" id="btnDoPrint">인쇄하기</button>
    <span class="hint" id="modeHint"></span>
  </div>
  <div id="sheets"></div>
  <script>
    (function () {
      var DATA = ${JSON.stringify(payload)};
      var PER_ROW = 2;   // A4(210mm, 여백 8mm) 가로 2장 = 171.2mm
      var PER_PAGE = 10; // 세로 5장 = 269.9mm

      var state = { mode: "a4", side: "front", design: "on", flip: "0", guide: false };
      // 위치 보정값은 프린터마다 한 번 맞추면 계속 쓰는 값이라 보관해둔다.
      // 인쇄 창은 about:blank라 자체 저장소가 없어 부모 창(opener)의 localStorage를 빌려 쓴다.
      var ADJ_KEY = "qrCardAdjust";
      var adj = { x: 0, y: 0, s: 100, b: 100 };

      function loadAdjust() {
        try {
          var raw = window.opener && window.opener.localStorage.getItem(ADJ_KEY);
          if (raw) {
            var v = JSON.parse(raw);
            adj = { x: Number(v.x) || 0, y: Number(v.y) || 0, s: Number(v.s) || 100, b: Number(v.b) || 100 };
          }
        } catch (e) { /* 저장소를 못 쓰는 환경이면 기본값으로 간다 */ }
      }

      function saveAdjust() {
        try {
          if (window.opener) window.opener.localStorage.setItem(ADJ_KEY, JSON.stringify(adj));
        } catch (e) { /* 무시 */ }
      }

      function applyAdjust() {
        var root = document.body.style;
        root.setProperty("--ox", adj.x + "mm");
        root.setProperty("--oy", adj.y + "mm");
        root.setProperty("--sc", (adj.s / 100));
        root.setProperty("--bgsc", (adj.b / 100));
      }
      var designReady = { front: false, back: false };

      // 디자인 PNG 존재 여부를 먼저 확인한다 — 없으면 "디자인 포함"을 끄고 안내만 띄운다.
      function probe(key, url, done) {
        var img = new Image();
        img.onload = function () { designReady[key] = true; done(); };
        img.onerror = function () { designReady[key] = false; done(); };
        img.src = url;
      }

      // 보정용 눈금 마크업 — 카드 테두리 + 가로/세로 5mm 눈금(10mm마다 숫자) + 중앙 십자.
      function guideHtml() {
        var h = '<div class="guide"><div class="guide-frame"></div>'
          + '<div class="guide-cross-h"></div><div class="guide-cross-v"></div>';
        for (var x = 5; x < 86; x += 5) {
          h += '<span class="guide-tick h" style="left:' + x + 'mm"></span>';
          if (x % 10 === 0) h += '<span class="guide-label" style="left:' + x + 'mm">' + x + "</span>";
        }
        for (var y = 5; y < 54; y += 5) {
          h += '<span class="guide-tick v" style="top:' + y + 'mm"></span>';
        }
        return h + "</div>";
      }

      function cardHtml(card, face) {
        var bgUrl = face === "back" ? DATA.design.back : DATA.design.front;
        var bg = designReady[face] ? '<img class="card-bg" src="' + bgUrl + '" alt="">' : "";
        // 뒷면에는 가변정보를 올리지 않는다(안내문구는 디자인에 선인쇄). 필요해지면 여기에 추가.
        if (face === "back") {
          return '<div class="card back"><div class="card-inner">' + bg + guideHtml() + "</div></div>";
        }
        return '<div class="card"><div class="card-inner">' + bg
          + '<img class="qr" src="' + card.qr + '" alt="QR ' + card.serial + '">'
          + '<span class="center-name">' + DATA.centerName + "</span>"
          + '<span class="serial">ID : ' + card.serial + "</span>"
          + guideHtml()
          + "</div></div>";
      }

      // A4 뒷면 시트 — 출력물을 좌우로 뒤집어 재급지하므로 각 행의 순서를 반전한다.
      function mirrorRows(list) {
        var out = [];
        for (var i = 0; i < list.length; i += PER_ROW) {
          out = out.concat(list.slice(i, i + PER_ROW).reverse());
        }
        return out;
      }

      function pagesForA4(face) {
        var html = "";
        for (var i = 0; i < DATA.cards.length; i += PER_PAGE) {
          var chunk = DATA.cards.slice(i, i + PER_PAGE);
          if (face === "back") chunk = mirrorRows(chunk);
          html += '<div class="page">'
            + chunk.map(function (c) { return cardHtml(c, face); }).join("")
            + "</div>";
        }
        return html;
      }

      function render() {
        var html = "";
        if (state.mode === "a4") {
          if (state.side === "front" || state.side === "both") html += pagesForA4("front");
          if (state.side === "back" || state.side === "both") html += pagesForA4("back");
        } else {
          DATA.cards.forEach(function (c) {
            if (state.side === "front" || state.side === "both") {
              html += '<div class="page">' + cardHtml(c, "front") + "</div>";
            }
            if (state.side === "back" || state.side === "both") {
              html += '<div class="page">' + cardHtml(c, "back") + "</div>";
            }
          });
        }
        document.getElementById("sheets").innerHTML = html;

        // 배경 로드 실패를 엑박 대신 안내로 바꾼다(어느 면의 어떤 경로가 실패했는지 화면에 남긴다).
        document.querySelectorAll(".card-bg").forEach(function (img) {
          img.addEventListener("error", function () {
            var card = img.closest(".card");
            img.remove();
            if (card && !card.querySelector(".bg-error")) {
              var msg = document.createElement("span");
              msg.className = "bg-error";
              msg.textContent = "디자인 이미지를 불러오지 못했습니다";
              card.appendChild(msg);
            }
          });
        });
      }

      function syncBody() {
        var cls = ["mode-" + state.mode, "side-" + state.side, "design-" + state.design];
        if (state.flip === "180") cls.push("flip-180");
        if (state.guide) cls.push("guide-on");
        var need = state.side === "back" ? ["back"] : state.side === "front" ? ["front"] : ["front", "back"];
        var missing = need.filter(function (k) { return !designReady[k]; });
        if (missing.length) cls.push("design-missing");
        document.body.className = cls.join(" ");

        var hint = state.mode === "a4"
          ? "A4 용지에 10장(2x5) 배치, 점선 따라 재단. 일반 프린터용."
          : "카드 1장 = 페이지 1장 (86 x 54mm 가로). 카드 프린터/인쇄소용.";
        if (state.side !== "both" && state.mode === "card") {
          hint += " 단면 프린터는 앞면만 → 뒤집어 재투입 → 뒷면만 순서로 2패스.";
        }
        if (state.side === "both") {
          hint += state.mode === "a4"
            ? " 앞면 페이지 → 뒷면 페이지 순서, 뒷면은 좌우 반전 배치(뒤집어 재급지)."
            : " 앞1 → 뒤1 → 앞2 → 뒤2 … 순서.";
        }
        if (state.design === "on" && missing.length) {
          hint += " ⚠ 디자인 파일 없음(" + missing.join(", ") + ") — /images/card/front.png, back.png 를 넣어주세요.";
        }
        var hintEl = document.getElementById("modeHint");
        hintEl.textContent = hint;
        hintEl.className = "hint" + (state.design === "on" && missing.length ? " warn" : "");

        document.getElementById("pageRule").textContent = state.mode === "a4"
          ? "@page { size: A4 portrait; margin: 8mm; }"
          : "@page { size: 85.6mm 53.98mm; margin: 0; }";
      }

      function apply() { syncBody(); render(); }

      function bind(attr, key) {
        document.querySelectorAll(".toolbar [data-" + attr + "]").forEach(function (b) {
          b.addEventListener("click", function () {
            state[key] = b.dataset[attr];
            document.querySelectorAll(".toolbar [data-" + attr + "]").forEach(function (x) {
              x.classList.toggle("active", x === b);
            });
            apply();
          });
        });
      }
      bind("mode", "mode");
      bind("side", "side");
      bind("design", "design");
      bind("flip", "flip");

      // 위치 보정 입력 — 값이 바뀔 때마다 즉시 화면에 반영하고 저장한다(다시 열어도 유지).
      var adjInputs = {
        x: document.getElementById("adjX"),
        y: document.getElementById("adjY"),
        s: document.getElementById("adjS"),
        b: document.getElementById("adjB"),
      };
      function syncAdjInputs() {
        adjInputs.x.value = adj.x;
        adjInputs.y.value = adj.y;
        adjInputs.s.value = adj.s;
        adjInputs.b.value = adj.b;
      }
      Object.keys(adjInputs).forEach(function (k) {
        adjInputs[k].addEventListener("input", function () {
          var v = parseFloat(adjInputs[k].value);
          adj[k] = isNaN(v) ? (k === "s" || k === "b" ? 100 : 0) : v;
          applyAdjust();
          saveAdjust();
        });
      });
      document.getElementById("btnGuide").addEventListener("click", function () {
        state.guide = !state.guide;
        this.classList.toggle("active", state.guide);
        syncBody();
      });
      document.getElementById("btnAdjReset").addEventListener("click", function () {
        adj = { x: 0, y: 0, s: 100, b: 100 };
        syncAdjInputs();
        applyAdjust();
        saveAdjust();
      });

      document.getElementById("btnDoPrint").addEventListener("click", function () {
        window.focus();
        window.print();
      });

      loadAdjust();
      syncAdjInputs();
      applyAdjust();

      var pending = 2;
      function afterProbe() { pending -= 1; if (pending === 0) apply(); }
      probe("front", DATA.design.front, afterProbe);
      probe("back", DATA.design.back, afterProbe);
    })();
  <\/script>
</body>
</html>`);
  win.document.close();
}

function switchTab(tab) {
  if (!currentDetail) return;

  document.querySelectorAll("#studentTabs button[data-tab]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.tab === tab);
  });

  const renderers = {
    all: renderAllTab,
    basic: renderBasicTab,
    assign: renderAssignTab,
    reading: renderReadingTab,
    reservation: renderReservationTab,
  };

  document.getElementById("studentModalBody").innerHTML = renderers[tab](currentDetail);

  if (tab === "assign") bindAssignTab();
}

/* 상태 코드 → all_pass 상태버튼(재원중/입학취소/휴원/탈퇴) 매핑. book_clinic은 "이용중/탈퇴" 2단계뿐이라
   재원중/탈퇴만 실제로 쓰이고 나머지 둘은 모양만 맞춰 흐리게 둔다 */
function statusButtons(status) {
  const map = [
    { key: "이용중", label: "재원중", cls: "active" },
    { key: "입학취소", label: "입학취소", cls: "cancel" },
    { key: "휴원", label: "휴원", cls: "closed" },
    { key: "탈퇴", label: "탈퇴", cls: "danger" },
  ];
  return `
    <div class="status-buttons">
      ${map.map((m) => `<button type="button" class="btn-status ${m.cls} ${m.key === status ? "selected" : ""}">${m.label}</button>`).join("")}
    </div>
  `;
}

/* 전체 탭 헤더용 — 4단계 버튼 그룹 대신 현재 상태 하나만 재원중/탈퇴 색으로 표시 */
function singleStatusPill(status) {
  const cls = status === "탈퇴" ? "danger" : "active";
  return `<span class="status-chip ${cls}">${status}</span>`;
}

function statusLabel(statusKey) {
  return statusKey === "WITHDRAWN" ? "탈퇴" : "이용중";
}

function reservationStatusPill(status) {
  const map = {
    RESERVED: { label: "예약완료", cls: "resv-scheduled" },
    ATTENDED: { label: "이용완료", cls: "resv-done" },
    CANCELED: { label: "취소", cls: "resv-scheduled" },
    NOSHOW: { label: "노쇼", cls: "resv-scheduled" },
  };
  const m = map[status] ?? { label: status ?? "-", cls: "resv-scheduled" };
  return `<span class="resv-pill ${m.cls}">${m.label}</span>`;
}

function readingResultBadge(row) {
  // 2026-08-28 — 첫 제출 뒤 status는 항상 DONE이므로 grade로 가른다(재도전 최종 결과로 갱신됨).
  if (row.grade === "KING") return { key: "king", label: "독서왕" };
  if (row.grade === "FRIEND") return { key: "pass", label: "통과" };
  return { key: "retry", label: "재도전" };
}

/* 기본문제 점수 — 재도전으로 최종 점수가 처음 점수와 달라졌으면 "처음→최종"으로 함께 보여준다(2026-08-28) */
function basicScoreText(row) {
  const first = row.basicCorrectCnt ?? 0;
  const total = row.basicTotalCnt ?? 0;
  const last = row.basicFinalCorrectCnt;
  if (last != null && last !== row.basicCorrectCnt) return `${first}→${last}/${total}`;
  return `${first}/${total}`;
}

/* ===================== 탭별 렌더링 ===================== */

function emptyState(text, full) {
  const cls = full ? "empty-state empty-state-tab" : "empty-state";
  return `<div class="${cls}"><p class="empty-state-text">${escapeHtml(text)}</p></div>`;
}

function renderAllTab(d) {
  const reservations = d.reservations ?? [];
  const readingHistory = d.readingHistory ?? [];
  return `
    <div class="all-info">
      <div class="card-left">
        <article class="card-common card-compact">
          <header class="card-header">
            <h4 class="sub-themes">기본 정보</h4>
            ${singleStatusPill(statusLabel(d.statusKey))}
          </header>
          <section class="card-body">
            <figure class="profile-img">
              <img src="/images/stu-img.png" alt="학생 프로필 이미지">
            </figure>
            <div>
              <h5 class="student-name">${escapeHtml(d.studentName)}</h5>
              <p class="student-tel">${escapeHtml(d.billingPhone ?? "-")}</p>
            </div>
          </section>
          <dl class="student-details">
            <div class="detail-row"><dt>입회일</dt><dd>${escapeHtml(formatKoreanDate(d.registeredAt))}</dd></div>
            <div class="detail-row"><dt>학교</dt><dd>${escapeHtml(d.school ?? "-")}</dd></div>
            <div class="detail-row"><dt>학년</dt><dd>${escapeHtml(d.gradeName ?? "-")}</dd></div>
            <div class="detail-row"><dt>생년월일</dt><dd>${escapeHtml(formatKoreanDate(d.birth))}</dd></div>
            <div class="detail-row"><dt>주소</dt><dd>${escapeHtml(d.address ?? "-")}</dd></div>
            <div class="detail-row"><dt>상세주소</dt><dd>${escapeHtml(d.addressDetail ?? "-")}</dd></div>
          </dl>
        </article>

        <article class="card-common card-grow">
          <h4 class="sub-themes">예약 현황</h4>
          <table class="basic-table table-head-fixed">
            <colgroup><col style="width:50%"><col style="width:50%"></colgroup>
            <thead><tr><th>날짜</th><th>상태</th></tr></thead>
          </table>
          <div class="table-frame">
            ${reservations.length ? `
              <table class="basic-table">
                <colgroup><col style="width:50%"><col style="width:50%"></colgroup>
                <tbody>
                  ${reservations.map((r) => `
                    <tr><td>${escapeHtml(r.serviceDate)}</td><td>${reservationStatusPill(r.status)}</td></tr>
                  `).join("")}
                </tbody>
              </table>
            ` : emptyState("예약된 일정이 없어요")}
          </div>
        </article>
      </div>

      <div class="card-right">
        <div class="card-common">
          <h4 class="sub-themes">독서 현황</h4>
          <div class="stat-tiles">
            <div class="stat-tile">
              <img class="stat-icon" src="/images/student_result/passport.png" alt="">
              <div class="stat-label">누적 독서</div>
              <div class="stat-value">${d.totalDoneBooks ?? 0}권</div>
            </div>
            <div class="stat-tile">
              <img class="stat-icon" src="${d.medalImg ?? "/images/student_result/medal.png"}" alt="">
              <div class="stat-label">현재 레벨</div>
              <div class="stat-value">Lv. ${d.levelNo ?? 1}</div>
            </div>
            <div class="stat-tile">
              <img class="stat-icon" src="/images/student_result/trophy.png" alt="">
              <div class="stat-label">독서왕 횟수</div>
              <div class="stat-value">${d.kingCount ?? 0}회</div>
            </div>
            <div class="stat-tile">
              <img class="stat-icon" src="/images/badge-1-01.png" alt="">
              <div class="stat-label">획득 뱃지</div>
              <div class="stat-value">${d.badgeCount ?? 0}개</div>
            </div>
          </div>
        </div>

        <div class="card-common card-grow">
          <h4 class="sub-themes">최근 읽은 책</h4>
          <div class="recent-book-scroll">
            ${readingHistory.length ? `
              <ul class="recent-book-list">
                ${readingHistory.slice(0, 4).map((b) => {
                  const result = readingResultBadge(b);
                  return `
                  <li>
                    <div class="recent-book-thumb"></div>
                    <div class="recent-book-info">
                      <div class="recent-book-title">${escapeHtml(b.bookName)}</div>
                      <div class="recent-book-meta">
                        ${escapeHtml(b.recordDate)} &nbsp;|&nbsp; 기본문제 ${basicScoreText(b)} &nbsp;|&nbsp; 심화문제 ${b.advancedTotalCnt ? `${b.advancedCorrectCnt ?? 0}/${b.advancedTotalCnt}` : "-"} &nbsp;|&nbsp;
                        <span class="result-text result-${result.key}">${result.label}</span>
                      </div>
                    </div>
                  </li>
                `;
                }).join("")}
              </ul>
            ` : emptyState("읽은 책이 없어요")}
          </div>
        </div>
      </div>
    </div>
  `;
}

function renderBasicTab(d) {
  const genderLabel = d.gender === true ? "남자" : d.gender === false ? "여자" : "-";
  return `
    <table class="base-info-t">
      <tbody>
        <tr>
          <th>이름</th>
          <td><input type="text" value="${escapeHtml(d.studentName)}" readonly></td>
          <th>상태</th>
          <td>${statusButtons(statusLabel(d.statusKey))}</td>
        </tr>
        <tr>
          <th>생년월일</th>
          <td class="icon-field">${escapeHtml(d.birth ?? "-")} <i class="fa-regular fa-calendar icon-btn"></i></td>
          <th>성별</th>
          <td class="choose-group">
            <span class="btn-choose ${genderLabel === "남자" ? "active" : ""}">남자</span>
            <span class="btn-choose ${genderLabel === "여자" ? "active" : ""}">여자</span>
          </td>
        </tr>
        <tr>
          <th>학교</th>
          <td class="icon-field"><input type="text" value="${escapeHtml(d.school ?? "-")}" readonly> <i class="fa-solid fa-magnifying-glass icon-btn"></i></td>
          <th>주소</th>
          <td class="icon-field"><input type="text" value="${escapeHtml(d.address ?? "-")}" readonly> <i class="fa-solid fa-magnifying-glass icon-btn"></i></td>
        </tr>
        <tr>
          <th>학년</th>
          <td>${escapeHtml(d.gradeName ?? "-")}</td>
          <th>상세주소</th>
          <td><input type="text" value="${escapeHtml(d.addressDetail ?? "-")}" readonly></td>
        </tr>
        <tr>
          <th>부모님HP</th>
          <td><input type="tel" value="${escapeHtml(d.billingPhone ?? "-")}" readonly></td>
          <th>학생과의 관계</th>
          <td class="choose-group" title="DB에 저장하는 값이 없어 선택 표시를 하지 않습니다">
            <span class="btn-choose">부</span>
            <span class="btn-choose">모</span>
            <span class="btn-choose">기타</span>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="save-btn-frame">
      <button type="submit" class="save-btn" disabled title="저장 API 연동 전이라 저장은 아직 동작하지 않습니다">저장</button>
    </div>
  `;
}

/*
 * 수강 정보 탭 — erp_bookstore_assign 1행을 읽고 쓴다 (2026-09-15).
 * "독서 레벨"은 저장 시점에 서버가 찍는 자동계산 레벨 스냅샷이라 화면에선 읽기 전용이다.
 * 미수강을 고르면 아래 미수강 행(일자/사유)이 펼쳐지고, 수강으로 되돌리면 접히면서 저장 시 값이 비워진다.
 */
function renderAssignTab(d) {
  const a = d.assign ?? {};
  const inactive = a.state === false;
  return `
    <div class="small-title">수강과목(독서)</div>
    <table class="base-info-t">
      <colgroup><col style="width:15%"><col style="width:35%"><col style="width:15%"><col style="width:35%"></colgroup>
      <tbody>
        <tr>
          <th>독서 레벨</th>
          <td>${a.level != null ? `Lv. ${a.level}` : "-"}</td>
          <th>수강 상태</th>
          <td class="choose-group" id="assignStateGroup">
            <label class="btn-choose ${inactive ? "" : "active"}">
              <input type="radio" name="assignState" value="1" ${inactive ? "" : "checked"}>수강
            </label>
            <label class="btn-choose ${inactive ? "active" : ""}">
              <input type="radio" name="assignState" value="0" ${inactive ? "checked" : ""}>미수강
            </label>
          </td>
        </tr>
        <tr class="assign-inactive-row" ${inactive ? "" : "hidden"}>
          <th>미수강 사유</th>
          <td><input type="text" id="assignInactiveReason" maxlength="200" placeholder="사유를 입력하세요."
                     value="${escapeHtml(a.inactiveReason ?? "")}"></td>
          <th>미수강 일자</th>
          <td><input type="date" id="assignInactiveDate" value="${escapeHtml(a.inactiveDate ?? "")}"></td>
        </tr>
        <tr>
          <th>교육비</th>
          <td><input type="number" id="assignEduFee" min="0" step="1000" placeholder="원"
                     value="${a.eduFee != null ? a.eduFee : ""}"></td>
          <th>시작일자</th>
          <td><input type="date" id="assignEntryDate" value="${escapeHtml(a.entryDate ?? "")}"></td>
        </tr>
      </tbody>
    </table>
    ${a.saved === false ? `<p class="mock-note">※ 아직 저장된 수강 정보가 없습니다. 아래 값은 기본값이며 저장해야 반영됩니다.</p>` : ""}
    <div class="save-btn-frame">
      <button type="button" class="save-btn" id="btnSaveAssign">저장</button>
    </div>
  `;
}

/* 수강 정보 탭은 innerHTML 로 매번 새로 그리므로 렌더 직후 이벤트를 다시 건다 (switchTab 참고) */
function bindAssignTab() {
  const group = document.getElementById("assignStateGroup");
  if (!group) return;

  group.addEventListener("change", () => {
    const attending = group.querySelector('input[name="assignState"]:checked').value === "1";
    group.querySelectorAll(".btn-choose").forEach((label) => {
      label.classList.toggle("active", label.querySelector("input").checked);
    });
    document.querySelector(".assign-inactive-row").hidden = attending;
  });

  document.getElementById("btnSaveAssign").addEventListener("click", saveAssign);
}

async function saveAssign() {
  const btn = document.getElementById("btnSaveAssign");
  const attending = document.querySelector('input[name="assignState"]:checked').value === "1";
  const eduFee = document.getElementById("assignEduFee").value.trim();

  const payload = {
    state: attending,
    eduFee: eduFee === "" ? null : Number(eduFee),
    entryDate: document.getElementById("assignEntryDate").value || null,
    inactiveDate: attending ? null : (document.getElementById("assignInactiveDate").value || null),
    inactiveReason: attending ? null : document.getElementById("assignInactiveReason").value.trim(),
  };

  btn.disabled = true;
  try {
    currentDetail.assign = await putJson(
      `/admin/students/${encodeURIComponent(currentDetail.studentId)}/bookstore-assign`, payload);
    switchTab("assign");   // 저장된 값(레벨 스냅샷 포함)으로 다시 그린다
    alert("저장되었습니다.");
  } catch (e) {
    console.error("수강 정보 저장 실패", e);
    alert(e.message);
    btn.disabled = false;
  }
}

function renderReadingTab(d) {
  const history = d.readingHistory ?? [];
  if (!history.length) {
    return emptyState("기록된 독서 이력이 없어요", true);
  }
  return `
    <table class="history-table">
      <thead>
        <tr><th>No</th><th>날짜</th><th>도서명</th><th>기본 문제</th><th>심화 문제</th><th>결과</th><th>기타 전달사항</th></tr>
      </thead>
      <tbody>
        ${history.map((h, idx) => {
          const result = readingResultBadge(h);
          return `
          <tr>
            <td class="col-no">${history.length - idx}</td>
            <td>${escapeHtml(h.recordDate)}</td>
            <td class="col-title">${escapeHtml(h.bookName)}</td>
            <td>${basicScoreText(h)}</td>
            <td>${h.advancedTotalCnt ? `${h.advancedCorrectCnt ?? 0}/${h.advancedTotalCnt}` : "-"}</td>
            <td><span class="result-badge result-${result.key}">${result.label}</span></td>
            <td class="col-note">${escapeHtml(h.note ?? "")}</td>
          </tr>
        `;
        }).join("")}
      </tbody>
    </table>
  `;
}

function renderReservationTab(d) {
  const reservations = d.reservations ?? [];
  if (!reservations.length) {
    return emptyState("예약된 일정이 없어요", true);
  }
  return `
    <table class="history-table">
      <thead><tr><th>날짜</th><th>회차</th><th>상태</th></tr></thead>
      <tbody>
        ${reservations.map((r) => `
          <tr>
            <td>${escapeHtml(r.serviceDate)}</td>
            <td>${r.seq ?? "-"}</td>
            <td>${reservationStatusPill(r.status)}</td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
}
