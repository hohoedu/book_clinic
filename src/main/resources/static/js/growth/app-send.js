// 독서 결과 발송 — 그날 회차별 학생의 정독 결과를 앱으로 내보내는 화면 (2026-09-22).
//
// 목록은 GET /admin/growth/app-send/list, 오른쪽 미리보기는 GET /admin/growth/app-send/report.
// 미리보기는 앱(i-with)의 "정독 결과" 화면을 그대로 축소해 그린다 — 응답이 앱의
// POST /app/bookstore/report 와 같은 조립(AppReportService)이라 두 화면이 갈라지지 않는다.
// 다른 점은 둘뿐이다: 발송 전 일지도 보고(sentOnly=false), 날짜가 툴바에서 고른 하루로
// 고정이라 앱 화면 상단의 일자 탭이 없다 (2026-09-23).

/* 상태 3종.
   발송 완료 / 미발송은 erp_bookstore_diary.is_send 그대로다(예약 발송은 쓰지 않는다).
   앱 미연동은 학생에게 app_token 이 없는 경우 — 보낼 곳이 없어 발송 대상에서 빠진다.
   DB 값이 아니라 발송 가능 여부라 미발송과 따로 표시한다 (2026-09-23). */
const SEND_STATUS = {
  SENT: { text: "발송 완료", className: "status-sent" },
  NONE: { text: "미발송", className: "status-none" },
  NO_APP: { text: "앱 미연동", className: "status-noapp" },
};

const DEFAULT_COVER = "/images/book-sample.png";

/* 뱃지 아이콘은 category로 고른다 — badge_id는 뱃지 4종↔5종 재편으로 두 번 재번호된 전력이
   있어(AppRespDTO.ReportBadgeDTO 주석) 이미지 매핑에 쓰면 개편 때마다 그림이 어긋난다. */
const BADGE_ICON = {
  BASIC_FAIL: "/images/icons/badge_1.png",
  BASIC_PASS: "/images/icons/badge_2.png",
  BASIC_PERFECT: "/images/icons/badge_3.png",
  ADV_PASS: "/images/icons/badge_4.png",
  ADV_PERFECT: "/images/icons/badge_5.png",
};

/* 영역 색 — 앱(i-with)의 bubbleColors / bubbleTextColors 를 그대로 옮긴 값이다.
   버블 차트와 독서 성향 벤 다이어그램이 이 맵 하나를 같이 쓴다 — 같은 영역이 두 그림에서
   다른 색이면 같은 영역인 줄 모른다.

   앱이 영역 이름으로 매핑하고 있어 여기서도 이름으로 건다(코드가 아니라). 앱 쪽 맵이
   바뀌었을 때 두 줄을 나란히 놓고 비교할 수 있어야 해서다 — 맞춰야 할 원본이 앱이다.

   심화 유형(어휘심화/문법심화)은 없다. 버블 차트는 기본 문제만 세기 때문에
   (AppMapper.selectBookstoreReportTendencies 가 qlevel='01' 만 본다) 여기 올 일이 없다. */
const TENDENCY_COLOR = {
  이해: { fill: "#DAF7BE", text: "#629A2C" },
  표현: { fill: "#BBE5F8", text: "#49849E" },
  어휘: { fill: "#E9D7F4", text: "#8B60A5" },
  감정: { fill: "#F8EAB4", text: "#AE8012" },
  사고: { fill: "#C0F3DC", text: "#2FA26E" },
  논리: { fill: "#FAE3E0", text: "#A56860" },
  지식: { fill: "#BEC9FB", text: "#586ABC" },
};
/* 코드표에 영역이 새로 생겼는데 위 맵에 없을 때 쓰는 색 — 회색으로 떨어지면 매핑 누락 신호다 */
const TENDENCY_FALLBACK = { fill: "#E8EDEF", text: "#4F5A60" };

/* 뱃지는 계열별로 "지금 어디까지 왔나" 한 칸씩만 보여준다 (2026-09-23).
   5종을 다 늘어놓으면 회색 칸이 더 많아져서, 달성한 것보다 못 한 것이 먼저 읽힌다.
   각 배열은 낮은 등급 → 높은 등급 순이고, 획득한 것 중 가장 높은 등급이 그 계열의 얼굴이 된다.
   하나도 못 받았으면 그 계열의 최고 등급을 회색으로 둔다 — 다음 목표를 가리키는 자리다. */
const BADGE_GROUPS = [
  ["BASIC_FAIL", "BASIC_PASS", "BASIC_PERFECT"],   // 완독 → 정독완료 → 정독왕
  ["ADV_PASS", "ADV_PERFECT"],                     // 문해력챌린저 → 문해력챔피언
];

/* 영역 아이콘 — 학생 문제풀이 화면(student-question.js QTYPE_INFO)과 같은 매핑이다.
   이름이 아니라 코드로 고른다: 이름은 코드표(erp_bookstore_code gubun='T')에서 바뀔 수 있고,
   06이 어휘/어휘심화로 갈렸던 전례도 있어 이름으로 걸면 조용히 어긋난다 (2026-09-23). */
const TENDENCY_ICON = {
  "01": "comp.png",          // 이해
  "02": "expr.png",          // 표현
  "03": "logic.png",         // 논리
  "04": "think.png",         // 사고
  "05": "emo.png",           // 감정
  "06": "voca.png",          // 어휘
  "07": "know.png",          // 지식
  "08": "advance_voca.png",  // 어휘심화
  "09": "advance_gram.png",  // 문법심화
};

/* 독서 성향 문구와 아이콘 — 점수 상위 3개 영역을 이 문구로 바꿔 보여준다.
   서버 리포트에 문구 필드가 없어(영역별 점수만 내려온다) 화면에서 영역 코드로 고른다.
   이름이 아니라 코드로 거는 이유는 TENDENCY_ICON 과 같다.

   02/03/04 는 앱 화면에서 확인한 문구다. 나머지는 아직 못 봐서 임시값이다
   — TODO: 앱 문구를 확인해 맞춘다 (2026-09-23). */
