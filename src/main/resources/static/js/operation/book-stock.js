/*
 * 보유도서 설정 — 로그인 직원 센터의 도서별 보유 수량(실물 사본 수) 조회/조정
 *
 * 이 화면에는 저장 버튼이 없다. +/- 를 누르거나 수량을 직접 입력하면 그 즉시 서버에 반영되고,
 * 서버가 돌려준 실제 수량으로 화면을 다시 맞춘다(대여 중이라 못 줄인 경우 등 화면과 DB가 어긋나지 않게).
 */

document.addEventListener("DOMContentLoaded", async () => {
  initFilters();
  initHistoryModal();
  initBulkModal();
  await loadStocks();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
// 서버가 세션마다 다른 값을 XSRF-TOKEN 쿠키로 내려준다(CookieCsrfTokenRepository, 2026-07-31) —
// 예전처럼 고정 문자열을 하드코딩하지 않고 매 요청마다 쿠키에서 읽는다.
function getCsrfToken() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}
const DEFAULT_BOOK_IMAGE = "/images/book-sample.png";

/* 학년 코드 → 뱃지 색상 클래스 (book-stock.css) */
const GRADE_BADGE_CLASS = {
  "01": "grade-01",
  "02": "grade-02",
  "03": "grade-03",
  "04": "grade-04",
  "05": "grade-05",
  "06": "grade-06",
  "07": "grade-07",
};

/* 난이도 → 뱃지 색상 클래스 */
const LEVEL_BADGE_CLASS = {
  하: "level-low",
  중: "level-mid",
  상: "level-high",
};

let stocks = []; // 현재 조회 결과 (contentId 기준으로 행을 찾아 수량만 갱신한다)

/* 공통 POST 요청 (JSON) */
async function postJson(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      [CSRF_HEADER]: getCsrfToken(),
    },
    body: JSON.stringify(body),
  });

  const data = await response.json();
  if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");

  return data.response;
}

/* ===================== 필터 ===================== */

function initFilters() {
  document.getElementById("btnSearch").addEventListener("click", loadStocks);

  document.getElementById("filterKeyword").addEventListener("keydown", (event) => {
    if (event.key === "Enter") loadStocks();
  });

  // 셀렉트는 고르는 즉시 조회 (조회 버튼은 검색어 입력용으로 남겨둔다)
  ["filterGrade", "filterContentType", "filterCategory", "filterHasStock"].forEach((id) => {
    document.getElementById(id).addEventListener("change", loadStocks);
  });

  document.getElementById("btnFilterReset").addEventListener("click", () => {
    ["filterGrade", "filterContentType", "filterCategory", "filterHasStock"].forEach((id) => {
      document.getElementById(id).value = "";
    });
    document.getElementById("filterKeyword").value = "";
    loadStocks();
  });
}

function getFilterParams() {
  const params = new URLSearchParams();
  const schoolYear = document.getElementById("filterGrade").value;
  const contentType = document.getElementById("filterContentType").value;
  const genre = document.getElementById("filterCategory").value;
  const hasStock = document.getElementById("filterHasStock").value;
  const title = document.getElementById("filterKeyword").value.trim();

  if (schoolYear) params.set("schoolYear", schoolYear);
  if (contentType) params.set("contentType", contentType);
  if (genre) params.set("genre", genre);
  if (hasStock) params.set("hasStock", hasStock);
  if (title) params.set("title", title);

  return params;
}

/* ===================== 목록 ===================== */

async function loadStocks() {
  renderMessage("조회 중입니다...");

  try {
    const response = await fetch(`/book/stock?${getFilterParams()}`);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "목록을 불러오지 못했습니다.");

    stocks = data.response ?? [];
    renderStocks();
  } catch (error) {
    console.error(error);
    renderMessage("목록을 불러오지 못했습니다.");
  }
}

function renderMessage(message) {
  const tbody = document.getElementById("stockListBody");
  tbody.innerHTML = `<tr class="stock-empty"><td colspan="9">${message}</td></tr>`;
}

