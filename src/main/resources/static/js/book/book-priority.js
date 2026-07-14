document.addEventListener("DOMContentLoaded", async () => {
  renderGradeTabs();
  initSearch();
  initActionButtons();
  initDraftModal();
  initUnsavedOrderGuard();
  await loadBooks();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TOKEN = "hohoedu-master-csrf-token";

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

const activeYear = String(new Date().getFullYear() + 1); // 이 화면은 기본적으로 "다음 해" 순위를 미리 준비하는 용도 — 연도 선택 UI는 아직 없음
const CURRENT_YEAR = String(new Date().getFullYear()); // 저장 시 "이번년도 즉시 적용"을 선택하면 이 연도로 저장한다
let activeGrade = SCHOOLYEAR_CODES[0].code;
let viewYear = getStoredViewYear(activeGrade); // 지금 화면에 조회/표시 중인 연도 — 즉시 적용 직후엔 새로고침해도 CURRENT_YEAR로 유지되도록 학년별로 세션에 기억해둔다
let gradeBooks = []; // 현재 학년의 전체 도서 (표시 순서 = 순위)
let originalOrder = []; // 조회 직후(=마지막으로 확정된) 순서 스냅샷 — 드래그 변경 여부 판단용

/* 학년별 "즉시 적용" 여부를 세션에 기억해서, 새로고침해도 방금 적용한 연도 화면이 유지되게 한다 */
function viewYearStorageKey(grade) {
  return `priority-view-year-${grade}`;
}

function getStoredViewYear(grade) {
  return sessionStorage.getItem(viewYearStorageKey(grade)) === "current" ? CURRENT_YEAR : activeYear;
}

function setStoredViewYear(grade, year) {
  sessionStorage.setItem(viewYearStorageKey(grade), year === CURRENT_YEAR ? "current" : "next");
}

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

  return data;
}

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
      viewYear = getStoredViewYear(activeGrade); // 학년마다 마지막으로 확인한 화면(다음해 준비 / 올해 적용됨)을 그대로 이어서 보여준다
      wrap.querySelectorAll("button").forEach((b) => b.classList.remove("active"));
      button.classList.add("active");
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
    const response = await fetch(`/priority?year=${viewYear}&schoolYear=${activeGrade}`);
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
/*
 * 이 학년에 저장된 순위가 하나도 없으면(=아직 초안을 한 번도 저장 안 함) 전부 순위가 매겨진 것처럼
 * 번호를 매기고 바로 드래그할 수 있게 보여준다. "순위 없음" 구분선은 이미 순위가 있는 초안에
 * 새로 추가된 도서처럼, 일부만 순위 밖에 있는 경우에만 보여준다.
 */
function renderList(books) {
  const listEl = document.getElementById("rankingList");
  const keyword = document.getElementById("rankingSearch").value.trim().toLowerCase();
  const isFiltered = keyword.length > 0;

  listEl.innerHTML = "";

  const visible = books.filter(
    (book) => !isFiltered || (book.originalTitle ?? "").toLowerCase().includes(keyword)
  );

  if (!visible.length) {
    listEl.innerHTML = '<li class="ranking-empty">등록된 도서가 없습니다.</li>';
    return;
  }

  const hasAnyRanked = visible.some((book) => book.sortOrder != null);

  if (!hasAnyRanked) {
    visible.forEach((book, index) => listEl.appendChild(buildRankingRow(book, index + 1, isFiltered)));
    refreshRowDividers(listEl);
    return;
  }

  const ranked = visible.filter((book) => book.sortOrder != null);
  const unranked = visible.filter((book) => book.sortOrder == null);

  ranked.forEach((book, index) => listEl.appendChild(buildRankingRow(book, index + 1, isFiltered)));

  if (unranked.length) {
    const divider = document.createElement("li");
    divider.className = "ranking-list-divider";
    divider.innerHTML = "<span>아직 순위가 없는 도서</span>";
    listEl.appendChild(divider);

    unranked.forEach((book) => listEl.appendChild(buildRankingRow(book, null, true)));
  }

  refreshRowDividers(listEl);
}

/* 행과 행 사이 구분선을 현재 DOM 순서 기준으로 다시 그린다 (드래그로 순서가 바뀌어도 항상 올바른 위치에 있도록) */
function refreshRowDividers(container) {
  container.querySelectorAll(".row-divider").forEach((el) => el.remove());

  const isRowLike = (el) => el.classList.contains("ranking-row") || el.classList.contains("ranking-row-placeholder");

  let prevWasRow = false;
  [...container.children].forEach((child) => {
    if (child.classList.contains("ranking-list-divider")) {
      prevWasRow = false; // "순위 없음" 구분선 다음엔 선을 안 붙인다
      return;
    }
    if (!isRowLike(child)) return;

    if (prevWasRow) container.insertBefore(createRowDivider(), child);
    prevWasRow = true;
  });
}

function createRowDivider() {
  const divider = document.createElement("li");
  divider.className = "row-divider";
  return divider;
}

/* 순위 카드(행) 생성 — rank가 null이면 "아직 순위 없음" 행으로, 드래그 대상에서 제외됨 */
function buildRankingRow(book, rank, disableDrag) {
  const li = document.createElement("li");
  li.className = rank == null ? "ranking-row unranked" : "ranking-row";
  li.dataset.contentId = book.contentId;

  const tags = (book.keywords ?? "")
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean)
    .map((t) => `#${t}`)
    .join(" ");

  const diffClass = DIFFICULTY_BADGE_CLASS[book.difficulty] ?? "diff-mid";

  li.innerHTML = `
    <span class="drag-handle">${rank == null ? "" : '<i class="fa-solid fa-grip-vertical"></i>'}</span>
    <span class="rank-number">${rank ?? ""}</span>
    <img class="rank-thumb" src="${book.imageUrl || "/images/book-sample.png"}" alt="">
    <span class="rank-title">${book.originalTitle ?? ""}</span>
    <span class="rank-type">${book.contentTypeName ?? ""}</span>
    <span class="rank-tags">${tags}</span>
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
 *
 * 여기서는 "집어서 살짝 띄우기" 느낌을 주기 위해, 잡은 행은 position:fixed로 커서를 따라 세로로만 움직이고
 * (document.body로 옮겨 목록 흐름에서 완전히 분리), 원래 자리엔 빈 자리(placeholder)를 남겨 어디에 놓일지 보여준다.
 */
function bindDragEvents(row) {
  const handle = row.querySelector(".drag-handle");
  if (!handle) return;

  handle.addEventListener("pointerdown", (event) => {
    event.preventDefault();

    const listEl = document.getElementById("rankingList");
    const rect = row.getBoundingClientRect();
    const offsetY = event.clientY - rect.top;

    // 원래 자리에 빈 칸(placeholder)을 남기고, 실제 행은 목록 흐름에서 빼내어 커서를 따라 띄운다
    const placeholder = document.createElement("li");
    placeholder.className = "ranking-row-placeholder";
    placeholder.style.height = `${rect.height}px`;
    row.before(placeholder);

    row.classList.add("dragging");
    row.style.position = "fixed";
    row.style.top = `${rect.top}px`;
    row.style.left = `${rect.left}px`;
    row.style.width = `${rect.width}px`;
    document.body.appendChild(row);

    handle.setPointerCapture(event.pointerId);

    let lastAfterElement; // 직전에 계산한 위치와 같으면 재배치를 건너뛰어 불필요한 애니메이션을 막는다

    const onPointerMove = (moveEvent) => {
      row.style.top = `${moveEvent.clientY - offsetY}px`;

      const afterElement = getDragAfterElement(listEl, moveEvent.clientY);
      if (afterElement === lastAfterElement) return;
      lastAfterElement = afterElement;

      const divider = listEl.querySelector(".ranking-list-divider");
      animateReorder(listEl, () => {
        if (afterElement == null) {
          if (divider) listEl.insertBefore(placeholder, divider);
          else listEl.appendChild(placeholder);
        } else if (afterElement !== placeholder) {
          listEl.insertBefore(placeholder, afterElement);
        }
        refreshRowDividers(listEl);
      });
      renumberVisibleRows();
    };

    const onPointerUp = () => {
      placeholder.replaceWith(row);
      row.classList.remove("dragging");
      row.style.position = "";
      row.style.top = "";
      row.style.left = "";
      row.style.width = "";

      syncOrderFromDom();
      refreshRowDividers(listEl);
      renumberVisibleRows();
      handle.releasePointerCapture(event.pointerId);
      document.removeEventListener("pointermove", onPointerMove);
      document.removeEventListener("pointerup", onPointerUp);
    };

    document.addEventListener("pointermove", onPointerMove);
    document.addEventListener("pointerup", onPointerUp);
  });
}

/*
 * FLIP 기법으로 재배치를 부드럽게 애니메이션한다.
 * mutate() 실행 전/후의 각 행 위치를 비교해서, 실제로는 이미 최종 위치에 두고
 * transform으로 이전 위치만큼 밀어둔 뒤 0으로 되돌리며 밀려나는 것처럼 보이게 한다.
 */
function animateReorder(container, mutate) {
  const rows = [...container.querySelectorAll(".ranking-row")];
  const firstRects = new Map(rows.map((el) => [el, el.getBoundingClientRect()]));

  mutate();

  rows.forEach((el) => {
    const first = firstRects.get(el);
    const last = el.getBoundingClientRect();
    const deltaY = first.top - last.top;
    if (!deltaY) return;

    el.style.transition = "none";
    el.style.transform = `translateY(${deltaY}px)`;

    requestAnimationFrame(() => {
      el.style.transition = "transform 180ms ease";
      el.style.transform = "";
    });
  });
}

/* 커서(y좌표) 기준으로, 빈 칸(placeholder)이 들어갈 위치의 "다음 행"을 찾는다 (순위 없는 도서·구분선은 대상에서 제외) */
function getDragAfterElement(container, y) {
  const rows = [...container.querySelectorAll(".ranking-row:not(.dragging):not(.unranked)")];

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

/* 드래그 중 실시간으로 순번 뱃지 갱신 — 구분선을 만나면 중단 (순위 없는 도서는 번호 매기지 않음) */
function renumberVisibleRows() {
  const listEl = document.getElementById("rankingList");
  if (!listEl) return;

  let rank = 0;
  for (const child of listEl.children) {
    if (child.classList.contains("ranking-list-divider") || child.classList.contains("unranked")) break;
    if (child.classList.contains("row-divider")) continue;
    rank++;
    if (child.classList.contains("ranking-row-placeholder")) continue;
    const numberEl = child.querySelector(".rank-number");
    if (numberEl) numberEl.textContent = rank;
  }
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

/* 엑셀 다운로드(올해 적용 중인 순위, 초1~중등 7개 시트) / 순위 저장 / 예약(초안) 목록 보기 */
function initActionButtons() {
  document.getElementById("btnExcelDownload")?.addEventListener("click", () => {
    window.location.href = `/priority/excel?year=${CURRENT_YEAR}`;
  });

  document.getElementById("btnSaveRanking")?.addEventListener("click", saveRankingDraft);

  document.getElementById("btnReservationList")?.addEventListener("click", openDraftModal);
}

/* 현재 화면 순서를 새 초안으로 저장 (해당 연도+학년 첫 저장이면 자동으로 적용 상태가 됨) */
async function saveRankingDraft() {
  if (!gradeBooks.length) {
    alert("저장할 도서가 없습니다.");
    return;
  }

  const applyNow = await customChoice("이 순위를 언제 적용하시겠습니까?", [
    { label: "이번년도에 바로 적용", value: true, primary: true },
    { label: "내년에 적용되도록 등록", value: false },
  ]);
  if (applyNow === null) return; // 취소

  try {
    await postJson("/priority/draft", {
      year: applyNow ? CURRENT_YEAR : activeYear,
      schoolYear: activeGrade,
      contentIds: gradeBooks.map((b) => b.contentId),
      applyNow,
    });

    // 방금 저장한 순서를 "확정 상태"로 스냅샷 갱신 (뒤로가기/탭전환 경고 해제)
    originalOrder = gradeBooks.map((b) => String(b.contentId));

    if (applyNow) {
      // 화면은 평소 "다음해" 데이터만 보여주므로, 즉시 적용 직후엔 올해 데이터로 바꿔서 실제로 반영된 것을 바로 보여준다
      // 새로고침해도 이 화면이 유지되도록 학년별로 세션에 기억해둔다
      viewYear = CURRENT_YEAR;
      setStoredViewYear(activeGrade, CURRENT_YEAR);
      await loadBooks();
      alert("순위가 저장되어 바로 적용되었습니다.");
    } else {
      alert("순위 초안이 저장되었습니다. 여러 초안 중 적용할 것은 '예약 목록 보기'에서 선택할 수 있습니다.");
    }
  } catch (error) {
    console.error(error);
    alert(error.message ?? "순위 저장 중 오류가 발생했습니다.");
  }
}

/* ===================== 순위 초안(예약) 목록 모달 ===================== */

function initDraftModal() {
  const modal = document.getElementById("draftModal");

  document.getElementById("btnCloseDraftModal")?.addEventListener("click", () => {
    if (modal) modal.hidden = true;
  });

  modal?.addEventListener("click", (event) => {
    if (event.target === modal) modal.hidden = true;
  });
}

async function openDraftModal() {
  const modal = document.getElementById("draftModal");
  if (modal) modal.hidden = false;

  // 예약 목록은 "내년 등록" 전용 화면이므로 즉시 적용 여부(viewYear)와 무관하게 항상 다음해(activeYear) 기준으로 보여준다
  const gradeName = SCHOOLYEAR_CODES.find((g) => g.code === activeGrade)?.name ?? "";
  const titleEl = document.getElementById("draftModalTitle");
  if (titleEl) titleEl.textContent = `저장된 순위 초안 · ${gradeName} · ${activeYear}년`;

  await loadDrafts();
}

/* "2026-07-07T09:12:34" -> "2026년 07월 07일 09시12분" */
function formatDraftDate(isoString) {
  if (!isoString) return "";
  const [datePart, timePart] = String(isoString).split("T");
  const [y, m, d] = (datePart ?? "").split("-");
  const [hh, mm] = (timePart ?? "00:00").split(":");
  if (!y || !m || !d) return String(isoString);
  return `${y}년 ${m}월 ${d}일 ${hh}시${mm}분`;
}

async function loadDrafts() {
  const listEl = document.getElementById("draftList");
  listEl.innerHTML = '<li class="draft-empty">불러오는 중...</li>';

  try {
    const response = await fetch(`/priority/draft/list?year=${activeYear}&schoolYear=${activeGrade}`);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "초안 목록 조회에 실패했습니다.");

    renderDrafts(data.response ?? []);
  } catch (error) {
    console.error(error);
    listEl.innerHTML = '<li class="draft-empty">조회 중 오류가 발생했습니다.</li>';
  }
}

function renderDrafts(drafts) {
  const listEl = document.getElementById("draftList");
  listEl.innerHTML = "";

  if (!drafts.length) {
    listEl.innerHTML = '<li class="draft-empty">저장된 순위 초안이 없습니다.</li>';
    return;
  }

  drafts.forEach((draft) => {
    const wrap = document.createElement("li");
    wrap.className = "draft-item-wrap";

    const row = document.createElement("div");
    row.className = "draft-item";

    const toggleBtn = document.createElement("button");
    toggleBtn.type = "button";
    toggleBtn.className = "draft-toggle";
    toggleBtn.setAttribute("aria-label", "순위 미리보기");
    toggleBtn.innerHTML = '<i class="fa-solid fa-chevron-down"></i>';

    const info = document.createElement("div");
    info.className = "draft-item-info";

    const date = document.createElement("span");
    date.className = "draft-item-date";
    date.textContent = formatDraftDate(draft.createdAt);

    const meta = document.createElement("span");
    meta.className = "draft-item-meta";
    meta.textContent = draft.createdBy ? `${draft.createdBy} 저장` : "";

    info.append(date, meta);

    let actionEl;
    if (draft.isActive === "Y") {
      actionEl = document.createElement("span");
      actionEl.className = "draft-active-badge";
      actionEl.textContent = "적용중";
    } else {
      actionEl = document.createElement("button");
      actionEl.type = "button";
      actionEl.className = "btn primary small";
      actionEl.textContent = "이 순위로 적용";
      actionEl.addEventListener("click", () => activateDraft(draft.draftId));
    }

    const deleteBtn = document.createElement("button");
    deleteBtn.type = "button";
    deleteBtn.className = "draft-delete";
    deleteBtn.setAttribute("aria-label", "초안 삭제");
    deleteBtn.innerHTML = '<i class="fa-solid fa-trash"></i>';
    deleteBtn.addEventListener("click", () => deleteDraft(draft.draftId, draft.isActive));

    row.append(toggleBtn, info, actionEl, deleteBtn);

    const preview = document.createElement("div");
    preview.className = "draft-preview";
    preview.hidden = true;

    toggleBtn.addEventListener("click", async () => {
      const expanding = preview.hidden;
      preview.hidden = !expanding;
      toggleBtn.classList.toggle("expanded", expanding);
      if (expanding && !preview.dataset.loaded) {
        preview.dataset.loaded = "1";
        await loadDraftPreview(draft.draftId, preview);
      }
    });

    wrap.append(row, preview);
    listEl.appendChild(wrap);
  });
}

/* 초안 순위 미리보기 (제목만 순서대로) */
async function loadDraftPreview(draftId, container) {
  container.innerHTML = '<p class="draft-preview-empty">불러오는 중...</p>';

  try {
    const response = await fetch(`/priority/draft/${draftId}/books`);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "미리보기 조회에 실패했습니다.");

    const books = data.response ?? [];
    if (!books.length) {
      container.innerHTML = '<p class="draft-preview-empty">저장된 도서가 없습니다.</p>';
      return;
    }

    const ol = document.createElement("ol");
    ol.className = "draft-preview-list";
    books.forEach((book) => {
      const li = document.createElement("li");
      li.textContent = book.originalTitle ?? "";
      ol.appendChild(li);
    });

    container.innerHTML = "";
    container.appendChild(ol);
  } catch (error) {
    console.error(error);
    delete container.dataset.loaded;
    container.innerHTML = '<p class="draft-preview-empty">조회 중 오류가 발생했습니다.</p>';
  }
}

async function activateDraft(draftId) {
  const ok = await customConfirm("이 순위를 적용하시겠습니까? 현재 적용 중인 순위는 해제됩니다.");
  if (!ok) return;

  try {
    await postJson("/priority/draft/activate", { draftId });
    alert("적용되었습니다.");
    document.getElementById("draftModal").hidden = true;
    await loadBooks();
  } catch (error) {
    console.error(error);
    alert(error.message ?? "적용 중 오류가 발생했습니다.");
  }
}

/* 초안 삭제 — _del로 이관만 하고 복구는 지원하지 않으므로 삭제 전 한 번 더 확인 */
async function deleteDraft(draftId, isActive) {
  const message = isActive === "Y"
    ? "이 순위는 현재 적용 중입니다.\n삭제하면 적용된 순위가 없어집니다.\n그래도 삭제하시겠습니까?\n(삭제 후 복구할 수 없습니다)"
    : "이 순위 초안을 삭제하시겠습니까?\n삭제 후에는 복구할 수 없습니다.";
  const ok = await customConfirm(message);
  if (!ok) return;

  try {
    await postJson("/priority/draft/delete", { draftId });
    await loadDrafts();
    if (isActive === "Y") await loadBooks();
  } catch (error) {
    console.error(error);
    alert(error.message ?? "삭제 중 오류가 발생했습니다.");
  }
}