const TENDENCY_TITLE = {
  "01": { title: "이야기 이해왕", icon: "fa-regular fa-file-lines" },        // 이해 (임시)
  "02": { title: "표현의 연금술사", icon: "fa-solid fa-flask" },             // 표현
  "03": { title: "냉철한 분석가", icon: "fa-solid fa-scale-balanced" },      // 논리
  "04": { title: "주제를 찾는 탐구자", icon: "fa-solid fa-magnifying-glass" }, // 사고
  "05": { title: "감성적인 독서가", icon: "fa-regular fa-face-smile" },      // 감정 (임시)
  "06": { title: "걸어다니는 어휘사전", icon: "fa-solid fa-book" },          // 어휘 (임시)
  "07": { title: "지식을 넓히는 탐험가", icon: "fa-solid fa-globe" },        // 지식 (임시)
};

/* 버블 캔버스 — 자리를 미리 잡지 않고 값에서 크기를 내고 겹치지 않게 앉힌다 (2026-09-23).
   pad 는 가장자리 여백, gap 은 버블 사이 최소 간격. fill 은 캔버스 면적 중 버블이 차지할
   비율인데, 원을 사각형에 담으면 이론상 한계가 약 0.9 라 그보다 넉넉히 낮춰 잡는다. */
const BUBBLE_VIEW = { w: 300, h: 295, pad: 3, gap: 5, fill: 0.5 };

// 조회 결과 전체. 회차·검색어 필터는 독서일지와 같은 이유로 화면에서 거른다
// (센터마다 회차 수가 달라 드롭다운을 그날 데이터에서 만들어야 한다).
let allRows = [];
let selectedRowId = null;
let selectedBookIndex = 0;

document.addEventListener("DOMContentLoaded", () => {
  initDatePicker();
  initSlotPicker();
  initSearchControls();
  initSelectionControls();
  initSendButtons();
  loadSendList();
});

/* ── 툴바 ───────────────────────────────────────────── */

function todayStr(d = new Date()) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function initDatePicker() {
  const dateInput = document.getElementById("sendDate");
  const dateDisplay = document.getElementById("sendDateDisplay");
  if (!dateInput || !dateDisplay) return;

  dateInput.value = todayStr();
  updateDateDisplay(dateInput, dateDisplay);

  dateInput.closest(".monitor-date-trigger").addEventListener("click", () => {
    if (typeof dateInput.showPicker === "function") dateInput.showPicker();
  });

  dateInput.addEventListener("change", () => {
    updateDateDisplay(dateInput, dateDisplay);
    loadSendList();
  });
}

function updateDateDisplay(input, display) {
  const [y, m, d] = input.value.split("-");
  display.textContent = `${y}. ${m}. ${d}`;
}

function initSlotPicker() {
  const select = document.getElementById("sendSlot");
  select.innerHTML = `<option value="">전체</option>`;
  select.addEventListener("change", renderTable);
}

/* 회차 드롭다운은 서버가 내려준 그날의 회차 전체로 채운다 — 그 회차에 학생이 한 명도 없어도
   선택지에는 떠 있어야 한다(없는 회차인지 학생이 없는 회차인지 구분이 돼야 해서).
   이미 고른 회차가 새 목록에도 있으면 유지한다. */
function refreshSlotOptions(slots) {
  const select = document.getElementById("sendSlot");
  const current = select.value;

  select.innerHTML = `<option value="">전체</option>`;
  (slots ?? []).forEach((slot) => {
    const option = document.createElement("option");
    option.value = String(slot.seq);
    option.textContent = slot.label;
    select.appendChild(option);
  });

  select.value = (slots ?? []).some((s) => String(s.seq) === current) ? current : "";
}

function initSearchControls() {
  document.getElementById("btnSearchSend").addEventListener("click", loadSendList);
  document.getElementById("sendStudentKeyword").addEventListener("keydown", (e) => {
    if (e.key === "Enter") loadSendList();
  });
  // "내 담당 학생만 보기"는 아직 걸 수 없다 — 학생-직원을 잇는 담당 컬럼이 DB에 없다.
  // 체크박스는 HTML에서 비활성으로 두고, 컬럼이 생기면 여기서 조회 파라미터에 실어 보낸다.
}

/* 화면에 실제로 그릴 행 — 회차/검색어로 거른 결과 */
function visibleRows() {
  const slot = document.getElementById("sendSlot").value;
  const keyword = document.getElementById("sendStudentKeyword").value.trim();

  return allRows.filter((row) => {
    if (slot && String(row.slotSeq) !== slot) return false;
    if (keyword && !row.studentName.includes(keyword)) return false;
    return true;
  });
}

/* ── 목록 ───────────────────────────────────────────── */

async function getJson(url) {
  const response = await fetch(url);
  const data = await response.json();
  if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");
  return data.response;
}

const CSRF_HEADER = "X-XSRF-TOKEN";
function getCsrfToken() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}

async function postJson(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", [CSRF_HEADER]: getCsrfToken() },
    body: JSON.stringify(body),
  });
  const data = await response.json();
  if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");
  return data.response;
}

/* 조회 — 날짜/학생명은 서버에서 거르고, 회차는 받아온 행에서 화면이 거른다(독서일지와 같음).
   회차만 바꾸는 데 서버를 다시 다녀올 이유가 없다. */
async function loadSendList() {
  const date = document.getElementById("sendDate").value;
  const keyword = document.getElementById("sendStudentKeyword").value.trim();

  // 스크롤·레이아웃 확인용 임시 스위치 — ?mock=15 처럼 붙이면 서버 대신 그 수만큼 가짜 행을 만든다.
  // 실데이터가 쌓이면 지운다 (2026-09-22)
  const mockCount = Number(new URLSearchParams(location.search).get("mock"));
  if (mockCount > 0) {
    refreshSlotOptions([
      { seq: 1, label: "1교시(14:00 ~ 15:00)" },
      { seq: 2, label: "2교시(15:10 ~ 16:10)" },
    ]);
    allRows = buildMockRows(mockCount);
    renderTable();
    return;
  }

  const params = new URLSearchParams({ date });
  if (keyword) params.set("keyword", keyword);

  try {
    const res = await getJson(`/admin/growth/app-send/list?${params}`);
    refreshSlotOptions(res.slots);
    allRows = (res.rows ?? []).map(toRow);
  } catch (e) {
    allRows = [];
    await customConfirm(e.message, { confirmText: "확인", cancelText: "닫기" });
  }
  renderTable();
}

