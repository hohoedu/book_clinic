/*
 * 결제 내역 (2026-08-31).
 *
 * 목록은 /admin/payment/history/list 한 번으로 요약(결제완료/미결제 명수)까지 함께 받는다.
 * 행 상세(차감 내역 + 결제/환불 내역)는 펼칠 때 /admin/payment/history/detail을 따로 부른다 —
 * 학생 100명이면 상세도 100건이라 목록에 미리 실어 내리면 대부분 안 볼 데이터를 매번 나른다.
 * 한 번 받은 상세는 행에 캐시해 두고 다시 펼칠 때는 요청하지 않는다.
 */

document.addEventListener("DOMContentLoaded", () => {
  initFilterBar();
  initManualEntry();
  document.getElementById("filterMonth").value = currentYearMonth();
  loadPaymentList();
});

/* 뱃지 코드 → 색상 클래스 접미사. 라벨 글자는 서버가 내려주는 passStatusLabel을 그대로 쓴다 */
const STATUS_CLASS = {
  IN_USE: "status-in_use",
  USED_UP: "status-used_up",
  UNPAID: "status-unpaid",
  PARTIAL_REFUND: "status-partial_refund",
  REFUNDED: "status-refunded",
};

const RESERVATION_LABEL = { ATTENDED: "출석 완료", RESERVED: "예약", NOSHOW: "미출석" };
const TRAIL_STATUS_LABEL = { PAID: "결제 완료", CANCELED: "결제 취소", REQ: "취소 요청", DONE: "취소 완료", FAIL: "취소 실패" };

const TABLE_COLUMN_COUNT = 8;

/* ===================== 조회 ===================== */

async function fetchJson(url) {
  const res = await fetch(url);
  const body = await res.json();
  if (!res.ok || body.success === false) {
    throw new Error(body?.error?.message || "요청 처리 중 오류가 발생했습니다.");
  }
  return body.response;
}

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function currentFilters() {
  return {
    type: document.querySelector("#filterType button.active").dataset.type,
    month: document.getElementById("filterMonth").value,
    grade: document.getElementById("filterGrade").value,
    status: document.getElementById("filterStatus").value,
    keyword: document.getElementById("filterKeyword").value.trim(),
  };
}

async function loadPaymentList() {
  const listBody = document.getElementById("paymentListBody");
  const filters = currentFilters();
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });

  listBody.innerHTML = `<tr class="empty-row"><td colspan="${TABLE_COLUMN_COUNT}">조회 중입니다…</td></tr>`;

  try {
    const page = await fetchJson(`/admin/payment/history/list?${params.toString()}`);
    renderSummary(page);
    renderPaymentList(page.rows);
  } catch (e) {
    console.error("결제 내역 조회 실패", e);
    document.getElementById("paymentSummary").textContent = "";
    listBody.innerHTML = `<tr class="empty-row"><td colspan="${TABLE_COLUMN_COUNT}">목록을 불러오지 못했습니다: ${escapeHtml(e.message)}</td></tr>`;
  }
}

/* "2026년 8월 결제 내역 | 결제완료 89명 | 미결제 11명" */
function renderSummary(page) {
  const year = page.billingYm.slice(0, 4);
  const month = Number(page.billingYm.slice(4, 6));
  document.getElementById("paymentSummary").innerHTML = `
    <span class="summary-month">${year}년 ${month}월</span> 결제 내역
    <span class="summary-sep">|</span>
    <span class="summary-count">결제완료 <b>${page.paidStudentCount}</b>명</span>
    <span class="summary-sep">|</span>
    <span class="summary-count">미결제 <b>${page.unpaidStudentCount}</b>명</span>
  `;
}

