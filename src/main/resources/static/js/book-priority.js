document.addEventListener("DOMContentLoaded", async () => {
  renderGradeTabs();
  initSearch();
  initActionButtons();
  initUnsavedOrderGuard();
  await loadBooks();
});

const SCHOOLYEAR_CODES = [
  { code: "01", name: "초1" },
  { code: "02", name: "초2" },
  { code: "03", name: "초3" },
  { code: "04", name: "초4" },
  { code: "05", name: "초5" },
  { code: "06", name: "초6" },
  { code: "07", name: "중등" },
];

const DIFFICULTY_BADGE_CLASS = {
  하: "diff-low",
  중: "diff-mid",
  상: "diff-high",
};

let activeGrade = SCHOOLYEAR_CODES[0].code;
let gradeBooks = []; // 현재 학년의 전체 도서 (표시 순서 = 순위)
let originalOrder = []; // 조회 직후(=마지막으로 확정된) 순서 스냅샷 — 드래그 변경 여부 판단용

/* 학년 탭 렌더링 */
function renderGradeTabs() {
  const wrap = document.getElementById("gradeTabs");
  if (!wrap) return;

  wrap.innerHTML = "";

  SCHOOLYEAR_CODES.forEach(({ code, name }) => {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = name;
    button.classList.toggle("active", code === activeGrade);

    button.addEventListener("click", async () => {
      if (code === activeGrade) return;
      if (hasUnsavedOrderChange() && !confirm("순위 변경사항이 저장되지 않았습니다. 정말로 나가시겠습니까?")) return;

      activeGrade = code;
      wrap.querySelectorAll("button").forEach((b) => b.classList.remove("active"));
      button.classList.add("active");
      document.getElementById("rankingSearch").value = "";
      await loadBooks();
    });

    wrap.appendChild(button);
  });
}

/* 학년별 도서 목록 조회 */
async function loadBooks() {
  const listEl = document.getElementById("rankingList");
  listEl.innerHTML = '<li class="ranking-empty">불러오는 중...</li>';

  try {
    const response = await fetch(`/book/search?schoolYear=${activeGrade}`);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "도서 목록 조회에 실패했습니다.");

    gradeBooks = data.response ?? [];
    originalOrder = gradeBooks.map((b) => String(b.contentId));
    renderStats(gradeBooks);
    renderList(gradeBooks);
  } catch (error) {
    console.error(error);
    listEl.innerHTML = '<li class="ranking-empty">조회 중 오류가 발생했습니다.</li>';
  }
}

/* 상단 통계 (학년 전체 기준, 검색어와 무관) */
function renderStats(books) {
  const active = books.filter((b) => b.state === "Y").length;
  const inactive = books.filter((b) => b.state === "N").length;

  document.getElementById("statTotal").textContent = `${books.length}권`;
  document.getElementById("statActive").textContent = `${active}권`;
  document.getElementById("statInactive").textContent = `${inactive}권`;
}

/* 순위 목록 렌더링 (검색어가 있으면 필터링만 하고, 원래 순번은 유지) */
function renderList(books) {
  const listEl = document.getElementById("rankingList");
  const keyword = document.getElementById("rankingSearch").value.trim().toLowerCase();
  const isFiltered = keyword.length > 0;

  listEl.innerHTML = "";

  const visible = books
    .map((book, index) => ({ book, rank: index + 1 }))
    .filter(({ book }) => !isFiltered || (book.originalTitle ?? "").toLowerCase().includes(keyword));

  if (!visible.length) {
    listEl.innerHTML = '<li class="ranking-empty">등록된 도서가 없습니다.</li>';
    return;
  }

  visible.forEach(({ book, rank }) => listEl.appendChild(buildRankingRow(book, rank, isFiltered)));
}

/* 순위 카드(행) 생성 */
function buildRankingRow(book, rank, disableDrag) {
  const li = document.createElement("li");
  li.className = "ranking-row";
  li.dataset.contentId = book.contentId;

  const tags = (book.keywords ?? "")
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean)
    .map((t) => `#${t}`)
    .join(" ");

  const diffClass = DIFFICULTY_BADGE_CLASS[book.difficulty] ?? "diff-mid";

  li.innerHTML = `
    <span class="drag-handle"><i class="fa-solid fa-grip-vertical"></i></span>
    <span class="rank-number">${rank}</span>
    <img class="rank-thumb" src="${book.imageUrl || "/images/book-sample.png"}" alt="">
    <div class="rank-info">
      <span class="rank-title">${book.originalTitle ?? ""}</span>
      <span class="rank-type">${book.contentTypeName ?? ""}</span>
      <span class="rank-tags">${tags}</span>
    </div>
    <div class="rank-badges">
      ${book.state === "N" ? '<span class="state-badge">절판</span>' : ""}
      <span class="diff-badge ${diffClass}">${book.difficulty ?? ""}</span>
    </div>
  `;

  if (!disableDrag) bindDragEvents(li);

  return li;
}