/* ?mock=N 전용 — 화면 확인용 가짜 행 (2026-09-22, 실데이터 확인 후 삭제).
   목록 레이아웃만 보는 스위치다 — 없는 학생이라 미리보기는 조회되지 않는다. */
function buildMockRows(count) {
  const names = ["김호호", "이하니", "박부키", "최책방", "송하나", "강도서", "윤읽기", "김독서"];
  const titles = ["강아지 똥", "흥부전", "홍길동전", "아낌없이 주는 나무", "은혜갚은 호랑이"];

  return Array.from({ length: count }, (_, i) => {
    const seq = (i % 2) + 1;
    const sent = i % 3 !== 0;
    return {
      id: i + 1,
      studentId: `MOCK-${i + 1}`,
      studentName: `${names[i % names.length]}${i < names.length ? "" : i}`,
      grade: `초${(i % 6) + 1}`,
      slotSeq: seq,
      slotLabel: seq === 1 ? "1교시(14:00 ~ 15:00)" : "2교시(15:10 ~ 16:10)",
      diaryKey: i + 1,
      hasAppToken: true,
      books: [{ bookTitle: titles[i % titles.length] }, { bookTitle: titles[(i + 1) % titles.length] }],
      status: sent ? "SENT" : "NONE",
      sentAt: sent ? "2026-09-11 10:40" : null,
      report: null,
    };
  });
}

/* 서버 행 → 화면 행. 발송 상태는 일지의 is_send 하나뿐이라 발송/미발송 두 값으로만 갈린다. */
function toRow(r) {
  return {
    id: r.reservationId,
    studentId: r.studentId,
    studentName: r.studentName,
    grade: r.gradeName ?? "-",
    slotSeq: r.slotSeq,
    slotLabel: r.slotLabel,
    diaryKey: r.diaryKey,
    hasAppToken: r.hasAppToken,
    books: r.books ?? [],
    // 앱 미연동이 미발송보다 앞선다 — 보낼 곳이 없는 건 "아직 안 보냈다"와 다른 이야기다.
    // 이미 발송된 뒤 앱을 지운 경우는 발송 완료 그대로 둔다(그때는 실제로 나갔다).
    status: r.isSend ? "SENT" : r.hasAppToken ? "NONE" : "NO_APP",
    sentAt: r.sendAt,
    // 미리보기 리포트는 학생을 처음 고를 때 받아 여기 담아 둔다(같은 학생을 다시 눌러도 재조회 없음)
    report: null,
  };
}