function renderPaymentList(rows) {
  const listBody = document.getElementById("paymentListBody");
  if (!rows.length) {
    listBody.innerHTML = `<tr class="empty-row"><td colspan="${TABLE_COLUMN_COUNT}">조회된 결제 내역이 없습니다.</td></tr>`;
    return;
  }

  listBody.innerHTML = rows.map((row, idx) => {
    const badgeClass = STATUS_CLASS[row.passStatus] ?? "status-used_up";
    const hasRefund = (row.refundAmount ?? 0) > 0;
    return `
      <tr class="payment-row"
          data-student-id="${escapeHtml(row.studentId)}"
          data-pass-id="${row.passId ?? ""}"
          data-payment-id="${row.paymentId ?? ""}">
        <td class="col-no">${idx + 1}</td>
        <td class="col-name">${escapeHtml(row.studentName)}</td>
        <td>${escapeHtml(row.gradeName ?? "-")}</td>
        <td class="${row.paidAt ? "" : "col-muted"}">${formatDateTime(row.paidAt)}</td>
        <td class="col-amount">${formatMoney(row.amount)}</td>
        <td class="col-refund ${hasRefund ? "has-refund" : "col-muted"}">${hasRefund ? formatMoney(row.refundAmount) : "-"}</td>
        <td>${row.usedCount}회</td>
        <td><span class="pass-badge ${badgeClass}">${escapeHtml(row.passStatusLabel ?? "-")}</span></td>
      </tr>
    `;
  }).join("");

  listBody.querySelectorAll(".payment-row").forEach((tr) => {
    tr.addEventListener("click", () => toggleDetail(tr));
  });
}

/* ===================== 행 펼침 상세 ===================== */

async function toggleDetail(tr) {
  const opened = tr.nextElementSibling?.classList.contains("detail-row");
  if (opened) {
    tr.nextElementSibling.remove();
    tr.classList.remove("open");
    return;
  }

  // 한 번에 한 행만 펼친다 — 여러 행이 동시에 펼쳐지면 표가 세로로 길어져 비교가 오히려 어려워진다
  document.querySelectorAll(".detail-row").forEach((el) => el.remove());
  document.querySelectorAll(".payment-row.open").forEach((el) => el.classList.remove("open"));

  tr.classList.add("open");
  const detailRow = document.createElement("tr");
  detailRow.className = "detail-row";
  detailRow.innerHTML = `<td colspan="${TABLE_COLUMN_COUNT}"><div class="detail-loading">불러오는 중…</div></td>`;
  tr.after(detailRow);

  if (tr.dataset.detailCache) {
    detailRow.querySelector("td").innerHTML = tr.dataset.detailCache;
    return;
  }

  const params = new URLSearchParams({ studentId: tr.dataset.studentId });
  if (tr.dataset.passId) params.set("passId", tr.dataset.passId);
  if (tr.dataset.paymentId) params.set("paymentId", tr.dataset.paymentId);

  try {
    const detail = await fetchJson(`/admin/payment/history/detail?${params.toString()}`);
    const html = renderDetail(detail);
    tr.dataset.detailCache = html;
    detailRow.querySelector("td").innerHTML = html;
  } catch (e) {
    console.error("결제 상세 조회 실패", e);
    detailRow.querySelector("td").innerHTML =
      `<div class="detail-loading">상세를 불러오지 못했습니다: ${escapeHtml(e.message)}</div>`;
  }
}

function renderDetail(detail) {
  return `
    <div class="detail-box">
      <div class="detail-section">
        <h4>차감 내역</h4>
        ${renderPassUses(detail.passUses)}
      </div>
      <div class="detail-section">
        <h4>결제/환불 내역</h4>
        ${renderTrail(detail.trail)}
      </div>
    </div>
  `;
}