function renderStocks() {
  const tbody = document.getElementById("stockListBody");

  if (stocks.length === 0) {
    renderMessage("조건에 맞는 도서가 없습니다.");
    return;
  }

  tbody.innerHTML = "";

  stocks.forEach((stock, index) => {
    const row = document.createElement("tr");
    row.className = "stock-master-row";
    row.dataset.contentId = stock.contentId;
    row.innerHTML = `
      <td class="col-no">${index + 1}</td>
      <td class="col-book">
        <i class="fa-solid fa-caret-right tree-caret"></i>
        <img src="${stock.imageUrl || DEFAULT_BOOK_IMAGE}" alt="" onerror="this.src='${DEFAULT_BOOK_IMAGE}'">
        <strong></strong>
      </td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td class="col-qty"><span class="qty-display"></span></td>
      <td class="col-date"></td>
      <td>
        <button type="button" class="btn outline small btn-history">변경 이력</button>
      </td>
    `;

    const cells = row.querySelectorAll("td");
    row.querySelector(".col-book strong").textContent = stock.originalTitle ?? "";
    syncOutOfStockWarning(row, stock);
    cells[2].appendChild(gradeBadge(stock));
    cells[3].textContent = stock.contentTypeName ?? "-";
    cells[4].textContent = stock.genreName ?? "-";
    cells[5].appendChild(levelBadge(stock.difficulty));
    cells[7].textContent = formatDate(stock.lastChangedAt);

    // 마스터 행은 보유수량을 직접 조작하지 않는다 — 하위 트리(사본)에서 조작한 결과를 표시만 한다
    syncMasterQuantityDisplay(row, stock);

    const itemsRow = buildItemsRow(stock);

    row.querySelector(".btn-history").addEventListener("click", () => openHistory(stock));

    row.addEventListener("click", (event) => {
      if (event.target.closest(".btn-history")) return; // 이력 버튼은 토글과 무관
      toggleItemsRow(row, itemsRow, stock);
    });

    tbody.appendChild(row);
    tbody.appendChild(itemsRow);
  });
}

/* 마스터 행의 보유수량 표시를 갱신 — content는 quantity를 갖지 않으므로 항상 stock.quantity(=item 집계값)를 그대로 보여준다 */
function syncMasterQuantityDisplay(masterRow, stock) {
  masterRow.querySelector(".qty-display").textContent = `${stock.quantity ?? 0}권`;
  masterRow.querySelector(".col-date").textContent = formatDate(stock.lastChangedAt);
  syncOutOfStockWarning(masterRow, stock);
}

/* 사용중(절판 아님)인데 보유수량이 0이면 위험재고로 보고 도서명을 빨간색으로 강조한다 */
function syncOutOfStockWarning(masterRow, stock) {
  masterRow.querySelector(".col-book strong").classList.toggle("title-danger", (stock.quantity ?? 0) === 0);
}

/* ===================== 마스터 도서 하위 트리 (실물 사본 목록 + 보유수량 수정) ===================== */

function buildItemsRow(stock) {
  const row = document.createElement("tr");
  row.className = "stock-items-row";
  row.hidden = true;
  row.innerHTML = `<td colspan="9"><div class="stock-items-panel"></div></td>`;
  return row;
}

async function toggleItemsRow(masterRow, itemsRow, stock) {
  const expanded = masterRow.classList.contains("expanded");

  if (expanded) {
    masterRow.classList.remove("expanded");
    itemsRow.hidden = true;
    return;
  }

  masterRow.classList.add("expanded");
  itemsRow.hidden = false;

  const panel = itemsRow.querySelector(".stock-items-panel");
  if (stock.items) {
    renderItemsPanel(panel, masterRow, itemsRow, stock);
    return;
  }

  await loadAndRenderItems(masterRow, itemsRow, stock);
}

/*
 * silent=true면 기존 표를 그대로 둔 채 데이터만 새로 받아서 갈아끼운다 — +/- 조작 직후 재조회 때
 * "불러오는 중" 문구로 표를 잠깐 비웠다가 다시 채우면 화면이 깜빡여 보여서, 최초로 펼칠 때만 로딩 문구를 쓴다.
 */
async function loadAndRenderItems(masterRow, itemsRow, stock, { silent = false } = {}) {
  const panel = itemsRow.querySelector(".stock-items-panel");
  if (!silent) panel.innerHTML = `<p class="stock-items-empty">불러오는 중입니다...</p>`;

  try {
    const response = await fetch(`/book/stock/items/${stock.contentId}`);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "하위도서를 불러오지 못했습니다.");

    stock.items = data.response ?? []; // 서버가 이미 bcode(판본)당 1행으로 qty를 집계해서 내려준다
    stock.quantity = stock.items.reduce((sum, item) => sum + (item.qty ?? 0), 0); // 총 보유수량 = 판본별 qty 합
    syncMasterQuantityDisplay(masterRow, stock);
    renderItemsPanel(panel, masterRow, itemsRow, stock);
  } catch (error) {
    console.error(error);
    panel.innerHTML = `<p class="stock-items-empty">하위도서를 불러오지 못했습니다.</p>`;
  }
}