function renderTable() {
  const tbody = document.getElementById("sendTableBody");
  const rows = visibleRows();

  if (!rows.length) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="9">발송 목록에 학생이 없습니다.</td></tr>`;
    clearPreview();
    updateCount(rows);
    syncCheckAll();
    return;
  }

  tbody.innerHTML = rows
    .map((row, index) => {
      const status = SEND_STATUS[row.status] ?? SEND_STATUS.NONE;
      const books = row.books.map((b) => b.bookTitle).join(", ") || "-";
      return `
        <tr data-id="${row.id}" class="${row.id === selectedRowId ? "selected" : ""}">
          <td class="col-check"><input type="checkbox" class="row-check" data-id="${row.id}" /></td>
          <td class="col-no">${index + 1}</td>
          <td class="col-slot">${escapeHtml(row.slotLabel)}</td>
          <td class="col-student">${escapeHtml(row.studentName)}</td>
          <td class="col-grade">${escapeHtml(row.grade)}</td>
          <td class="col-books">${escapeHtml(books)}</td>
          <td class="col-status"><span class="send-badge ${status.className}">${status.text}</span></td>
          <td class="col-sent-at">${row.sentAt ?? "-"}</td>
          <td class="col-action"><button type="button" class="btn view-btn" data-id="${row.id}">보기</button></td>
        </tr>`;
    })
    .join("");

  // 선택돼 있던 학생이 필터 밖으로 나갔으면 미리보기를 비운다
  if (selectedRowId && !rows.some((r) => r.id === selectedRowId)) clearPreview();

  updateCount(rows);
  syncCheckAll();
}

function updateCount(rows) {
  // 앱 미연동도 "아직 안 나간" 건이라 미발송에 같이 센다 — 숫자는 상태 표시와 맞아야 한다
  const notSent = rows.filter((r) => r.status !== "SENT").length;
  document.getElementById("sendCount").innerHTML = `미발송 <b>${notSent}</b>/${rows.length}명`;
}

/* ── 선택 (행 클릭 = 미리보기, 체크박스 = 발송 대상) ── */

function initSelectionControls() {
  const tbody = document.getElementById("sendTableBody");

  tbody.addEventListener("click", (e) => {
    // 체크박스는 발송 대상 선택이라 미리보기를 건드리지 않는다
    if (e.target.classList.contains("row-check")) {
      syncCheckAll();
      return;
    }
    const tr = e.target.closest("tr[data-id]");
    if (!tr) return;
    selectRow(Number(tr.dataset.id));
  });

  document.getElementById("checkAll").addEventListener("change", (e) => {
    document.querySelectorAll(".row-check").forEach((cb) => (cb.checked = e.target.checked));
  });
}

/* 전체선택 체크박스를 행 상태에 맞춘다(하나라도 빠지면 해제) */
function syncCheckAll() {
  const boxes = [...document.querySelectorAll(".row-check")];
  const checkAll = document.getElementById("checkAll");
  checkAll.checked = boxes.length > 0 && boxes.every((cb) => cb.checked);
}

function checkedIds() {
  return [...document.querySelectorAll(".row-check:checked")].map((cb) => Number(cb.dataset.id));
}

async function selectRow(id) {
  selectedRowId = id;
  selectedBookIndex = 0;

  document.querySelectorAll("#sendTableBody tr").forEach((tr) => {
    tr.classList.toggle("selected", Number(tr.dataset.id) === id);
  });

  const row = allRows.find((r) => r.id === id);
  if (!row) return;

  if (row.report) {
    renderReport(row.report);
    return;
  }

  showPreviewMessage("발송 내용을 불러오는 중입니다.");
  try {
    const params = new URLSearchParams({
      studentId: row.studentId,
      date: document.getElementById("sendDate").value,
    });
    const report = await getJson(`/admin/growth/app-send/report?${params}`);
    row.report = report;

    // 불러오는 사이에 다른 학생을 눌렀으면 그 화면을 덮지 않는다
    if (selectedRowId !== id) return;
    renderReport(report);
  } catch (e) {
    if (selectedRowId !== id) return;
    showPreviewMessage(e.message);
  }
}

function showPreviewMessage(text) {
  document.getElementById("sendPreview").innerHTML =
    `<div class="send-preview-empty"><p>${escapeHtml(text)}</p></div>`;
}

function clearPreview() {
  selectedRowId = null;
  selectedBookIndex = 0;
  document.getElementById("sendPreview").innerHTML = `
    <div class="send-preview-empty"><p>학생을 선택하면 발송 내용이 표시됩니다.</p></div>`;
}

/* ── 발송 ───────────────────────────────────────────── */

function initSendButtons() {
  document.getElementById("btnSendSelected").addEventListener("click", async () => {
    const ids = checkedIds();
    const rows = allRows.filter((r) => ids.includes(r.id));
    if (!rows.length) {
      await customConfirm("발송할 학생을 선택해 주세요.", { confirmText: "확인", cancelText: "닫기" });
      return;
    }
    sendRows(rows);
  });

  document.getElementById("btnSendAll").addEventListener("click", () => {
    // "전체"는 지금 화면에 걸린 필터 안의 미발송 학생까지다 — 조회하지 않은 회차까지 나가면 곤란하다
    sendRows(visibleRows().filter((r) => r.status === "NONE"));
  });
}

/**
 * 발송 — 서버는 일지의 is_send 를 1 로 올린다. 그 플래그가 곧 발송이다(앱 조회가 1만 본다).
 *
 * 보내기 전에 화면에서 두 종류를 걸러 낸다. 서버로 보내도 바뀌는 게 없거나(이미 발송됨,
 * 일지 없음) 보내 봐야 학부모가 볼 수 없는(앱 미가입) 행이라, 직원에게 이유를 알려 주는 편이
 * 조용히 빠지는 것보다 낫다.
 */
async function sendRows(rows) {
  // status NONE 이면 앱 미연동은 이미 걸러져 있다(NO_APP). 남은 건 일지 유무뿐이다.
  const sendable = rows.filter((r) => r.status === "NONE" && r.diaryKey != null);
  const skipped = rows.length - sendable.length;

  if (!sendable.length) {
    await customConfirm(sendReason(rows), { confirmText: "확인", cancelText: "닫기" });
    return;
  }

  const notice = skipped ? `\n(발송할 수 없는 ${skipped}명은 제외됩니다)` : "";
  if (!(await customConfirm(`${sendable.length}명에게 독서 결과를 발송할까요?${notice}`))) return;

  try {
    const sent = await postJson("/admin/growth/app-send/send", {
      diaryKeys: sendable.map((r) => r.diaryKey),
    });
    // 발송일시(send_at)는 서버가 KST로 찍으므로 화면에서 만들지 않고 다시 조회한다
    await loadSendList();
    if (sent < sendable.length) {
      await customConfirm(`${sent}명 발송했습니다. (이미 발송된 건은 제외됐습니다)`,
        { confirmText: "확인", cancelText: "닫기" });
    }
  } catch (e) {
    await customConfirm(e.message, { confirmText: "확인", cancelText: "닫기" });
  }
}

/* 한 명도 못 보낼 때 그 이유를 짚어 준다 (전부 같은 이유일 때만 이유를 말한다) */
function sendReason(rows) {
  if (!rows.length) return "발송할 미발송 학생이 없습니다.";
  if (rows.every((r) => r.status === "SENT")) return "이미 모두 발송된 학생입니다.";
  if (rows.every((r) => r.status === "NO_APP")) return "앱이 연동되지 않은 학생입니다.";
  if (rows.every((r) => r.diaryKey == null)) return "독서일지가 아직 작성되지 않았습니다.";
  return "발송할 수 있는 학생이 없습니다.";
}

/* ── 미리보기 = 앱 "정독 결과" 화면 ─────────────────── */

// 응답은 앱의 정독 결과와 같은 AppRespDTO.BookstoreReportDTO 다. 조회 일자는 툴바에서 고른
// 날짜 하나뿐이라 앱 화면 상단의 일자 탭(report.dates)은 미리보기에서 쓰지 않는다.

function renderReport(report) {
  const wrap = document.getElementById("sendPreview");

  // 그날 일지가 없으면 보낼 내용 자체가 없다(서버가 recordDate=null 로 알려준다)
  if (!report.recordDate) {
    showPreviewMessage("선택한 날짜에 작성된 독서일지가 없습니다.");
    return;
  }

  const books = report.books ?? [];
  const book = books[selectedBookIndex] ?? books[0];

  wrap.innerHTML = `
    <div class="app-report">
      ${summaryHtml(report)}

      <section class="app-card">
        <h4 class="app-card-title">읽은 책의 문제 풀이 결과</h4>
        ${bookTabsHtml(report)}
        ${book ? bookResultHtml(book) : `<p class="app-growth-text">읽은 책이 없습니다.</p>`}
        ${book ? growthHtml(book) : ""}
      </section>

      <section class="app-card">
        <h4 class="app-card-title">이번 독서 활동에서는</h4>
        <div class="app-achievements">${achievementsHtml(report)}</div>
      </section>

      <section class="app-card">
        <h4 class="app-card-title">6개 영역 이해 분포</h4>
        ${bubbleChartHtml(report.tendencies)}

        <div class="app-card-divider"></div>
        <h4 class="app-card-title">독서 성향</h4>
        <div class="app-tendency">
          ${vennHtml(report.tendencies)}
          <div class="app-tendency-list">${tendencyTitlesHtml(report.tendencies)}</div>
        </div>
      </section>

      ${monthlyHtml(report.monthly)}
    </div>
  `;

  bindReportEvents(report);
}

function bindReportEvents(report) {
  const wrap = document.getElementById("sendPreview");

  wrap.querySelectorAll(".app-book-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      selectedBookIndex = Number(tab.dataset.index);
      renderReport(report);
    });
  });

  const zoomBtn = wrap.querySelector(".app-book-zoom");
  if (zoomBtn) zoomBtn.addEventListener("click", () => openCover(zoomBtn.dataset.cover));
}

function summaryHtml(report) {
  // 요약 문장(summaryText)은 생성 정책이 아직 없어 서버가 늘 null 로 준다 —
  // 그때는 문장 영역을 통째로 숨긴다(앱과 같은 처리)
  const comment = escapeHtml(report.summaryText ?? "");

  return `
    <section class="app-summary">
      <p class="app-summary-title">${escapeHtml(report.studentName)} 학생은 총 <b>${report.bookCount}권</b>의 책을 읽었어요.</p>
      <div class="app-summary-stats">
        <span>독서 시간 ${report.readMinutes}분</span>
        ${report.correctRate == null ? "" : `<span>평균 정답률 ${report.correctRate}%</span>`}
        <span>누적독서 ${report.totalBookCount}권째</span>
      </div>
      ${comment ? `<p class="app-summary-comment">${comment}</p>` : ""}
    </section>`;
}

/* 책이 한 권뿐이면 탭을 띄우지 않는다(누를 데가 없는 탭은 혼란만 준다) */
function bookTabsHtml(report) {
  const books = report.books ?? [];
  if (books.length < 2) return "";
  return `<div class="app-book-tabs">${books
    .map(
      (b, i) =>
        `<button type="button" class="app-book-tab${i === selectedBookIndex ? " active" : ""}" data-index="${i}">${escapeHtml(b.title)}</button>`
    )
    .join("")}</div>`;
}

function bookResultHtml(book) {
  const cover = book.imageUrl || DEFAULT_COVER;
  // 처음점수는 2026-09-21에 없어졌다(재제출 결과가 곧 그 학생의 점수라 최종점수와 늘 같다).
  // 남은 건 몇 번 다시 풀었는지뿐이다.
  const retry = book.retryCount ? `재도전 ${book.retryCount}회` : "";

  return `
    <div class="app-book">
      <div class="app-book-cover">
        <img src="${cover}" alt="${escapeHtml(book.title)}" onerror="this.onerror=null;this.src='${DEFAULT_COVER}';" />
        <button type="button" class="app-book-zoom" data-cover="${cover}" aria-label="표지 크게 보기">
          <span class="app-book-zoom-dot"><i class="fa-solid fa-magnifying-glass"></i></span>
        </button>
      </div>
      <div class="app-book-info">
        <p class="app-book-title">${escapeHtml(book.title)}</p>
        ${retry ? `<p class="app-book-retry">${escapeHtml(retry)}</p>` : ""}
        <div class="app-score-row">
          <div class="app-score">
            <div class="app-score-label">정독 문제</div>
            <div class="app-score-value">${book.basicCorrect}<small>/${book.basicTotal}</small></div>
          </div>
          <div class="app-score">
            <div class="app-score-label">문해력 문제</div>
            <div class="app-score-value">${book.advancedCorrect}<small>/${book.advancedTotal}</small></div>
          </div>
          <div class="app-score score-rate">
            <div class="app-score-label">정답률</div>
            <div class="app-score-value">${book.correctRate ?? "-"}${book.correctRate == null ? "" : "%"}</div>
          </div>
        </div>
      </div>
    </div>`;
}

/* "문해력이 자랐어요" — 낱말이 없으면(현재 서버가 항상 null) 영역을 통째로 숨긴다 */
function growthHtml(book) {
  const words = book.growthWords ?? [];
  if (!words.length) return "";

  const quoted = words.map((w) => `‘${w}’`).join(", ");
  return `
    <h5 class="app-growth-head">
      <img src="/images/student_result/medal.png" alt="" onerror="this.remove()" /> 문해력이 자랐어요!
    </h5>
    <p class="app-growth-text">${escapeHtml(quoted)} 처럼 문맥 속 낱말 뜻을 짐작하며 낱말 순서를 바르게 배열해 문장을 완성하는 힘이 자랐어요.</p>`;
}

/* 이번 독서 활동에서는 — 계열별 대표 뱃지 2칸 + 누적 독서기록 + 가장 오른 영역.
   미획득 뱃지도 자리를 지키되 회색으로 둔다(앱과 같다) — 빈칸으로 두면 그 계열이
   아예 없는 것처럼 보이고, 컬러로 두면 받은 것과 구분이 안 된다.

   문구는 앱과 같이 두 줄로 끊는다 — 앞줄이 "무엇을 얼마나", 뒷줄이 "어떻게 됐다"다.
   칸 너비에 맡겨 저절로 접히게 두면 학생 이름이나 영역 이름 길이에 따라 끊기는 자리가
   칸마다 달라져 네 칸이 들쭉날쭉해진다. */
function achievementsHtml(report) {
  const badges = report.badges ?? [];
  const items = [];

  BADGE_GROUPS.forEach((group) => {
    const inGroup = group.map((category) => badges.find((b) => b.category === category)).filter(Boolean);
    if (!inGroup.length) return;

    // 획득분 중 가장 높은 등급(배열 뒤쪽). 하나도 없으면 그 계열 최고 등급을 목표로 세운다.
    const earned = inGroup.filter((b) => b.earned && b.earnedCount > 0);
    const badge = earned.length ? earned[earned.length - 1] : inGroup[inGroup.length - 1];

    items.push({
      icon: BADGE_ICON[badge.category] ?? "/images/student_result/trophy.png",
      name: badge.badgeName,
      // 못 받았어도 "0번"을 적는다 — 횟수를 빼면 그 칸만 문구 길이가 달라진다
      head: `${josa(badge.badgeName)} ${badge.earnedCount ?? 0}번`,
      tail: "달성했어요.",
      locked: !earned.length,
    });
  });

  items.push({
    icon: "/images/student_result/passport.png",
    name: "독서기록",
    head: `이 ${report.bookCount}개`,
    tail: "누적됐어요.",
  });

  // 정답률이 가장 높은 영역을 "능력이 증가했어요"로 짚는다 — 서버에 해당 필드가 없어
  // 성향 값에서 고른다(성향 문구와 같은 근거를 쓴다)
  const topTendency = [...(report.tendencies ?? [])].sort((a, b) => (b.rate ?? 0) - (a.rate ?? 0))[0];
  if (topTendency) {
    items.push({
      icon: `/images/icons/${TENDENCY_ICON[topTendency.typeCode] ?? "comp.png"}`,
      name: topTendency.typeName,
      head: " 능력이",
      tail: "증가했어요.",
    });
  }

  return items
    .map(
      (a) => `
      <div class="app-achievement${a.locked ? " is-locked" : ""}">
        <img src="${a.icon}" alt="" onerror="this.remove()" />
        <p><b>${escapeHtml(a.name)}</b>${escapeHtml(a.head)}<br />${escapeHtml(a.tail)}</p>
      </div>`
    )
    .join("");
}

/* 목적격 조사 — "정독완료를" / "정독왕을". 뱃지 이름이 받침으로 끝나는지로 갈린다.
   한글 음절은 (코드 - 0xAC00) % 28 이 0 이면 받침이 없다. */
function josa(name) {
  const last = String(name ?? "").trim().slice(-1).charCodeAt(0);
  const hangul = last >= 0xac00 && last <= 0xd7a3;
  return hangul && (last - 0xac00) % 28 === 0 ? "를" : "을";
}

/* 6개 영역 버블 — 크기는 그 영역의 점수에 비례하고, 자리는 겹치지 않게 계산한다.
   반지름을 점수에 그대로 비례시킨다(면적이 아니라) — 화면에서 읽는 값이 원의 지름이라,
   면적 비례로 잡으면 92%와 51%가 눈으로는 거의 같은 크기로 보인다. */
function bubbleChartHtml(tendencies) {
  const { w: W, h: H } = BUBBLE_VIEW;

  // 점수가 없는 영역(푼 문제 없음)은 크기를 낼 수 없어 뺀다
  const sorted = [...(tendencies ?? [])]
    .filter((t) => t.rate != null)
    .sort((a, b) => b.rate - a.rate);
  if (!sorted.length) return "";

  const placed = packBubbles(sorted.map((t) => ({ tendency: t, weight: Number(t.rate) })));

  const circles = placed
    .map(({ tendency: t, x, y, r }) => {
      const color = TENDENCY_COLOR[t.typeName] ?? TENDENCY_FALLBACK;
      const rate = `${Number(t.rate).toFixed(1)}%`;

      // 글자가 원을 넘기면 값이 아니라 소음이 된다 — 좁으면 값부터, 더 좁으면 이름까지 뺀다.
      // 뺀 값은 마우스를 올리면 나오는 <title> 에 그대로 남는다.
      const nameSize = Math.max(9, Math.round(r * 0.28));
      const rateSize = Math.max(9, Math.round(r * 0.25));
      const showRate = r >= 26;
      const showName = r >= 16;

      const labels = [
        showName
          ? `<text class="app-bubble-label" x="${x}" y="${showRate ? y - 2 : y + nameSize / 3}"
                   text-anchor="middle" font-size="${nameSize}" fill="${color.text}">${escapeHtml(t.typeName)}</text>`
          : "",
        showRate
          ? `<text class="app-bubble-label" x="${x}" y="${y + rateSize + 3}"
                   text-anchor="middle" font-size="${rateSize}" fill="${color.text}">${rate}</text>`
          : "",
      ].join("");

      return `
        <g>
          <title>${escapeHtml(t.typeName)} ${rate} (응답 ${t.answerCount ?? 0}개)</title>
          <circle cx="${x}" cy="${y}" r="${r}" fill="${color.fill}" />
          ${labels}
        </g>`;
    })
    .join("");

  return `<svg class="app-bubbles" viewBox="0 0 ${W} ${H}" role="img" aria-label="영역별 이해 분포">${circles}</svg>`;
}

/**
 * 버블 배치 — 큰 것부터 빈자리에 무작위로 앉힌다.
 *
 * 자리를 미리 박아 두지 않는 건 영역 수와 점수가 학생마다 다르기 때문이다. 고정 슬롯은
 * 값이 달라져도 늘 같은 그림이 나와서, 크기에 담긴 뜻이 사라진다.
 *
 * 그릴 때마다 새로 뽑는다 — 앱이 그렇게 동작한다(같은 학생·같은 값인데 열 때마다 배치가
 * 다르다). 씨앗을 고정해 두면 앱과 다른 그림이 되므로 일부러 고정하지 않는다 (2026-09-23).
 *
 * 자리가 없으면 전부 조금씩 줄여 다시 앉힌다. 겹치느니 작게 그리는 쪽이 낫다.
 */
function packBubbles(items) {
  const { w: W, h: H, fill } = BUBBLE_VIEW;

  // 전체 면적이 캔버스의 fill 만큼 되도록 첫 배율을 잡는다(반지름 = 점수 × 배율)
  const sumSq = items.reduce((sum, it) => sum + it.weight ** 2, 0);
  let scale = Math.sqrt((fill * W * H) / (Math.PI * sumSq));

  for (let attempt = 0; attempt < 20; attempt++) {
    const result = tryPack(items.map((it) => ({ ...it, r: it.weight * scale })));
    if (result) return result;
    scale *= 0.93;
  }
  // 20번 줄여도 못 앉히는 일은 사실상 없지만, 그래도 그림은 나와야 한다
  return tryPack(items.map((it) => ({ ...it, r: it.weight * scale })), true) ?? [];
}

/* 한 배율로 전부 앉혀 본다. 하나라도 자리를 못 찾으면 null(=배율을 줄여 다시). */
function tryPack(sized, force = false) {
  const { w: W, h: H, pad, gap } = BUBBLE_VIEW;
  const placed = [];

  for (const item of sized) {
    const r = item.r;
    let spot = null;

    // 원이 캔버스 안에 온전히 들어오는 범위에서만 중심을 뽑는다
    const minX = pad + r, maxX = W - pad - r;
    const minY = pad + r, maxY = H - pad - r;
    if (maxX < minX || maxY < minY) return force ? placed : null; // 캔버스보다 큰 원

    for (let tries = 0; tries < 800; tries++) {
      const x = minX + Math.random() * (maxX - minX);
      const y = minY + Math.random() * (maxY - minY);
      if (placed.some((p) => Math.hypot(p.x - x, p.y - y) < p.r + r + gap)) continue;

      spot = { x: Number(x.toFixed(1)), y: Number(y.toFixed(1)), r: Number(r.toFixed(1)) };
      break;
    }

    if (!spot) {
      if (!force) return null;
      spot = { x: W / 2, y: H / 2, r: Number(r.toFixed(1)) }; // 최후 수단 — 겹쳐서라도 그린다
    }
    placed.push({ ...item, ...spot });
  }

  return placed;
}

/* 독서 성향 벤 다이어그램 — 점수 상위 3개 영역을 위 1 / 아래 2로 겹쳐 그린다(앱과 같다).
   자리는 정삼각형이고 중심 간 거리는 반지름의 1.5배다(2026-09-23, 1.4배에서 살짝 벌림)
   — 더 붙이면 겹친 부분이 넓어져 원마다 제 색으로 남는 자리가 줄고, 더 떼면 세 영역이
   이어져 있다는 뜻이 안 보인다.

   이름은 각 원의 한가운데 놓는다. 중심 간 거리(60)가 반지름(40)보다 커서 어느 원의
   중심도 다른 원에 덮이지 않는다 — 가운데가 그 영역만의 자리라 글자가 겹칠 일이 없다. */
const VENN = {
  w: 160,
  h: 146,
  r: 40,
  circles: [
    { cx: 80, cy: 47 },   // 1위 — 위
    { cx: 50, cy: 99 },   // 2위 — 왼쪽 아래
    { cx: 110, cy: 99 },  // 3위 — 오른쪽 아래
  ],
};

function vennHtml(tendencies) {
  const top3 = [...(tendencies ?? [])]
    .filter((t) => t.rate != null)
    .sort((a, b) => b.rate - a.rate)
    .slice(0, VENN.circles.length);
  if (!top3.length) return "";

  const shapes = top3
    .map((t, i) => {
      const slot = VENN.circles[i];
      const color = TENDENCY_COLOR[t.typeName] ?? TENDENCY_FALLBACK;
      // multiply — 겹친 부분이 두 색을 섞은 진한 색이 된다(앱과 같다). 색을 반투명으로만
      // 두면 겹친 자리가 흐려져 오히려 경계가 사라진다.
      return `<circle cx="${slot.cx}" cy="${slot.cy}" r="${VENN.r}" fill="${color.fill}"
                      style="mix-blend-mode: multiply" />`;
    })
    .join("");

  // 글자는 원을 다 그린 뒤에 얹는다 — 원 사이에 끼우면 뒤에 오는 원에 덮인다
  const labels = top3
    .map((t, i) => {
      const slot = VENN.circles[i];
      const color = TENDENCY_COLOR[t.typeName] ?? TENDENCY_FALLBACK;
      return `<text class="app-venn-label" x="${slot.cx}" y="${slot.cy}"
                    text-anchor="middle" dominant-baseline="central"
                    fill="${color.text}">${escapeHtml(t.typeName)}</text>`;
    })
    .join("");

  return `
    <svg class="app-tendency-venn" viewBox="0 0 ${VENN.w} ${VENN.h}"
         role="img" aria-label="상위 세 영역">
      ${shapes}
      ${labels}
    </svg>`;
}

/* 독서 성향 문구 — 정답률 상위 3개 영역을 문구로/* 독서 성향 문구 — 벤 다이어그램과 같은 상위 3개를 같은 순서로 늘어놓는다.
   왼쪽 그림의 원과 오른쪽 줄이 1:1로 맞아야 어느 영역 이야기인지 읽힌다. */
function tendencyTitlesHtml(tendencies) {
  return [...(tendencies ?? [])]
    .filter((t) => t.rate != null)
    .sort((a, b) => b.rate - a.rate)
    .slice(0, VENN.circles.length)
    .map((t) => {
      const info = TENDENCY_TITLE[t.typeCode] ?? { title: t.typeName, icon: "fa-regular fa-star" };
      return `<div class="app-tendency-item"><i class="${info.icon}"></i>${escapeHtml(info.title)}</div>`;
    })
    .join("");
}

/* 월별 독서량 — 한 계열짜리 영역 그래프(범례 없이 제목이 계열을 말한다).
   가로축은 입회월(또는 그 해 1월)부터 기준월까지 한 달도 빠짐없이(0권인 달 포함, 서버가 채운다).
   세로 눈금은 0/5/10/15권이다 — 대부분의 달이 여기 들어와서, 눈금을 값에 맞춰 늘였다
   줄였다 하면 같은 높이의 곡선이 달마다 다른 권수를 뜻하게 된다 (2026-09-23). */
function monthlyHtml(monthly) {
  const points = (monthly ?? []).map((m) => ({
    month: Number(m.month),
    year: m.year,
    count: Number(m.count),
  }));
  if (!points.length) return "";

  const total = points.reduce((sum, p) => sum + p.count, 0);
  const first = points[0];
  const last = points[points.length - 1];

  // 최근 4개월 평균 — 점이 4개보다 적으면 있는 만큼으로 계산한다
  const recent = points.slice(-4);
  const recentAvg = (recent.reduce((sum, p) => sum + p.count, 0) / recent.length).toFixed(1);

  const W = 300, H = 170, PAD_L = 34, PAD_R = 10, PAD_T = 14, PAD_B = 24;
  // 기본 15권. 그보다 많이 읽은 달이 있으면 5권 단위로만 올린다 — 눈금을 고정해 두면
  // 그 달의 곡선이 그래프 위로 잘려 나간다.
  const maxCount = Math.max(15, Math.ceil(Math.max(...points.map((p) => p.count)) / 5) * 5);
  const x = (i) => PAD_L + (i * (W - PAD_L - PAD_R)) / Math.max(1, points.length - 1);
  const y = (v) => PAD_T + (1 - v / maxCount) * (H - PAD_T - PAD_B);

  // 달이 하나뿐이면(입회한 달의 리포트) 점 하나라 선도 영역도 그려지지 않는다.
  // 0권에서 그 달의 권수로 올라오는 선을 그린다 — 한 달치라도 "이만큼 읽었다"가 보여야 한다.
  // 시작점은 왼쪽 끝이 아니라 조금 안쪽이다. 눈금선 시작과 붙으면 축의 일부처럼 보인다.
  const single = points.length === 1;
  const xAt = (i) => (single ? W - PAD_R : x(i));
  const coords = single
    ? [
        { x: PAD_L + (W - PAD_L - PAD_R) * 0.2, y: y(0) },
        { x: W - PAD_R, y: y(points[0].count) },
      ]
    : points.map((p, i) => ({ x: x(i), y: y(p.count) }));
  const markers = single
    ? coords
        .map((c) => `<circle cx="${c.x.toFixed(1)}" cy="${c.y.toFixed(1)}" r="3.5" fill="#1f9d76" />`)
        .join("")
    : "";
  const line = smoothPath(coords, { top: PAD_T, bottom: H - PAD_B });
  const area = `${line} L${coords[coords.length - 1].x.toFixed(1)},${H - PAD_B} L${coords[0].x.toFixed(1)},${H - PAD_B} Z`;

  // 0권은 실선 기준선, 5권 간격으로 점선. 숫자는 0권까지 전부 붙인다(앱과 같다).
  const ticks = [];
  for (let v = 5; v <= maxCount; v += 5) ticks.push(v);

  const grid = `
      <line x1="${PAD_L}" y1="${y(0).toFixed(1)}" x2="${W - PAD_R}" y2="${y(0).toFixed(1)}"
            stroke="#cfd6da" stroke-width="1" />
      <text x="${PAD_L - 6}" y="${(y(0) + 4).toFixed(1)}" text-anchor="end" font-size="10" fill="#9aa5ab">0권</text>
      ${ticks
        .map(
          (v) => `
      <line x1="${PAD_L}" y1="${y(v).toFixed(1)}" x2="${W - PAD_R}" y2="${y(v).toFixed(1)}"
            stroke="#dfe4e7" stroke-width="1" stroke-dasharray="4 4" />
      <text x="${PAD_L - 6}" y="${(y(v) + 4).toFixed(1)}" text-anchor="end" font-size="10" fill="#9aa5ab">${v}권</text>`
        )
        .join("")}`;

  // 점 마커는 찍지 않는다(곡선만 보이는 디자인) — 대신 달마다 투명한 호버 영역을 둬서
  // 마우스를 올리면 그 달의 권수를 읽을 수 있게 한다
  const dots = points
    .map(
      (p, i) => `
      <g>
        <title>${p.year}년 ${p.month}월 · ${p.count}권</title>
        <circle cx="${xAt(i).toFixed(1)}" cy="${y(p.count).toFixed(1)}" r="10" fill="transparent" />
      </g>`
    )
    .join("");

  const xLabels = points
    .map(
      (p, i) =>
        `<text x="${xAt(i).toFixed(1)}" y="${H - 6}" text-anchor="middle" font-size="10" fill="#9aa5ab">${p.month}</text>`
    )
    .join("");

  return `
    <section class="app-card">
      <h4 class="app-card-title center">
        ${first.year}년 ${first.month}월부터 ${last.month}월까지<br />
        총 <b>${total}권</b>의 책을 읽었어요
      </h4>
      <svg class="app-monthly-chart" viewBox="0 0 ${W} ${H}" role="img"
           aria-label="${first.year}년 ${first.month}월부터 ${last.month}월까지 월별 독서량">
        ${grid}
        <path d="${area}" fill="#1f9d76" fill-opacity="0.14" />
        <path d="${line}" fill="none" stroke="#1f9d76" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
        ${markers}
        ${dots}
        ${xLabels}
      </svg>
      <p class="app-monthly-foot">${recent.length > 1 ? "최근 " : ""}${recent.length}개월간의 평균 독서량은 <b>${recentAvg}권</b>이에요</p>
    </section>`;
}

/* 꺾은선 대신 부드러운 곡선 — Catmull-Rom 스플라인을 3차 베지어로 바꿔 그린다.
   제어점 y는 그래프 영역 안으로 가둔다. 안 그러면 권수가 크게 오르내린 달에서 곡선이
   위/아래로 튀어나가 눈금선 밖까지 그려진다. */
function smoothPath(pts, bounds) {
  if (pts.length < 2) return pts.length ? `M${pts[0].x.toFixed(1)},${pts[0].y.toFixed(1)}` : "";

  let d = `M${pts[0].x.toFixed(1)},${pts[0].y.toFixed(1)}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] ?? pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] ?? p2;

    const clampY = (v) =>
      bounds ? Math.min(Math.max(v, bounds.top), bounds.bottom) : v;

    const c1x = p1.x + (p2.x - p0.x) / 6;
    const c1y = clampY(p1.y + (p2.y - p0.y) / 6);
    const c2x = p2.x - (p3.x - p1.x) / 6;
    const c2y = clampY(p2.y - (p3.y - p1.y) / 6);

    d += ` C${c1x.toFixed(1)},${c1y.toFixed(1)} ${c2x.toFixed(1)},${c2y.toFixed(1)} ${p2.x.toFixed(1)},${p2.y.toFixed(1)}`;
  }
  return d;
}

/* 표지 원본 모달 — 아무 데나 누르거나 ESC 로 닫는다.
   ESC 를 받는 건 오버레이가 화면을 통째로 덮어서, 닫는 길이 클릭 하나뿐이면
   키보드로는 빠져나올 수 없기 때문이다. */
function openCover(src) {
  const overlay = document.createElement("div");
  overlay.className = "preview-zoom-overlay";
  overlay.innerHTML = `<img src="${src}" alt="표지" onerror="this.onerror=null;this.src='${DEFAULT_COVER}';" />`;

  const close = () => {
    overlay.remove();
    document.removeEventListener("keydown", onKey);
  };
  const onKey = (e) => {
    if (e.key === "Escape") close();
  };

  overlay.addEventListener("click", close);
  document.addEventListener("keydown", onKey);
  document.body.appendChild(overlay);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