/*
 * 드래그로 순서 변경 — 네이티브 HTML5 DnD 대신 Pointer Events로 직접 구현.
 * 이유: 네이티브 드래그는 브라우저가 dragstart 시점 스냅샷("잔상")을 만들어 커서를 따라다니는데,
 * 이 잔상은 고정된 이미지라 순번이 바뀌어도 갱신되지 않고, 커서를 따라 좌우로도 움직인다.
 * Pointer Events로 직접 다루면 잔상 없이 실제 행이 그 자리에서 위/아래로만 재배치된다.
 */
function bindDragEvents(row) {
  const handle = row.querySelector(".drag-handle");
  if (!handle) return;

  handle.addEventListener("pointerdown", (event) => {
    event.preventDefault();
    row.classList.add("dragging");
    handle.setPointerCapture(event.pointerId);

    const listEl = document.getElementById("rankingList");

    const onPointerMove = (moveEvent) => {
      const afterElement = getDragAfterElement(listEl, moveEvent.clientY);
      if (afterElement == null) {
        listEl.appendChild(row);
      } else if (afterElement !== row) {
        listEl.insertBefore(row, afterElement);
      }
      renumberVisibleRows();
    };

    const onPointerUp = () => {
      row.classList.remove("dragging");
      syncOrderFromDom();
      handle.releasePointerCapture(event.pointerId);
      document.removeEventListener("pointermove", onPointerMove);
      document.removeEventListener("pointerup", onPointerUp);
    };

    document.addEventListener("pointermove", onPointerMove);
    document.addEventListener("pointerup", onPointerUp);
  });
}

/* 커서(y좌표) 기준으로, 드래그 중인 행이 들어갈 위치의 "다음 행"을 찾는다 (없으면 맨 끝) */
function getDragAfterElement(container, y) {
  const rows = [...container.querySelectorAll(".ranking-row:not(.dragging)")];

  return rows.reduce(
    (closest, row) => {
      const box = row.getBoundingClientRect();
      const offset = y - box.top - box.height / 2;
      if (offset < 0 && offset > closest.offset) {
        return { offset, element: row };
      }
      return closest;
    },
    { offset: Number.NEGATIVE_INFINITY, element: null }
  ).element;
}

/* 드래그 중 실시간으로 순번 뱃지만 갱신 (검색 필터가 걸려있으면 순번은 원래 순위를 유지하므로 스킵) */
function renumberVisibleRows() {
  const listEl = document.getElementById("rankingList");
  if (!listEl) return;

  listEl.querySelectorAll(".ranking-row").forEach((row, index) => {
    const numberEl = row.querySelector(".rank-number");
    if (numberEl) numberEl.textContent = index + 1;
  });
}

/* 드롭 완료 시, 현재 DOM 순서를 그대로 gradeBooks 배열에 반영 */
function syncOrderFromDom() {
  const listEl = document.getElementById("rankingList");
  if (!listEl) return;

  const domOrder = [...listEl.querySelectorAll(".ranking-row")].map((row) => row.dataset.contentId);
  if (!domOrder.length) return;

  gradeBooks.sort((a, b) => domOrder.indexOf(String(a.contentId)) - domOrder.indexOf(String(b.contentId)));
}

/* 마지막으로 조회(확정)된 순서와 현재 순서가 다른지 (=저장 안 된 드래그 변경이 있는지) */
function hasUnsavedOrderChange() {
  const current = gradeBooks.map((b) => String(b.contentId));
  return JSON.stringify(current) !== JSON.stringify(originalOrder);
}

/* 순위 변경 후 학년 탭 전환(위에서 처리)이 아닌, 뒤로가기/새로고침/탭 닫기 시 경고 */
function initUnsavedOrderGuard() {
  window.addEventListener("beforeunload", (event) => {
    if (!hasUnsavedOrderChange()) return;
    event.preventDefault();
    event.returnValue = "";
  });
}

/* 도서명 검색 */
function initSearch() {
  document.getElementById("rankingSearch")?.addEventListener("input", () => renderList(gradeBooks));
}

/* 엑셀 다운로드 / 저장 / 예약 목록 — 추후 연동 예정 */
function initActionButtons() {
  document.getElementById("btnExcelDownload")?.addEventListener("click", () => {
    alert("엑셀 다운로드는 추후 지원 예정입니다.");
  });

  document.getElementById("btnSaveRanking")?.addEventListener("click", () => {
    alert("순위 저장은 추후 지원 예정입니다.");
  });

  document.getElementById("btnReservationList")?.addEventListener("click", () => {
    alert("우선순위 변경 예약 기능은 추후 지원 예정입니다.");
  });
}