function renderItemsPanel(panel, masterRow, itemsRow, stock) {
  panel.innerHTML = "";

  const table = document.createElement("table");
  table.className = "stock-items-table";
  table.innerHTML = `
    <thead>
      <tr>
        <th></th>
        <th>도서명</th>
        <th>바코드</th>
        <th>저자</th>
        <th>출판사</th>
        <th>보유수량</th>
        <th>최근 등록일</th>
      </tr>
    </thead>
    <tbody></tbody>
  `;

  const tbody = table.querySelector("tbody");
  const items = stock.items ?? [];

  if (items.length === 0) {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td colspan="7" class="stock-items-empty"></td>`;
    const cell = tr.querySelector("td");
    cell.append("보유 중인 사본이 없습니다. ");

    const addBtn = document.createElement("button");
    addBtn.type = "button";
    addBtn.className = "btn outline small";
    addBtn.textContent = "사본 추가";
    addBtn.addEventListener("click", () => addFirstCopy(masterRow, itemsRow, stock, addBtn));
    cell.appendChild(addBtn);

    tbody.appendChild(tr);
  } else {
    items.forEach((item) => tbody.appendChild(buildGroupRow(item, stock, masterRow, itemsRow)));
  }

  panel.appendChild(table);
}

/* 등록된 사본(bcode)이 하나도 없을 때 첫 사본을 등록 — 새 바코드는 서버가 자동 발급한다 */
async function addFirstCopy(masterRow, itemsRow, stock, button) {
  if (stock.busy) return;
  stock.busy = true;
  button.disabled = true;

  try {
    await postJson("/book/stock/update", { contentId: stock.contentId, quantity: 1 });
    stock.items = null;
    await loadAndRenderItems(masterRow, itemsRow, stock);
  } catch (error) {
    alert(error.message ?? "사본 등록 중 오류가 발생했습니다.");
  } finally {
    stock.busy = false;
  }
}

/* 판본(bcode) 1행 — 서버가 이미 이 bcode의 qty를 집계해서 내려주므로 그 값을 그대로 스테퍼에 쓴다 */
function buildGroupRow(item, stock, masterRow, itemsRow) {
  const tr = document.createElement("tr");
  tr.innerHTML = `
    <td class="item-thumb"><img src="${item.imageUrl || DEFAULT_BOOK_IMAGE}" alt="" onerror="this.src='${DEFAULT_BOOK_IMAGE}'"></td>
    <td>${item.bookTitle ?? "-"}</td>
    <td>${item.bcode ?? "-"}</td>
    <td>${item.author ?? "-"}</td>
    <td>${item.publisher ?? "-"}</td>
    <td>
      <div class="stepper">
        <button type="button" class="stepper-minus" aria-label="수량 감소">-</button>
        <input type="text" class="stepper-value" inputmode="numeric">
        <button type="button" class="stepper-plus" aria-label="수량 증가">+</button>
      </div>
    </td>
    <td>${formatDate(item.registeredAt)}</td>
  `;

  tr.querySelector(".stepper-value").value = item.qty ?? 0;
  bindGroupStepper(tr, stock, item.bcode, masterRow, itemsRow);
  return tr;
}

function gradeBadge(stock) {
  const badge = document.createElement("span");
  badge.className = `grade ${GRADE_BADGE_CLASS[stock.schoolyear] ?? ""}`.trim();
  badge.textContent = stock.schoolyearName ?? "-";
  return badge;
}

function levelBadge(difficulty) {
  if (!difficulty) {
    const empty = document.createElement("span");
    empty.textContent = "-";
    return empty;
  }

  const badge = document.createElement("span");
  badge.className = `level ${LEVEL_BADGE_CLASS[difficulty] ?? ""}`.trim();
  badge.textContent = difficulty;
  return badge;
}

/* 최근 변경일 — 한 번도 조정한 적이 없으면 표시할 값이 없다 */
function formatDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}.${month}.${day}`;
}

/* ===================== 보유수량 조정 (bcode 단위, 저장 버튼 없이 즉시 반영) ===================== */

/*
 * 보유수량은 하위 트리에서 bcode(판본) 그룹별로 +/- 로 조작한다. 마스터 행은 그 총합을 표시만 하며
 * (DB에 별도 quantity를 갖지 않음), 그 동기화는 서버 호출 없이 JS에서 화면 값만 맞춘다.
 */
function bindGroupStepper(container, stock, bcode, masterRow, itemsRow) {
  const input = container.querySelector(".stepper-value");
  const minusBtn = container.querySelector(".stepper-minus");
  const plusBtn = container.querySelector(".stepper-plus");
  const currentQty = () => Number(input.value) || 0;

  minusBtn.addEventListener("click", () => applyGroupQuantity(container, stock, bcode, currentQty() - 1, masterRow, itemsRow));
  plusBtn.addEventListener("click", () => applyGroupQuantity(container, stock, bcode, currentQty() + 1, masterRow, itemsRow));

  // 직접 입력은 포커스를 벗어나거나 엔터를 쳤을 때 확정 (타이핑 중간값이 그대로 저장되지 않도록)
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") input.blur();
  });

  input.addEventListener("blur", () => {
    const typed = Number(input.value.replace(/[^0-9]/g, ""));
    if (!Number.isInteger(typed) || typed === currentQty()) {
      input.value = currentQty();
      return;
    }
    applyGroupQuantity(container, stock, bcode, typed, masterRow, itemsRow);
  });
}