function renderPassUses(passUses) {
  if (!passUses?.length) {
    return `<div class="detail-empty">차감된 이용 내역이 없습니다.</div>`;
  }
  const rows = passUses.map((u) => `
    <tr>
      <td>${escapeHtml(u.usedDate)}</td>
      <td>${formatSlot(u)}</td>
      <td>${u.remainAfter}회</td>
      <td>${escapeHtml(RESERVATION_LABEL[u.reservationStatus] ?? "출석 완료")}</td>
    </tr>
  `).join("");
  return `
    <table class="detail-table">
      <thead>
        <tr><th>이용일</th><th>회차/시간</th><th>차감 후 잔여</th><th>상태</th></tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}

/* "2회차 (14:00 ~ 14:50)" — 짝지을 예약을 못 찾은 차감은 시간 없이 "-"만 찍는다 */
function formatSlot(use) {
  if (use.slotSeq == null) return "-";
  const time = use.startsAt && use.endsAt ? ` (${formatTime(use.startsAt)} ~ ${formatTime(use.endsAt)})` : "";
  return `${use.slotSeq}회차${escapeHtml(time)}`;
}

function renderTrail(trail) {
  if (!trail?.length) {
    return `<div class="detail-empty">결제 내역이 없습니다.</div>`;
  }
  const rows = trail.map((t) => `
    <tr>
      <td>${t.trailType === "CANCEL" ? "취소" : "결제"}</td>
      <td>${formatDateTime(t.occurredAt, true)}</td>
      <td class="${t.amount < 0 ? "amount-minus" : ""}">${formatMoney(t.amount)}</td>
      <td>${escapeHtml(trailStatusLabel(t))}</td>
    </tr>
  `).join("");
  return `
    <table class="detail-table">
      <thead>
        <tr><th>종류</th><th>결제/승인일</th><th>금액</th><th>상태</th></tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}

/*
 * 전액 취소와 부분 취소는 payment_cancel.status만으로는 구분되지 않는다(둘 다 DONE).
 * 취소 실패는 사유가 곧 다음 조치라 라벨 뒤에 붙여 보여준다.
 */
function trailStatusLabel(t) {
  const base = TRAIL_STATUS_LABEL[t.status] ?? t.status;
  if (t.trailType === "CANCEL" && t.status === "FAIL" && t.resultMsg) {
    return `${base} (${t.resultMsg})`;
  }
  return base;
}

/* ===================== 포맷 ===================== */

/* 서버는 LocalDateTime을 "2026-08-01T13:50:00"으로 내린다. 초는 화면에서 의미가 없어 자른다 */
function formatDateTime(value, dateOnlyIfMidnight = false) {
  if (!value) return "-";
  const [date, time = ""] = String(value).split("T");
  const hhmm = time.slice(0, 5);
  if (!hhmm || (dateOnlyIfMidnight && hhmm === "00:00")) return date;
  return `${date} ${hhmm}`;
}

function formatTime(value) {
  return String(value).split("T")[1]?.slice(0, 5) ?? "";
}

function formatMoney(value) {
  if (value == null) return "-";
  return `${value.toLocaleString("ko-KR")}원`;
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/* ===================== 필터바 ===================== */

function initFilterBar() {
  document.getElementById("btnSearch").addEventListener("click", loadPaymentList);
  document.getElementById("filterKeyword").addEventListener("keydown", (event) => {
    if (event.key === "Enter") loadPaymentList();
  });

  // 종류/월/학년/상태는 고르는 즉시 조회한다. 검색어만 조회 버튼(또는 Enter)을 거치는데,
  // 타이핑 중간 상태로 매번 조회하면 전원 목록을 계속 다시 받게 되기 때문이다.
  document.querySelectorAll("#filterType button").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll("#filterType button").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      loadPaymentList();
    });
  });
  ["filterMonth", "filterGrade", "filterStatus"].forEach((id) => {
    document.getElementById(id).addEventListener("change", loadPaymentList);
  });
}

/* 수기 등록 — 현금/계좌 수납분 이용권 발급. 발급 정책(환불·중복 처리)이 정해진 뒤 별도 작업 */
function initManualEntry() {
  document.getElementById("btnManualEntry").addEventListener("click", () => {
    customConfirm("수기 등록은 아직 준비 중입니다.", { confirmText: "확인", cancelText: "닫기" });
  });
}
