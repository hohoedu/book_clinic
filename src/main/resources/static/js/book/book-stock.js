/*
 * 보유도서 설정 — 로그인 직원 센터의 도서별 보유 수량(실물 사본 수) 조회/조정
 *
 * 이 화면에는 저장 버튼이 없다. +/- 를 누르거나 수량을 직접 입력하면 그 즉시 서버에 반영되고,
 * 서버가 돌려준 실제 수량으로 화면을 다시 맞춘다(대여 중이라 못 줄인 경우 등 화면과 DB가 어긋나지 않게).
 */

document.addEventListener("DOMContentLoaded", async () => {
  initFilters();
  initHistoryModal();
  initPwaInstallButton();
  await loadStocks();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TOKEN = "hohoedu-master-csrf-token";
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
      [CSRF_HEADER]: CSRF_TOKEN,
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
    row.dataset.contentId = stock.contentId;
    row.innerHTML = `
      <td class="col-no">${index + 1}</td>
      <td class="col-book">
        <img src="${stock.imageUrl || DEFAULT_BOOK_IMAGE}" alt="" onerror="this.src='${DEFAULT_BOOK_IMAGE}'">
        <strong></strong>
      </td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td>
        <div class="stepper">
          <button type="button" class="stepper-minus" aria-label="수량 감소">-</button>
          <input type="text" class="stepper-value" inputmode="numeric">
          <button type="button" class="stepper-plus" aria-label="수량 증가">+</button>
        </div>
      </td>
      <td class="col-date"></td>
      <td>
        <button type="button" class="btn outline small btn-history">변경 이력</button>
      </td>
    `;

    const cells = row.querySelectorAll("td");
    row.querySelector(".col-book strong").textContent = stock.originalTitle ?? "";
    cells[2].appendChild(gradeBadge(stock));
    cells[3].textContent = stock.contentTypeName ?? "-";
    cells[4].textContent = stock.genreName ?? "-";
    cells[5].appendChild(levelBadge(stock.difficulty));
    cells[7].textContent = formatDate(stock.lastChangedAt);

    row.querySelector(".stepper-value").value = stock.quantity ?? 0;
    bindStepper(row, stock);
    row.querySelector(".btn-history").addEventListener("click", () => openHistory(stock));

    tbody.appendChild(row);
  });
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

/* ===================== 수량 조정 (저장 버튼 없이 즉시 반영) ===================== */

function bindStepper(row, stock) {
  const input = row.querySelector(".stepper-value");
  const minusBtn = row.querySelector(".stepper-minus");
  const plusBtn = row.querySelector(".stepper-plus");

  minusBtn.addEventListener("click", () => applyQuantity(row, stock, (stock.quantity ?? 0) - 1));
  plusBtn.addEventListener("click", () => applyQuantity(row, stock, (stock.quantity ?? 0) + 1));

  // 직접 입력은 포커스를 벗어나거나 엔터를 쳤을 때 확정 (타이핑 중간값이 그대로 저장되지 않도록)
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") input.blur();
  });

  input.addEventListener("blur", () => {
    const typed = Number(input.value.replace(/[^0-9]/g, ""));
    if (!Number.isInteger(typed) || typed === stock.quantity) {
      input.value = stock.quantity ?? 0;
      return;
    }
    applyQuantity(row, stock, typed);
  });
}

async function applyQuantity(row, stock, target) {
  if (target < 0) return;

  const stepper = row.querySelector(".stepper");
  const input = row.querySelector(".stepper-value");
  const buttons = row.querySelectorAll(".stepper button");

  // 연타로 요청이 겹치면 마지막 응답이 이전 값으로 되돌릴 수 있어, 처리 중엔 조작을 막는다
  if (stepper.dataset.busy === "true") return;
  stepper.dataset.busy = "true";
  buttons.forEach((button) => (button.disabled = true));

  try {
    const quantity = await postJson("/book/stock/update", { contentId: stock.contentId, quantity: target });
    stock.quantity = quantity;
    stock.lastChangedAt = new Date().toISOString();
    input.value = quantity;
    row.querySelector(".col-date").textContent = formatDate(stock.lastChangedAt);
  } catch (error) {
    input.value = stock.quantity ?? 0;
    alert(error.message ?? "보유 수량 변경 중 오류가 발생했습니다.");
  } finally {
    stepper.dataset.busy = "false";
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

    const summary = document.createElement("span");
    summary.textContent = `${log.beforeQty}권 → ${log.afterQty}권 (${log.changedBy ?? "-"})`;

    const date = document.createElement("span");
    date.className = "history-date";
    date.textContent = formatDateTime(log.changedAt);

    item.append(summary, date);
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

/* ===================== PWA 설치 버튼 ===================== */

function initPwaInstallButton() {
  const installBtn = document.getElementById("pwaInstallBtn");
  if (!installBtn) return;

  let deferredInstallPrompt = null;

  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    deferredInstallPrompt = event;
    installBtn.hidden = false;
  });

  installBtn.addEventListener("click", async () => {
    if (!deferredInstallPrompt) return;
    deferredInstallPrompt.prompt();
    await deferredInstallPrompt.userChoice;
    deferredInstallPrompt = null;
    installBtn.hidden = true;
  });

  window.addEventListener("appinstalled", () => {
    installBtn.hidden = true;
    deferredInstallPrompt = null;
  });
}