async function applyGroupQuantity(container, stock, bcode, target, masterRow, itemsRow) {
  if (target < 0) return;

  const input = container.querySelector(".stepper-value");
  const buttons = container.querySelectorAll(".stepper button");
  const prevValue = input.value;
  const currentQty = Number(prevValue) || 0;

  // 줄이는 조작은 누를 때마다 사유를 받는다 (일괄 등록 팝업과 동일한 규칙)
  let memo = null;
  if (target < currentQty) {
    const reason = prompt(`${stock.originalTitle ?? "이 도서"}: ${currentQty}권 → ${target}권으로 줄입니다. 사유를 입력해주세요.`);
    if (!reason || !reason.trim()) return;
    memo = reason.trim();
  }

  // 연타로 요청이 겹치면 마지막 응답이 이전 값으로 되돌릴 수 있어, 처리 중엔 조작을 막는다
  if (stock.busy) return;
  stock.busy = true;
  buttons.forEach((button) => (button.disabled = true));

  try {
    const quantity = await postJson("/book/stock/items/update", { contentId: stock.contentId, bcode, quantity: target, memo });
    input.value = quantity;
    stock.lastChangedAt = new Date().toISOString();
    stock.items = null; // bcode 구성이 바뀌었으니 다시 불러와서 그룹/총 수량을 맞춘다

    await loadAndRenderItems(masterRow, itemsRow, stock, { silent: true });
  } catch (error) {
    input.value = prevValue;
    alert(error.message ?? "보유 수량 변경 중 오류가 발생했습니다.");
  } finally {
    stock.busy = false;
    // 위 loadAndRenderItems가 패널을 다시 그렸다면 container/buttons는 이미 화면에서 떨어져나간 노드라 이 처리는 아무 효과가 없다(안전)
    buttons.forEach((button) => (button.disabled = false));
  }
}

/* ===================== 변경 이력 ===================== */

function initHistoryModal() {
  const modal = document.getElementById("historyModal");

  document.getElementById("btnCloseHistory").addEventListener("click", () => (modal.hidden = true));
  modal.addEventListener("click", (event) => {
    if (event.target === modal) modal.hidden = true;
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") modal.hidden = true;
  });
}

async function openHistory(stock) {
  const modal = document.getElementById("historyModal");
  const list = document.getElementById("historyList");

  document.getElementById("historyModalTitle").textContent = `${stock.originalTitle} — 보유 수량 변경 이력`;
  list.innerHTML = `<li class="history-empty">불러오는 중입니다...</li>`;
  modal.hidden = false;

  try {
    const response = await fetch(`/book/stock/history/${stock.contentId}`);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "이력을 불러오지 못했습니다.");

    renderHistory(data.response ?? []);
  } catch (error) {
    console.error(error);
    list.innerHTML = `<li class="history-empty">이력을 불러오지 못했습니다.</li>`;
  }
}

