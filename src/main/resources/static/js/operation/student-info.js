/*
 * 학생 정보 — 목록/필터/학년옵션/상세정보(전체·기본정보 탭)·독서이력·예약현황은 /admin/students/* API로
 * 실데이터를 쓴다(2026-08-26). DB에 대응 컬럼이 없는 값(담당선생님·회비·학생과의 관계)만 그대로
 * 목업으로 남아있다 — StudentAdminController/StudentRepository 참고.
 */

document.addEventListener("DOMContentLoaded", () => {
  initFilterBar();
  initStudentModal();
  loadGradeOptions();
  loadStudentList();
  document.getElementById("btnPrintQrCard").addEventListener("click", printQrCards);
});

const RESULT_LABEL = { DONE_KING: "독서왕", DONE_FRIEND: "통과", PENDING: "재도전" };

/* 목업으로 남아있는 필드(회비 탭 전체)만 계속 쓰는 공용 샘플값 — API 연동 전 안내는 renderFeeTab 참고 */
const FEE_MOCK = {
  bookLevelName: "독서 3단계",
  bookTeacher: "-",
  bookFee: "-",
  bookMaterialFee: "-",
};

/* ===================== 목록 조회 ===================== */

async function fetchJson(url) {
  const res = await fetch(url);
  const body = await res.json();
  if (!res.ok || body.success === false) {
    throw new Error(body?.error?.message || "요청 처리 중 오류가 발생했습니다.");
  }
  return body.response;
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
    const gradeClass = gradeNameToLevelClass(s.gradeName);
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
          data-level="${s.levelNo ?? 1}"
          data-level-class="${gradeClass}"
          data-status="${statusLabel}"
          data-status-class="${statusClass}">
        <td class="col-no">${idx + 1}</td>
        <td class="col-name">${escapeHtml(s.studentName)}</td>
        <td>${escapeHtml(s.gradeName ?? "-")}</td>
        <td>${escapeHtml(s.billingPhone ?? "-")}</td>
        <td class="col-date">${escapeHtml(s.registeredAt ?? "-")}</td>
        <td class="col-date">${escapeHtml(s.lastVisitDate ?? "-")}</td>
        <td class="col-books">${s.totalDoneBooks ?? 0}</td>
        <td><span class="level-pill ${gradeClass}"><span class="level-icon">🐱</span>Lv. ${s.levelNo ?? 1}</span></td>
        <td><span class="status ${statusClass}">${statusLabel}</span></td>
      </tr>
    `;
  }).join("");
}

/* 학년명("초1"~"초6") → 레벨필 색상 클래스. 그 외 학년(유치원/중등 등)은 색 클래스 없이 기본값만 적용 */
function gradeNameToLevelClass(gradeName) {
  const map = { "초1": "grade-01", "초2": "grade-02", "초3": "grade-03", "초4": "grade-04", "초5": "grade-05", "초6": "grade-06" };
  return map[gradeName] ?? "grade-01";
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/* ===================== 필터바 ===================== */

function initFilterBar() {
  document.getElementById("btnSearch").addEventListener("click", loadStudentList);
  document.getElementById("filterKeyword").addEventListener("keydown", (event) => {
    if (event.key === "Enter") loadStudentList();
  });
  document.getElementById("btnFilterReset").addEventListener("click", () => {
    document.getElementById("filterGrade").value = "";
    document.getElementById("filterStatus").value = "";
    document.getElementById("filterKeyword").value = "";
    loadStudentList();
  });
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
    const [detail, readingHistory, reservations] = await Promise.all([
      fetchJson(`/admin/students/${encodeURIComponent(studentId)}`),
      fetchJson(`/admin/students/${encodeURIComponent(studentId)}/reading-history`),
      fetchJson(`/admin/students/${encodeURIComponent(studentId)}/reservations`),
    ]);
    currentDetail = { ...detail, readingHistory, reservations };
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
 * 학생증 QR 카드 출력 — 카드 디자인(밴드/로고/문구)은 이미 선인쇄된 세로형 PVC 원판(54 x 86mm)이고,
 * 여기서는 그 위에 겹쳐 찍을 가변 정보 3가지 — QR · 지점명 · ID — 만 레이어로 인쇄한다.
 * 인쇄 창 상단 토글로 용지 방식을 고른다:
 *   - A4 시트: A4(세로) 한 장에 여러 장 + 점선 재단선 (일반 프린터로 정렬 테스트)
 *   - 카드 낱장: 카드 1장 = 페이지 1장 @page 54x86mm 세로 (카드 프린터 원판 위에 오버프린트)
 * QR/지점명/ID 좌표는 인쇄 doc 안 .qr/.center-name/.serial 규칙에서 원판에 맞춰 조정.
 *
 * QR 원문은 시리얼 넘버 원문 그대로다 — 학생 로그인/스캔이 app_id 또는 serial_num 어느 쪽으로도
 * 학생을 찾으므로(StudentMapper.findByAppId 참고), 실물 카드에는 임의 발급한 시리얼 넘버를 싣는다.
 *
 * 아래 QR_CARD_SERIALS는 지금은 테스트용 고정 배치다(요청: 100260002 ~ 100260009 8장).
 * 실제 발급 플로우(임의 시리얼 생성 → 학생 등록)를 붙일 땐 이 배열을 그 결과로 바꾸면 된다.
 */
const QR_CARD_SERIALS = serialRange(100260002, 100260009);
const QR_CARD_CENTER_NAME = "부산 센텀점";

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

function printQrCards() {
  if (typeof QRCode !== "function") {
    alert("QR 생성 모듈을 불러오지 못했습니다. 네트워크 상태를 확인한 뒤 다시 시도해주세요.");
    return;
  }

  const cards = QR_CARD_SERIALS.map((serial) => {
    const dataUrl = makeQrDataUrl(serial);
    return `
      <div class="card">
        <img class="qr" src="${dataUrl}" alt="QR ${escapeHtml(serial)}">
        <span class="center-name">${escapeHtml(QR_CARD_CENTER_NAME)}</span>
        <span class="serial">ID : ${escapeHtml(serial)}</span>
      </div>`;
  }).join("");

  const win = window.open("", "_blank", "width=920,height=760");
  if (!win) {
    alert("팝업이 차단되어 있습니다. 팝업 허용 후 다시 시도해주세요.");
    return;
  }
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
    display: flex; align-items: center; gap: 8px;
    padding: 10px 14px; background: #fff; border-bottom: 1px solid #ddd;
  }
  .toolbar button {
    padding: 6px 13px; font-size: 13px; font-weight: 600;
    border: 1px solid #1c3aa1; border-radius: 6px;
    background: #fff; color: #1c3aa1; cursor: pointer;
  }
  .toolbar button.active { background: #1c3aa1; color: #fff; }
  .toolbar .spacer { flex: 1; }
  .toolbar .hint { font-size: 12px; color: #888; font-weight: 400; }
  .toolbar .print-btn { background: #1c3aa1; color: #fff; }

  .sheet { display: flex; flex-wrap: wrap; }

  /* A4 시트 모드 — 한 장에 여러 카드 + 점선 재단선 */
  body.mode-a4 .sheet { padding: 16px; gap: 4px; justify-content: flex-start; }
  body.mode-a4 .card { outline: 0.2mm dashed #b0b0b0; }

  /* 카드 낱장 모드 — 카드 1장 = 페이지 1장 (flex 대신 block: 카드 프린터에서 page-break가 더 확실) */
  body.mode-card .sheet { display: block; padding: 16px; }
  body.mode-card .card { margin: 0 auto 16px; page-break-after: always; }
  body.mode-card .card:last-child { page-break-after: auto; }

  @media screen {
    .card { box-shadow: 0 1px 6px rgba(0, 0, 0, .18); }
  }
  @media print {
    body { background: #fff; }
    .toolbar { display: none; }
    body.mode-a4 .sheet { padding: 0; gap: 0; }
    body.mode-card .sheet { padding: 0; }
    body.mode-card .card { margin: 0; }
    .card { box-shadow: none; }
  }

  /*
   * 세로형 카드. 카드 원판(디자인·로고·밴드 전부 선인쇄된 PVC)에 겹쳐 찍는 레이어라 배경은 투명.
   * QR / 지점명 / ID 3가지만 올린다. ▼ 아래 좌표(mm)를 실제 원판에 맞춰 조정하세요.
   */
  .card {
    position: relative;
    /* CR-80(ISO ID-1) 세로 = 53.98 x 85.60mm. IDP SMART-51 카드 크기와 정확히 맞춰야 함 */
    width: 53.98mm; height: 85.6mm;
    background: transparent;
    overflow: hidden;
  }
  .qr {
    position: absolute; left: 50%; top: 19mm;
    transform: translateX(-50%);
    width: 22mm; height: 22mm;
    image-rendering: pixelated;
  }
  .center-name {
    position: absolute; left: 50%; top: 50mm;
    transform: translateX(-50%);
    font-size: 3.2mm; color: #000000;
    white-space: nowrap;
  }
  .serial {
    position: absolute; left: 50%; top: 66mm;
    transform: translateX(-50%);
    font-size: 3.2mm; color: #000000;
  }

  /* 화면 미리보기에서만 원판 위치 감을 잡도록 옅은 상단 밴드 표시 (인쇄 안 됨) */
  @media screen {
    .card { background: #fff; }
    .card::before {
      content: ""; position: absolute; left: 0; right: 0; top: 0; height: 16mm;
      background: #1c3aa1; opacity: .12;
    }
  }
</style>
</head>
<body class="mode-a4">
  <div class="toolbar">
    <button type="button" data-mode="a4" class="active">A4 시트</button>
    <button type="button" data-mode="card">카드 낱장</button>
    <span class="hint" id="modeHint"></span>
    <span class="spacer"></span>
    <button type="button" class="print-btn" id="btnDoPrint">인쇄하기</button>
  </div>
  <div class="sheet">${cards}</div>
  <script>
    (function () {
      var HINTS = {
        a4: "A4 용지에 여러 장 배치(점선 따라 재단). 일반 프린터용.",
        card: "카드 1장 = 페이지 1장 (54 x 86mm 세로). 카드 프린터/인쇄소용."
      };
      function setMode(m) {
        document.body.className = "mode-" + m;
        document.querySelectorAll(".toolbar [data-mode]").forEach(function (b) {
          b.classList.toggle("active", b.dataset.mode === m);
        });
        document.getElementById("pageRule").textContent = m === "a4"
          ? "@page { size: A4 portrait; margin: 8mm; }"
          : "@page { size: 53.98mm 85.6mm; margin: 0; }";
        document.getElementById("modeHint").textContent = HINTS[m];
      }
      document.querySelectorAll(".toolbar [data-mode]").forEach(function (b) {
        b.addEventListener("click", function () { setMode(b.dataset.mode); });
      });
      document.getElementById("btnDoPrint").addEventListener("click", function () {
        window.focus();
        window.print();
      });
      setMode("a4");
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
    fee: renderFeeTab,
    reading: renderReadingTab,
    reservation: renderReservationTab,
  };

  document.getElementById("studentModalBody").innerHTML = renderers[tab](currentDetail);
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
            <div class="detail-row"><dt>입회일</dt><dd>${escapeHtml(d.registeredAt ?? "-")}</dd></div>
            <div class="detail-row"><dt>학교</dt><dd>${escapeHtml(d.school ?? "-")}</dd></div>
            <div class="detail-row"><dt>학년</dt><dd>${escapeHtml(d.gradeName ?? "-")}</dd></div>
            <div class="detail-row"><dt>생년월일</dt><dd>${escapeHtml(d.birth ?? "-")}</dd></div>
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
            <table class="basic-table">
              <colgroup><col style="width:50%"><col style="width:50%"></colgroup>
              <tbody>
                ${reservations.length ? reservations.map((r) => `
                  <tr><td>${escapeHtml(r.serviceDate)}</td><td>${reservationStatusPill(r.status)}</td></tr>
                `).join("") : `<tr><td colspan="2" class="empty-row">예약 이력이 없습니다.</td></tr>`}
              </tbody>
            </table>
          </div>
        </article>
      </div>

      <div class="card-right">
        <div class="card-common">
          <h4 class="sub-themes">독서 현황</h4>
          <div class="stat-tiles">
            <div class="stat-tile">
              <div class="stat-icon">📖</div>
              <div class="stat-label">누적 독서</div>
              <div class="stat-value">${d.totalDoneBooks ?? 0}권</div>
            </div>
            <div class="stat-tile">
              <div class="stat-icon">🏅</div>
              <div class="stat-label">현재 레벨</div>
              <div class="stat-value">Lv. ${d.levelNo ?? 1}</div>
            </div>
            <div class="stat-tile">
              <div class="stat-icon">🏆</div>
              <div class="stat-label">독서왕 횟수</div>
              <div class="stat-value">${d.kingCount ?? 0}회</div>
            </div>
            <div class="stat-tile">
              <div class="stat-icon">🛡️</div>
              <div class="stat-label">획득 뱃지</div>
              <div class="stat-value">${d.badgeCount ?? 0}개</div>
            </div>
          </div>
        </div>

        <div class="card-common card-grow">
          <h4 class="sub-themes">최근 읽은 책</h4>
          <div class="recent-book-scroll">
            <ul class="recent-book-list">
              ${readingHistory.length ? readingHistory.slice(0, 4).map((b) => {
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
              }).join("") : `<li class="empty-row">독서 이력이 없습니다.</li>`}
            </ul>
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

function renderFeeTab(d) {
  return `
    <div class="small-title">수강과목(독서)</div>
    <table class="base-info-t">
      <colgroup><col style="width:15%"><col style="width:35%"><col style="width:15%"><col style="width:35%"></colgroup>
      <tbody>
        <tr>
          <th>독서 단계</th>
          <td>${escapeHtml(FEE_MOCK.bookLevelName)}</td>
          <th>수강 상태</th>
          <td class="choose-group"><span class="btn-choose active">수강</span><span class="btn-choose">미수강</span></td>
        </tr>
        <tr>
          <th>담당 선생님</th>
          <td>${escapeHtml(FEE_MOCK.bookTeacher)}</td>
          <th>시작일자</th>
          <td>${escapeHtml(d.registeredAt ?? "-")}</td>
        </tr>
        <tr>
          <th>교육비</th>
          <td><input type="text" value="${escapeHtml(FEE_MOCK.bookFee)}" readonly></td>
          <th>교재비</th>
          <td><input type="text" value="${escapeHtml(FEE_MOCK.bookMaterialFee)}" readonly></td>
        </tr>
      </tbody>
    </table>
    <p class="mock-note" title="담당선생님/회비는 DB에 대응 데이터가 없어 목업입니다">※ 이 탭은 아직 API 연동 전(담당선생님/회비 데이터 없음)입니다.</p>
    <div class="save-btn-frame">
      <button type="submit" class="save-btn" disabled title="회비 API 연동 전이라 저장은 아직 동작하지 않습니다">교재비 저장</button>
    </div>
  `;
}

function renderReadingTab(d) {
  const history = d.readingHistory ?? [];
  if (!history.length) {
    return `<p class="empty-row">독서 이력이 없습니다.</p>`;
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
    return `<p class="empty-row">예약 이력이 없습니다.</p>`;
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