function renderHistory(logs) {
  const list = document.getElementById("historyList");

  if (logs.length === 0) {
    list.innerHTML = `<li class="history-empty">변경 이력이 없습니다.</li>`;
    return;
  }

  list.innerHTML = "";

  logs.forEach((log) => {
    const item = document.createElement("li");
    item.className = "history-item";

    const summaryLine = document.createElement("div");
    summaryLine.className = "history-summary-line";

    const summary = document.createElement("span");
    summary.textContent = `${log.beforeQty}권 → ${log.afterQty}권 (${log.changedBy ?? "-"})`;

    const date = document.createElement("span");
    date.className = "history-date";
    date.textContent = formatDateTime(log.changedAt);

    summaryLine.append(summary, date);
    item.appendChild(summaryLine);

    if (log.memo) {
      const memo = document.createElement("p");
      memo.className = "history-memo";
      memo.textContent = `사유: ${log.memo}`;
      item.appendChild(memo);
    }

    list.appendChild(item);
  });
}

function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  const hour = String(date.getHours()).padStart(2, "0");
  const minute = String(date.getMinutes()).padStart(2, "0");
  return `${formatDate(value)} ${hour}:${minute}`;
}

/*
 * ===================== 수량 일괄 등록 팝업 =====================
 * 트리(펼치기)는 도서 1권씩 확인/조정하기엔 좋지만 수백 권을 한꺼번에 세팅하기엔 클릭이 너무 많다.
 * 이 팝업은 학년별 탭으로 나눠서 보여주고, 값은 입력만 해두었다가 "이 탭 저장"을 눌러야 서버에 반영된다.
 *
 * bulkSteps(contentId → 단계 배열)는 그 도서에서 사용자가 확정한 값 변화를 순서대로 담는다. -를 눌러서
 * 값이 낮아질 때마다(=감소) 매번 사유를 새로 묻고, 그 사유와 함께 단계 하나로 추가한다 — 2번 줄이면 단계
 * 2개, 각각 자기 사유를 가진다. 늘어나는 건 사유 없이 그냥 단계로 쌓인다. 저장하면 그 도서의 단계들을
 * 순서대로 서버에 보내 서버가 하나씩 반영하므로, 변경 이력에도 단계별로 각각 남는다.
 * 탭을 옮기거나 팝업을 닫아도 단계는 그대로 남아있고, 실수로 닫기/새로고침/뒤로가기 하면 날아가므로
 * beforeunload와 팝업 닫기 모두에서 확인을 받는다.
 */
let bulkActiveGrade = null;
const bulkSteps = new Map(); // contentId → [{quantity, memo}, ...] (순서대로 반영할 단계들)

function initBulkModal() {
  const modal = document.getElementById("bulkModal");

  document.getElementById("btnBulkStock").addEventListener("click", openBulkModal);
  document.getElementById("btnCloseBulk").addEventListener("click", () => confirmCloseBulkModal());
  document.getElementById("btnBulkSave").addEventListener("click", saveBulkTab);
  modal.addEventListener("click", (event) => {
    if (event.target === modal) confirmCloseBulkModal();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !modal.hidden) confirmCloseBulkModal();
  });

  // 저장 안 한 변경사항이 있는 채로 새로고침/탭 닫기/뒤로가기 하는 것을 막는다
  window.addEventListener("beforeunload", (event) => {
    if (bulkSteps.size === 0) return;
    event.preventDefault();
    event.returnValue = "";
  });
}

function confirmCloseBulkModal() {
  if (bulkSteps.size > 0 && !confirm(`저장하지 않은 변경사항이 ${bulkSteps.size}건 있습니다. 닫으시겠습니까?`)) {
    return;
  }
  document.getElementById("bulkModal").hidden = true;
  renderStocks(); // 일괄 등록 중 저장된 수량/최근 변경일을 메인 목록에도 반영
}

function openBulkModal() {
  document.getElementById("bulkModal").hidden = false;
  renderBulkTabs();
}

/* 조회된 목록(stocks)에 실제로 존재하는 학년 코드만, 필터 셀렉트의 학년 순서 그대로 */
function gradeOrderVisible() {
  const gradesInList = new Set(stocks.map((s) => s.schoolyear ?? ""));
  return Array.from(document.querySelectorAll("#filterGrade option"))
    .filter((option) => option.value && gradesInList.has(option.value));
}

function tabHasPending(gradeCode) {
  return stocks.some((s) => (s.schoolyear ?? "") === gradeCode && bulkSteps.has(s.contentId));
}

/* 이 도서에 대해 화면에 표시 중인 "현재 값" — 확정한 단계가 있으면 마지막 단계의 값, 없으면 원본 보유수량 */
function bulkCurrentValue(stock) {
  const steps = bulkSteps.get(stock.contentId);
  return steps && steps.length ? steps[steps.length - 1].quantity : stock.quantity ?? 0;
}

function renderBulkTabs() {
  const tabs = gradeOrderVisible();

  if (!bulkActiveGrade || !tabs.some((t) => t.value === bulkActiveGrade)) {
    bulkActiveGrade = tabs[0]?.value ?? null;
  }

  const tabsEl = document.getElementById("bulkTabs");
  tabsEl.innerHTML = "";
  tabs.forEach((tab) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `bulk-tab ${tab.value === bulkActiveGrade ? "active" : ""}`.trim();
    btn.textContent = tab.textContent;
    if (tabHasPending(tab.value)) btn.insertAdjacentHTML("beforeend", `<span class="bulk-tab-dot"></span>`);
    btn.addEventListener("click", () => {
      bulkActiveGrade = tab.value;
      renderBulkTabs();
    });
    tabsEl.appendChild(btn);
  });

  renderBulkList();
}

function renderBulkList() {
  const tbody = document.getElementById("bulkListBody");
  tbody.innerHTML = "";

  const rows = stocks.filter((s) => (s.schoolyear ?? "") === bulkActiveGrade);
  if (rows.length === 0) {
    tbody.innerHTML = `<tr class="stock-empty"><td colspan="3">조회된 도서가 없습니다.</td></tr>`;
    updatePendingHint();
    return;
  }

  rows.forEach((stock) => tbody.appendChild(buildBulkRow(stock)));
  updatePendingHint();
}

function updatePendingHint() {
  const hint = document.getElementById("bulkPendingHint");
  const stepCount = Array.from(bulkSteps.values()).reduce((sum, steps) => sum + steps.length, 0);
  hint.textContent = bulkSteps.size > 0 ? `저장 대기 중 ${bulkSteps.size}권 / 단계 ${stepCount}건 (전체 탭 합산)` : "";
}

function buildBulkRow(stock) {
  const tr = document.createElement("tr");
  tr.dataset.contentId = stock.contentId;

  const steps = bulkSteps.get(stock.contentId);
  const lastStep = steps && steps.length ? steps[steps.length - 1] : null;
  if (lastStep) tr.classList.add(lastStep.memo ? "bulk-row-decrease" : "bulk-row-dirty");

  tr.innerHTML = `
    <td class="item-thumb"><img src="${stock.imageUrl || DEFAULT_BOOK_IMAGE}" alt="" onerror="this.src='${DEFAULT_BOOK_IMAGE}'"></td>
    <td class="bulk-title"></td>
    <td>
      <div class="stepper">
        <button type="button" class="stepper-minus" aria-label="수량 감소">-</button>
        <input type="text" class="bulk-qty-input stepper-value" inputmode="numeric">
        <button type="button" class="stepper-plus" aria-label="수량 증가">+</button>
      </div>
    </td>
  `;

  tr.querySelector(".bulk-title").textContent = stock.originalTitle ?? "";

  const input = tr.querySelector(".bulk-qty-input");
  input.value = bulkCurrentValue(stock);
  input.title = steps?.length ? steps.filter((s) => s.memo).map((s) => `${s.memo}`).join(" / ") : "";
  bindBulkInput(tr, input, stock);

  return tr;
}

function bindBulkInput(row, input, stock) {
  const stepTo = (target) => {
    if (target < 0) return;
    addBulkStep(row, input, stock, target);
  };

  row.querySelector(".stepper-minus").addEventListener("click", () => stepTo(bulkCurrentValue(stock) - 1));
  row.querySelector(".stepper-plus").addEventListener("click", () => stepTo(bulkCurrentValue(stock) + 1));

  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") input.blur();
  });

  input.addEventListener("blur", () => {
    const typed = Number(input.value.replace(/[^0-9]/g, ""));
    if (!Number.isInteger(typed) || typed < 0) {
      input.value = bulkCurrentValue(stock);
      return;
    }
    addBulkStep(row, input, stock, typed);
  });
}

/*
 * 값이 바뀔 때마다 이 도서의 단계 목록(bulkSteps)에 한 단계를 추가한다. 원본 수량으로 정확히 돌아오면
 * 그동안 쌓은 단계를 전부 없앤 걸로 친다(순변화가 없으니 저장할 것도 없음). 낮추는 단계는 그때마다 새
 * 사유를 물어보고 취소하면 그 단계 자체를 만들지 않는다 — 늘리는 단계는 사유 없이 바로 추가된다.
 */
function addBulkStep(row, input, stock, target) {
  if (target === (stock.quantity ?? 0)) {
    bulkSteps.delete(stock.contentId);
    input.value = target;
    input.title = "";
    row.classList.remove("bulk-row-dirty", "bulk-row-decrease");
    updatePendingHint();
    renderBulkTabDots();
    return;
  }

  const current = bulkCurrentValue(stock);
  if (target === current) return;

  let memo = null;
  if (target < current) {
    const reason = prompt(`${stock.originalTitle ?? "이 도서"}: ${current}권 → ${target}권으로 줄입니다. 사유를 입력해주세요.`);
    if (!reason || !reason.trim()) {
      input.value = current; // 취소하면 직전 값으로 되돌린다
      return;
    }
    memo = reason.trim();
  }

  const steps = bulkSteps.get(stock.contentId) ?? [];
  steps.push({ quantity: target, memo });
  bulkSteps.set(stock.contentId, steps);

  input.value = target;
  input.title = memo ? `감소 사유: ${memo}` : "";
  row.classList.remove("bulk-row-dirty", "bulk-row-decrease");
  row.classList.add(memo ? "bulk-row-decrease" : "bulk-row-dirty");

  updatePendingHint();
  renderBulkTabDots();
}

/* 탭 버튼 자체는 다시 그리지 않고(포커스/스크롤 유지) 대기중 표시(점)만 갱신 */
function renderBulkTabDots() {
  const tabs = gradeOrderVisible();
  document.querySelectorAll("#bulkTabs .bulk-tab").forEach((btn, index) => {
    btn.querySelector(".bulk-tab-dot")?.remove();
    const code = tabs[index]?.value;
    if (code && tabHasPending(code)) btn.insertAdjacentHTML("beforeend", `<span class="bulk-tab-dot"></span>`);
  });
}

/*
 * 현재 활성 탭에 속한, 저장 대기 단계가 있는 도서만 모아서 반영한다. 서버는 도서별로 단계를 순서대로
 * 재생하다가 하나가 실패하면 그 도서만 거기서 멈춘다 — 앞서 성공한 단계는 그대로 반영된 채 유지되므로,
 * 실패해도 이미 성공한 단계를 다시 보내는 게 안전하다(같은 목표수량으로 재요청하면 서버가 그냥 무시한다).
 */
async function saveBulkTab() {
  const targets = stocks.filter((s) => (s.schoolyear ?? "") === bulkActiveGrade && bulkSteps.has(s.contentId));
  if (targets.length === 0) {
    alert("이 탭에 저장할 변경사항이 없습니다.");
    return;
  }

  const saveBtn = document.getElementById("btnBulkSave");
  saveBtn.disabled = true;
  saveBtn.textContent = "저장 중...";

  try {
    const items = targets.map((s) => ({ contentId: s.contentId, steps: bulkSteps.get(s.contentId) }));
    const results = await postJson("/book/stock/bulk-update", { items });

    const failures = [];
    results.forEach((result) => {
      const stock = stocks.find((s) => s.contentId === result.contentId);
      if (!stock) return;

      if (result.quantity != null) {
        stock.quantity = result.quantity;
        stock.lastChangedAt = new Date().toISOString();
        stock.items = null; // 트리를 다시 펼치면 최신 판본 구성을 다시 불러오도록
      }

      if (result.success) {
        bulkSteps.delete(stock.contentId);
      } else {
        failures.push(`${stock.originalTitle ?? stock.contentId}: ${result.message ?? "저장 실패"}`);
      }
    });

    renderBulkTabs();
    alert(
      failures.length === 0
        ? "저장되었습니다."
        : `${results.length - failures.length}건 저장, ${failures.length}건 실패했습니다.\n\n${failures.join("\n")}`
    );

    // 실패한 항목은 계속 저장 대기 상태(노란/빨간 표시)로 남아 재시도할 수 있다
  } catch (error) {
    alert(error.message ?? "일괄 저장 중 오류가 발생했습니다.");
  } finally {
    saveBtn.disabled = false;
    saveBtn.textContent = "이 탭 저장";
  }
}

