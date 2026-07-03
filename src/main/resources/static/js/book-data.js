document.addEventListener("DOMContentLoaded", async () => {
  initToolbarFilters();
  initContentTypeChips();
  initBookListSelection();
  initNewBookButton();
  initSaveBookButton();
  initDeleteSelectedButton();
  initToolbarSearch();
  initListSearch();
  initDetailFilter();
  initDeletedBooks();
  initImageButtons();
  initStatusToggle();
  initCategoryTagInput();
  initQuestionTabs();
  initQuestionActions();
  initGuideLogout();

  loadGradeOptions();
  await loadCurrentUser();
  await loadBookList();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TOKEN = "hohoedu-master-csrf-token";
const DEFAULT_BOOK_IMAGE = "/images/book-sample.png";

// 본사 센터 코드 — 이 센터만 마스터 도서(content) 편집 가능, 그 외는 실물도서(item) 등록
const HQ_CENTER_CODE = "PUS001";
let currentUser = null;
let isHq = true; // 사용자 조회 실패 시 기본 본사

/*
 * /code/list/{groupCode}는 존재하지 않는 테이블(erp_code)을 조회하는 별개 버그가 있어 당장은 못 씀.
 * 시드 데이터(data.sql) 기준 코드값을 하드코딩한다.
 */
const SCHOOLYEAR_CODES = [
  { code: "01", name: "초1" },
  { code: "02", name: "초2" },
  { code: "03", name: "초3" },
  { code: "04", name: "초4" },
  { code: "05", name: "초5" },
  { code: "06", name: "초6" },
];

const CONTENTTYPE_CODES = [
  { code: "01", name: "수록" },
  { code: "02", name: "연계" },
  { code: "03", name: "필독" },
  { code: "04", name: "추천" },
];

const GENRE_CODES = [
  { code: "01", name: "창작" }, { code: "02", name: "명작" }, { code: "03", name: "전래" },
  { code: "04", name: "고전" }, { code: "05", name: "외국창작" }, { code: "06", name: "환경" },
  { code: "07", name: "신체동화" }, { code: "08", name: "과학동화" }, { code: "09", name: "인물" },
  { code: "10", name: "동시지" }, { code: "11", name: "역사" }, { code: "12", name: "비문학(자연)" },
  { code: "13", name: "비문학(예절)" }, { code: "14", name: "비문학(문화)" }, { code: "15", name: "비문학(경제)" },
  { code: "16", name: "비문학(인문)" }, { code: "17", name: "비문학(과학)" }, { code: "18", name: "상식" },
  { code: "19", name: "과학" }, { code: "20", name: "비문학(미술)" }, { code: "21", name: "비문학(철학)" },
  { code: "22", name: "비문학(안전)" }, { code: "23", name: "비문학(국기)" }, { code: "24", name: "비문학(전통)" },
  { code: "25", name: "비문학(환경)" }, { code: "26", name: "비문학" }, { code: "27", name: "만화" },
  { code: "28", name: "한국단편" }, { code: "29", name: "한국소설" }, { code: "30", name: "교양" },
  { code: "31", name: "국어" }, { code: "32", name: "사회" }, { code: "33", name: "시사" },
];

const QTYPE_CODES = [
  { code: "01", name: "이해" },
  { code: "02", name: "표현" },
  { code: "03", name: "논리" },
  { code: "04", name: "사고" },
  { code: "05", name: "감정" },
  { code: "06", name: "어휘" },
  { code: "07", name: "지식" },
];

const GRADE_COLORS = ["orange", "yellow", "green"];

let bookListCache = [];
let currentMode = "edit"; // "edit" | "new"
let currentContentId = null;
let currentImageUrl = "";
let selectedContentType = "";
let originalSnapshot = null;
let categoryTags = [];

let questionCache = [];
let activeLevel = "01"; // 01: 기본, 02: 심화
let questionUid = 0;
let questionObserver = null;

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

/* ===================== 사용자 권한(본사/비본사) ===================== */

/* 로그인 사용자 조회 → 본사 여부 판별 */
async function loadCurrentUser() {
  try {
    const response = await fetch("/api/user/me");
    const data = await response.json();
    if (data.success) {
      currentUser = data.response;
      isHq = currentUser.centerCode === HQ_CENTER_CODE;
    }
  } catch (error) {
    console.error(error);
  }
  applyRoleUi();
}

/* 권한별 UI 적용 */
function applyRoleUi() {
  const newBtn = document.getElementById("btnNewBook");

  if (isHq) {
    if (newBtn) newBtn.textContent = "+ 신규 도서 등록";
    return;
  }

  // 비본사: 마스터 도서 정보 read-only, 신규 등록 = 우리 센터 실물도서 등록
  if (newBtn) newBtn.textContent = "+ 우리 센터 도서 등록";
  ["btnSaveBook", "btnDeleteSelected", "btnAddQuestion", "btnSaveQuestions"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.hidden = true;
  });
  setBookFormReadonly(true);
}

/* 마스터 도서 정보 폼 읽기전용 처리 (실물도서 등록 폼 입력칸은 제외) */
const MASTER_FIELD_IDS = [
  "bookInfoTitleInput", "bookInfoAuthorInput", "bookInfoPublisherInput",
  "bookInfoGradeSelect", "bookInfoReadingTime", "bookInfoDifficulty",
  "bookInfoSummary", "categoryInput", "bookImageInput",
  "btnImageChange", "btnImageDelete",
];

function setBookFormReadonly(readonly) {
  MASTER_FIELD_IDS.forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.disabled = readonly;
  });
  document.querySelectorAll("#contentTypeChips .chip").forEach((c) => (c.disabled = readonly));
  document.querySelectorAll(".status-toggle input").forEach((r) => (r.disabled = readonly));
}

/* 문제 카드 읽기전용 처리 (비본사) */
function setQuestionsReadonly() {
  document
    .querySelectorAll(".question-editor input, .question-editor select, .question-editor textarea")
    .forEach((el) => (el.disabled = true));
  document.querySelectorAll(".btn-delete-question").forEach((b) => (b.hidden = true));
  if (window.jQuery) {
    document.querySelectorAll(".question-editor .q-qex").forEach((el) => {
      if (window.jQuery(el).next(".note-editor").length) window.jQuery(el).summernote("disable");
    });
  }
}

/* 사용 가이드 버튼 → (임시) 로그아웃 */
function initGuideLogout() {
  const button = document.querySelector(".guide-btn");
  if (!button) return;

  button.addEventListener("click", async () => {
    try {
      await fetch("/logout", { method: "POST", headers: { [CSRF_HEADER]: CSRF_TOKEN } });
    } catch (error) {
      console.error(error);
    }
    window.location.href = "/login";
  });
}

/* ===================== 비본사: 우리 센터 실물도서 ===================== */

// 우리 센터 실물도서 캐시 (contentId → item[])
let branchItemsByContent = {};

/* 우리 센터 실물도서 전체 로드 후 contentId별로 그룹핑 */
async function loadBranchCenterItems() {
  branchItemsByContent = {};

  if (isHq || !currentUser?.centerCode) return;

  try {
    const response = await fetch(`/book/item/center/${encodeURIComponent(currentUser.centerCode)}`);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "실물도서 조회에 실패했습니다.");

    (data.response ?? []).forEach((it) => {
      (branchItemsByContent[it.contentId] ??= []).push(it);
    });
  } catch (error) {
    console.error(error);
  }
}

/* 선택된 마스터의 우리 센터 실물도서 패널 렌더링 (캐시 기준) */
function renderBranchPanel() {
  const panel = document.getElementById("branchItemPanel");
  const list = document.getElementById("branchItemList");
  if (!panel || !list) return;

  list.innerHTML = "";

  if (isHq || currentContentId == null || !currentUser?.centerCode) {
    panel.hidden = true;
    return;
  }

  panel.hidden = false;

  const items = branchItemsByContent[currentContentId] ?? [];
  if (!items.length) {
    list.innerHTML = '<p class="empty-hint">우리 센터에 등록된 실물도서가 없습니다.</p>';
  } else {
    items.forEach((it) => list.appendChild(buildItemCard(it, null)));
  }
}

/* CRUD 후 센터 실물도서 재조회 → 패널 + 목록 갱신 */
async function refreshBranchItems() {
  await loadBranchCenterItems();
  renderBranchPanel();
  renderBookList(bookListCache);
}

/* 새 실물도서 입력 카드 추가 (마스터 값으로 기본 채움) */
function addBranchItemForm() {
  if (currentContentId == null) {
    alert("먼저 등록할 마스터 도서를 목록에서 선택해주세요.");
    return;
  }
  const list = document.getElementById("branchItemList");
  if (!list) return;

  list.querySelector(".empty-hint")?.remove();

  const master = bookListCache.find((b) => b.contentId === currentContentId);
  const card = buildItemCard(null, master);
  list.appendChild(card);
  card.scrollIntoView({ behavior: "smooth", block: "start" });
}

/* 실물도서 카드 (기존 item=수정/삭제, 신규=취소/등록) */
function buildItemCard(item, master) {
  const isExisting = !!item;
  const card = document.createElement("div");
  card.className = "item-card";
  card.dataset.bcode = isExisting ? item.bcode : "";
  card.dataset.imageUrl = (isExisting ? item.imageUrl : master?.imageUrl) ?? "";

  const imgSrc = card.dataset.imageUrl || DEFAULT_BOOK_IMAGE;

  card.innerHTML = `
    <div class="form-row"><label>도서명</label><input type="text" class="it-title"></div>
    <div class="form-row image-row">
      <label>도서 이미지</label>
      <div class="image-area">
        <img class="it-image" src="${imgSrc}" alt="">
        <div class="image-actions">
          <input type="file" class="it-image-input" accept="image/*" hidden>
          <button type="button" class="btn outline small it-image-change">이미지 변경</button>
          <button type="button" class="btn danger-outline small it-image-delete">이미지 삭제</button>
        </div>
      </div>
    </div>
    <div class="form-row"><label>저자</label><input type="text" class="it-author"></div>
    <div class="form-row"><label>출판사</label><input type="text" class="it-publisher"></div>
    <div class="item-form-actions">
      ${isExisting
        ? '<button type="button" class="btn primary small it-update">수정</button><button type="button" class="btn danger-outline small it-delete">삭제</button>'
        : '<button type="button" class="btn outline small it-cancel">취소</button><button type="button" class="btn primary small it-register">등록</button>'}
    </div>
  `;

  card.querySelector(".it-title").value = (isExisting ? item.bookTitle : master?.originalTitle) ?? "";
  card.querySelector(".it-author").value = (isExisting ? item.author : master?.author) ?? "";
  card.querySelector(".it-publisher").value = (isExisting ? item.publisher : master?.publisher) ?? "";

  // 이미지 업로드/삭제
  const fileInput = card.querySelector(".it-image-input");
  card.querySelector(".it-image-change").addEventListener("click", () => fileInput.click());
  card.querySelector(".it-image-delete").addEventListener("click", () => setCardImage(card, ""));
  fileInput.addEventListener("change", async () => {
    const file = fileInput.files?.[0];
    if (!file) return;
    try {
      const form = new FormData();
      form.append("file", file);
      const res = await fetch("/book/image", { method: "POST", headers: { [CSRF_HEADER]: CSRF_TOKEN }, body: form });
      const data = await res.json();
      if (!data.success) throw new Error(data.error?.message ?? "이미지 업로드에 실패했습니다.");
      setCardImage(card, data.response.url);
    } catch (error) {
      console.error(error);
      alert(error.message ?? "이미지 업로드 중 오류가 발생했습니다.");
    } finally {
      fileInput.value = "";
    }
  });

  // 액션 버튼
  if (isExisting) {
    card.querySelector(".it-update").addEventListener("click", () => updateBranchItem(card));
    card.querySelector(".it-delete").addEventListener("click", () => deleteBranchItem(card));
  } else {
    card.querySelector(".it-cancel").addEventListener("click", () => card.remove());
    card.querySelector(".it-register").addEventListener("click", () => registerBranchItem(card));
  }

  return card;
}

function setCardImage(card, url) {
  card.dataset.imageUrl = url || "";
  const img = card.querySelector(".it-image");
  if (img) img.src = card.dataset.imageUrl || DEFAULT_BOOK_IMAGE;
}

function readItemCard(card) {
  return {
    bookTitle: card.querySelector(".it-title").value.trim(),
    author: card.querySelector(".it-author").value.trim(),
    publisher: card.querySelector(".it-publisher").value.trim(),
    imageUrl: card.dataset.imageUrl || "",
  };
}

/* 신규 실물도서 등록 (bcode는 서버에서 숫자 UUID 자동 생성) */
async function registerBranchItem(card) {
  const v = readItemCard(card);
  if (!v.bookTitle) {
    alert("도서명을 입력해주세요.");
    return;
  }
  try {
    await postJson("/book/item/register", {
      contentId: currentContentId,
      bookTitle: v.bookTitle,
      author: v.author,
      publisher: v.publisher,
      imageUrl: v.imageUrl,
      centerCode: currentUser?.centerCode,
      quantity: 1,
      state: "Y",
    });
    alert("등록되었습니다.");
    await refreshBranchItems();
  } catch (error) {
    console.error(error);
    alert(error.message ?? "등록 중 오류가 발생했습니다.");
  }
}

/* 기존 실물도서 수정 */
async function updateBranchItem(card) {
  const v = readItemCard(card);
  if (!v.bookTitle) {
    alert("도서명을 입력해주세요.");
    return;
  }
  try {
    await postJson("/book/item/update", {
      bcode: card.dataset.bcode,
      bookTitle: v.bookTitle,
      author: v.author,
      publisher: v.publisher,
      imageUrl: v.imageUrl,
    });
    alert("수정되었습니다.");
    await refreshBranchItems();
  } catch (error) {
    console.error(error);
    alert(error.message ?? "수정 중 오류가 발생했습니다.");
  }
}

/* 기존 실물도서 삭제 */
async function deleteBranchItem(card) {
  if (!confirm("우리 센터 보유에서 삭제하시겠습니까?")) return;
  try {
    // item_del로 이관 후 우리 센터 매핑만 해제 (실물 레코드는 유지 → 삭제 도서함에서 복구 가능)
    await postJson("/book/item/delete", { bcode: card.dataset.bcode, centerCode: currentUser?.centerCode });
    alert("삭제되었습니다.");
    await refreshBranchItems();
  } catch (error) {
    console.error(error);
    alert(error.message ?? "삭제 중 오류가 발생했습니다.");
  }
}

/* ===================== 툴바 필터 / 검색 ===================== */

function initToolbarFilters() {
  const grade = document.getElementById("filterGrade");
  const contentType = document.getElementById("filterContentType");

  if (grade) SCHOOLYEAR_CODES.forEach(({ code, name }) => grade.add(new Option(name, code)));
  if (contentType) CONTENTTYPE_CODES.forEach(({ code, name }) => contentType.add(new Option(name, code)));
}

/* 현재 필터 값 (툴바 + 상세 필터, 서버 조회 파라미터) */
function currentFilters() {
  return {
    schoolYear: document.getElementById("filterGrade")?.value ?? "",
    contentType: document.getElementById("filterContentType")?.value ?? "",
    state: document.getElementById("filterState")?.value ?? "",
    title: document.getElementById("toolbarSearch")?.value.trim() ?? "",
    author: document.getElementById("filterAuthor")?.value.trim() ?? "",
    genre: document.getElementById("filterGenre")?.value ?? "",
    keyword: document.getElementById("filterKeyword")?.value.trim() ?? "",
  };
}

function initToolbarSearch() {
  const button = document.getElementById("btnSearch");
  const input = document.getElementById("toolbarSearch");

  button?.addEventListener("click", () => loadBookList(currentFilters()));
  input?.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      loadBookList(currentFilters());
    }
  });
}

/* 목록 검색(클라이언트 필터) + 상세 필터 버튼 */
function initListSearch() {
  const input = document.getElementById("listSearch");

  input?.addEventListener("input", () => {
    const term = input.value.trim().toLowerCase();
    const list = !term
      ? bookListCache
      : bookListCache.filter((b) =>
          (b.originalTitle ?? "").toLowerCase().includes(term) ||
          (b.author ?? "").toLowerCase().includes(term) ||
          (b.keywords ?? "").toLowerCase().includes(term));
    renderBookList(list);
  });

}

/* 상세 필터 (저자 / 장르 / 키워드) */
function initDetailFilter() {
  const panel = document.getElementById("detailFilterPanel");
  const genre = document.getElementById("filterGenre");

  if (genre) GENRE_CODES.forEach(({ code, name }) => genre.add(new Option(name, code)));

  document.getElementById("btnDetailFilter")?.addEventListener("click", () => {
    if (panel) panel.hidden = !panel.hidden;
  });

  document.getElementById("btnDetailApply")?.addEventListener("click", () => {
    loadBookList(currentFilters());
  });

  document.getElementById("btnDetailReset")?.addEventListener("click", () => {
    ["filterAuthor", "filterKeyword"].forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.value = "";
    });
    if (genre) genre.value = "";
    loadBookList(currentFilters());
  });
}

/* 삭제 도서함 (조회 + 복구) */
function initDeletedBooks() {
  const modal = document.getElementById("deletedModal");

  document.getElementById("btnDeletedBooks")?.addEventListener("click", () => {
    if (modal) modal.hidden = false;
    loadDeletedBooks();
  });

  document.getElementById("btnCloseDeleted")?.addEventListener("click", () => {
    if (modal) modal.hidden = true;
  });

  modal?.addEventListener("click", (event) => {
    if (event.target === modal) modal.hidden = true;
  });
}

async function loadDeletedBooks() {
  const listEl = document.getElementById("deletedList");

  if (!listEl) return;

  listEl.innerHTML = '<li class="deleted-empty">불러오는 중...</li>';

  // 본사=삭제된 마스터 도서(content_del), 비본사=삭제된 실물도서(item_del)
  const url = isHq ? "/book/deleted" : "/book/item/deleted";

  try {
    const response = await fetch(url);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "삭제 도서 조회에 실패했습니다.");

    let list = data.response ?? [];
    // 비본사는 우리 센터에서 삭제한 실물도서만 표시
    if (!isHq) list = list.filter((it) => it.centerCode === currentUser?.centerCode);

    renderDeletedBooks(list);
  } catch (error) {
    console.error(error);
    listEl.innerHTML = '<li class="deleted-empty">조회 중 오류가 발생했습니다.</li>';
  }
}

function renderDeletedBooks(books) {
  const listEl = document.getElementById("deletedList");

  if (!listEl) return;

  listEl.innerHTML = "";

  if (!books.length) {
    listEl.innerHTML = '<li class="deleted-empty">삭제된 도서가 없습니다.</li>';
    return;
  }

  books.forEach((book) => {
    const li = document.createElement("li");
    li.className = "deleted-item";

    const info = document.createElement("div");
    const title = document.createElement("strong");
    title.textContent = book.originalTitle ?? book.bookTitle ?? "";
    const meta = document.createElement("p");
    const deletedAt = book.deletedAt ? String(book.deletedAt).replace("T", " ").slice(0, 16) : "";
    meta.textContent = [book.author, `삭제: ${deletedAt}`, book.deletedBy].filter(Boolean).join(" · ");
    info.append(title, meta);

    const restoreBtn = document.createElement("button");
    restoreBtn.type = "button";
    restoreBtn.className = "btn primary small";
    restoreBtn.textContent = "복구";
    restoreBtn.addEventListener("click", () => restoreBook(book.delId));

    li.append(info, restoreBtn);
    listEl.appendChild(li);
  });
}

async function restoreBook(delId) {
  const message = isHq
    ? "이 도서를 복구하시겠습니까?\n연결된 실물도서·문제도 함께 복구됩니다."
    : "이 실물도서를 복구하시겠습니까?";
  if (!confirm(message)) return;

  const url = isHq ? "/book/restore" : "/book/item/restore";

  try {
    await postJson(url, { delId });
    alert("복구되었습니다.");
    await loadDeletedBooks();
    if (isHq) {
      await loadBookList(currentFilters());
    } else {
      await refreshBranchItems();
    }
  } catch (error) {
    console.error(error);
    alert(error.message ?? "복구 중 오류가 발생했습니다.");
  }
}

/* ===================== 도서 목록 ===================== */

/* 도서 목록 조회 (필터 파라미터 지원) */
async function loadBookList(params = {}) {
  const listEl = document.querySelector(".book-list");

  if (!listEl) return;

  try {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value) qs.append(key, value);
    });

    const url = "/book/search" + (qs.toString() ? `?${qs}` : "");
    const response = await fetch(url);
    const data = await response.json();

    if (!data.success) throw new Error(data.error?.message ?? "도서 목록 조회에 실패했습니다.");

    bookListCache = data.response ?? [];

    // 비본사면 목록 들여쓰기용으로 우리 센터 실물도서 캐시 로드
    await loadBranchCenterItems();

    if (bookListCache.length) {
      renderBookInfo(bookListCache[0]);
      renderBookList(bookListCache);
    } else {
      clearBookInfo(false);
      renderBookList(bookListCache);
    }
  } catch (error) {
    console.error(error);
    alert(error.message ?? "도서 목록 조회 중 오류가 발생했습니다.");
  }
}

function renderBookList(books) {
  const listEl = document.querySelector(".book-list");

  if (!listEl) return;

  listEl.innerHTML = "";

  books.forEach((book, index) => {
    const li = document.createElement("li");
    li.className = "book-item" + (book.contentId === currentContentId ? " active" : "");
    li.dataset.contentId = book.contentId;

    const img = document.createElement("img");
    img.src = book.imageUrl || DEFAULT_BOOK_IMAGE;
    img.alt = "";

    const info = document.createElement("div");

    const title = document.createElement("strong");
    title.textContent = book.originalTitle ?? "";

    const genre = document.createElement("p");
    genre.textContent = [book.contentTypeName, book.genreName].filter(Boolean).join(", ");

    info.append(title, genre);

    const grade = document.createElement("span");
    grade.className = "grade " + GRADE_COLORS[index % GRADE_COLORS.length];
    grade.textContent = book.schoolyearName ?? "";

    li.append(img, info, grade);
    listEl.appendChild(li);

    // 비본사: 마스터 하위로 우리 센터 실물도서 들여쓰기 표시
    if (!isHq) {
      (branchItemsByContent[book.contentId] ?? []).forEach((it) => {
        const sub = document.createElement("li");
        sub.className = "book-subitem";
        sub.dataset.contentId = book.contentId;

        const subImg = document.createElement("img");
        subImg.src = it.imageUrl || DEFAULT_BOOK_IMAGE;
        subImg.alt = "";

        const subInfo = document.createElement("div");
        const subTitle = document.createElement("strong");
        subTitle.textContent = it.bookTitle ?? "";
        const subMeta = document.createElement("p");
        subMeta.textContent = [it.author, it.publisher].filter(Boolean).join(" · ");
        subInfo.append(subTitle, subMeta);

        sub.append(subImg, subInfo);
        listEl.appendChild(sub);
      });
    }
  });
}

/* 도서 목록 클릭 → 선택 + 도서 정보 렌더링 (체크박스 클릭은 제외) */
function initBookListSelection() {
  const listEl = document.querySelector(".book-list");

  if (!listEl) return;

  listEl.addEventListener("click", (event) => {
    // 마스터 항목 또는 하위 실물도서 항목 클릭 → 해당 마스터 선택
    const target = event.target.closest(".book-item, .book-subitem");
    if (!target) return;

    const book = bookListCache.find((b) => String(b.contentId) === target.dataset.contentId);
    if (!book) return;

    listEl.querySelectorAll(".book-item").forEach((item) => item.classList.remove("active"));
    listEl.querySelector(`.book-item[data-content-id="${book.contentId}"]`)?.classList.add("active");

    renderBookInfo(book);
  });
}

/* 선택(현재 활성) 도서 삭제 */
function initDeleteSelectedButton() {
  const button = document.getElementById("btnDeleteSelected");

  if (!button) return;

  button.addEventListener("click", async () => {
    if (currentMode === "new" || currentContentId == null) {
      alert("삭제할 도서를 선택해주세요.");
      return;
    }

    const title = document.getElementById("bookInfoTitleInput")?.value.trim() || "선택한 도서";

    if (!confirm(`[${title}]를 삭제하시겠습니까?\n연결된 실물도서·문제도 함께 삭제됩니다.`)) return;

    try {
      await postJson("/book/delete", { contentId: currentContentId });
      alert("삭제되었습니다.");
      currentContentId = null;
      await loadBookList(currentFilters());
    } catch (error) {
      console.error(error);
      alert(error.message ?? "삭제 중 오류가 발생했습니다.");
    }
  });
}

/* ===================== 도서 정보 ===================== */

function loadGradeOptions() {
  const select = document.getElementById("bookInfoGradeSelect");

  if (!select) return;

  select.innerHTML = "";
  select.appendChild(new Option("선택", ""));
  SCHOOLYEAR_CODES.forEach(({ code, name }) => select.add(new Option(name, code)));
}

/* 분류 chip (단일 선택) */
function initContentTypeChips() {
  const wrap = document.getElementById("contentTypeChips");

  if (!wrap) return;

  wrap.innerHTML = "";

  CONTENTTYPE_CODES.forEach(({ code, name }) => {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = "chip";
    chip.dataset.code = code;
    chip.textContent = name;

    chip.addEventListener("click", () => {
      if (chip.classList.contains("active")) {
        chip.classList.remove("active");
        selectedContentType = "";
      } else {
        wrap.querySelectorAll(".chip").forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        selectedContentType = code;
      }
    });

    wrap.appendChild(chip);
  });
}

function setContentTypeChip(code) {
  selectedContentType = code ?? "";
  document
    .querySelectorAll("#contentTypeChips .chip")
    .forEach((chip) => chip.classList.toggle("active", chip.dataset.code === selectedContentType));
}

/* 사용여부 라디오 (use → Y, stop → N) */
function initStatusToggle() {
  // 라벨 클릭만으로 값이 반영되므로 별도 핸들러 불필요 (스냅샷 시 조회)
}

function setStatusRadio(state) {
  const stopRadio = document.getElementById("status-stop");
  const useRadio = document.getElementById("status-use");
  if (state === "N") {
    if (stopRadio) stopRadio.checked = true;
  } else if (useRadio) {
    useRadio.checked = true;
  }
}

function getStatusValue() {
  return document.getElementById("status-stop")?.checked ? "N" : "Y";
}

/* 도서 이미지 */
function setBookImage(url) {
  currentImageUrl = url || "";
  const img = document.getElementById("bookInfoImage");
  if (img) img.src = currentImageUrl || DEFAULT_BOOK_IMAGE;
}

function initImageButtons() {
  const input = document.getElementById("bookImageInput");
  const changeBtn = document.getElementById("btnImageChange");
  const deleteBtn = document.getElementById("btnImageDelete");

  changeBtn?.addEventListener("click", () => input?.click());
  deleteBtn?.addEventListener("click", () => setBookImage(""));

  input?.addEventListener("change", async () => {
    const file = input.files?.[0];
    if (!file) return;

    try {
      const form = new FormData();
      form.append("file", file);

      const response = await fetch("/book/image", {
        method: "POST",
        headers: { [CSRF_HEADER]: CSRF_TOKEN },
        body: form,
      });

      const data = await response.json();
      if (!data.success) throw new Error(data.error?.message ?? "이미지 업로드에 실패했습니다.");

      setBookImage(data.response.url);
    } catch (error) {
      console.error(error);
      alert(error.message ?? "이미지 업로드 중 오류가 발생했습니다.");
    } finally {
      input.value = "";
    }
  });
}

/* 도서 정보 패널 렌더링 */
function renderBookInfo(book) {
  if (!book) return;

  currentMode = "edit";
  currentContentId = book.contentId;

  setField("bookInfoTitle", `[${book.originalTitle ?? ""}]`, true);
  setValue("bookInfoTitleInput", book.originalTitle ?? "");
  setValue("bookInfoAuthorInput", book.author ?? "");
  setValue("bookInfoPublisherInput", book.publisher ?? "");
  setValue("bookInfoGradeSelect", book.schoolyear ?? "");
  setValue("bookInfoReadingTime", book.readingTime ?? "");
  setValue("bookInfoDifficulty", book.difficulty ?? "");
  setValue("bookInfoSummary", book.summary ?? "");

  setStatusRadio(book.state);
  setBookImage(book.imageUrl);
  setContentTypeChip(book.contentType ?? "");
  setCategoryTags(book.keywords ? book.keywords.split(",").map((k) => k.trim()).filter(Boolean) : []);

  originalSnapshot = getFormSnapshot();

  loadQuestions(book.contentId);
  renderBranchPanel();
}

/* 도서 정보 패널 비우기 (신규 등록 / 목록 없음) */
function clearBookInfo(isNew) {
  currentMode = isNew ? "new" : "edit";
  currentContentId = null;

  setField("bookInfoTitle", isNew ? "[신규 도서]" : "[도서 정보]", true);
  setValue("bookInfoTitleInput", "");
  setValue("bookInfoAuthorInput", "");
  setValue("bookInfoPublisherInput", "");
  setValue("bookInfoGradeSelect", "");
  setValue("bookInfoReadingTime", "");
  setValue("bookInfoDifficulty", "");
  setValue("bookInfoSummary", "");

  setStatusRadio("Y");
  setBookImage("");
  setContentTypeChip("");
  setCategoryTags([]);

  originalSnapshot = getFormSnapshot();

  questionCache = [];
  renderQuestions();
  renderBranchPanel();
}

function setField(id, text, isText) {
  const el = document.getElementById(id);
  if (el) el[isText ? "textContent" : "value"] = text;
}

function setValue(id, value) {
  const el = document.getElementById(id);
  if (el) el.value = value;
}

/* 신규 도서 등록 */
function initNewBookButton() {
  const button = document.getElementById("btnNewBook");

  if (!button) return;

  button.addEventListener("click", () => {
    // 비본사는 '신규 등록'이 우리 센터 실물도서 입력 카드 추가
    if (!isHq) {
      addBranchItemForm();
      return;
    }
    document.querySelectorAll(".book-item").forEach((item) => item.classList.remove("active"));
    clearBookInfo(true);
  });
}

/* 현재 도서 정보 스냅샷 */
function getFormSnapshot() {
  return {
    title: document.getElementById("bookInfoTitleInput")?.value.trim() ?? "",
    author: document.getElementById("bookInfoAuthorInput")?.value.trim() ?? "",
    publisher: document.getElementById("bookInfoPublisherInput")?.value.trim() ?? "",
    schoolYear: document.getElementById("bookInfoGradeSelect")?.value ?? "",
    readingTime: document.getElementById("bookInfoReadingTime")?.value ?? "",
    difficulty: document.getElementById("bookInfoDifficulty")?.value ?? "",
    summary: document.getElementById("bookInfoSummary")?.value.trim() ?? "",
    keywords: categoryTags.join(","),
    contentType: selectedContentType,
    state: getStatusValue(),
    imageUrl: currentImageUrl,
  };
}

/* 저장 버튼 - 신규 등록 / 수정 / 변경 없음 분기 */
function initSaveBookButton() {
  const button = document.getElementById("btnSaveBook");

  if (!button) return;

  button.addEventListener("click", async () => {
    const snapshot = getFormSnapshot();

    if (!snapshot.title) {
      alert("도서명을 입력해주세요.");
      return;
    }

    if (currentMode === "edit") {
      if (JSON.stringify(snapshot) === JSON.stringify(originalSnapshot)) {
        alert("변경 사항이 없습니다.");
        return;
      }
      await saveBook("update", snapshot);
    } else {
      await saveBook("register", snapshot);
    }
  });
}

async function saveBook(mode, snapshot) {
  const url = mode === "update" ? "/book/update" : "/book/register";
  const payload = {
    title: snapshot.title,
    author: snapshot.author,
    publisher: snapshot.publisher,
    schoolYear: snapshot.schoolYear,
    readingTime: snapshot.readingTime,
    difficulty: snapshot.difficulty,
    summary: snapshot.summary,
    keywords: snapshot.keywords,
    contentType: snapshot.contentType,
    state: snapshot.state,
    imageUrl: snapshot.imageUrl,
  };

  if (mode === "update") payload.contentId = currentContentId;

  try {
    await postJson(url, payload);
    alert("저장되었습니다.");
    currentContentId = mode === "update" ? currentContentId : null;
    await loadBookList(currentFilters());
  } catch (error) {
    console.error(error);
    alert(error.message ?? "저장 중 오류가 발생했습니다.");
  }
}

/* ===================== 카테고리 태그 ===================== */

function renderCategoryTags() {
  const tagList = document.querySelector(".tag-list");

  if (!tagList) return;

  tagList.innerHTML = "";

  categoryTags.forEach((tag, index) => {
    const tagItem = document.createElement("div");
    tagItem.className = "tag-item";

    const span = document.createElement("span");
    span.textContent = tag;

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "tag-remove";
    removeButton.dataset.index = index;
    removeButton.setAttribute("aria-label", `${tag} 삭제`);
    removeButton.innerHTML = '<i class="fa-solid fa-xmark"></i>';

    tagItem.append(span, removeButton);
    tagList.appendChild(tagItem);
  });
}

function addCategoryTag(text) {
  const tagText = text.trim().replace(/,$/, "");

  if (!tagText) return;
  if (categoryTags.includes(tagText)) return;

  categoryTags.push(tagText);
  renderCategoryTags();
}

function removeCategoryTag(index) {
  categoryTags.splice(index, 1);
  renderCategoryTags();
}

function setCategoryTags(list) {
  categoryTags = [...list];
  renderCategoryTags();
}

function initCategoryTagInput() {
  const input = document.getElementById("categoryInput");
  const tagList = document.querySelector(".tag-list");

  if (!input || !tagList) return;

  input.addEventListener("keydown", (event) => {
    // 한글 IME 조합 중에는 Enter를 무시 (마지막 글자가 중복 추가되는 문제 방지)
    if (event.isComposing || event.keyCode === 229) return;
    if (event.key !== "Enter" && event.key !== ",") return;
    event.preventDefault();
    addCategoryTag(input.value);
    input.value = "";
  });

  input.addEventListener("blur", () => {
    addCategoryTag(input.value);
    input.value = "";
  });

  tagList.addEventListener("click", (event) => {
    const removeButton = event.target.closest(".tag-remove");
    if (!removeButton) return;
    removeCategoryTag(Number(removeButton.dataset.index));
  });
}

/* ===================== 문제 출제 ===================== */

/* 기본/심화 탭 */
function initQuestionTabs() {
  document.querySelectorAll(".question-tabs button").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll(".question-tabs button").forEach((b) => b.classList.remove("active"));
      button.classList.add("active");
      activeLevel = button.dataset.level;
      renderQuestions();
    });
  });
}

function initQuestionActions() {
  document.getElementById("btnAddQuestion")?.addEventListener("click", addQuestion);
  document.getElementById("btnSaveQuestions")?.addEventListener("click", saveQuestions);
}

/* 도서별 문제 목록 조회 */
async function loadQuestions(contentId) {
  questionCache = [];

  if (contentId != null) {
    try {
      const response = await fetch(`/question/search?contentId=${contentId}`);
      const data = await response.json();
      if (!data.success) throw new Error(data.error?.message ?? "문제 조회에 실패했습니다.");
      questionCache = data.response ?? [];
    } catch (error) {
      console.error(error);
    }
  }

  renderQuestions();
}

/* 지문 예시 Summernote 에디터 */
function initSummernoteFor(qexEl) {
  if (!window.jQuery || !qexEl) return;
  if (window.jQuery(qexEl).next(".note-editor").length) return; // 이미 초기화됨

  window.jQuery(qexEl).summernote({
    height: 120,
    placeholder: "지문 예시를 입력해 주세요.",
    toolbar: [
      ["style", ["bold", "italic", "underline", "clear"]],
      ["para", ["ul", "ol", "paragraph"]],
      ["insert", ["picture", "link"]],
    ],
  });
}

function destroySummernote(qexEl) {
  if (window.jQuery && qexEl && window.jQuery(qexEl).next(".note-editor").length) {
    window.jQuery(qexEl).summernote("destroy");
  }
}

function destroyAllSummernote() {
  document.querySelectorAll(".question-editor .q-qex").forEach(destroySummernote);
}

function readQexValue(card) {
  const qexEl = card.querySelector(".q-qex");
  if (!qexEl) return "";
  if (window.jQuery && window.jQuery(qexEl).next(".note-editor").length) {
    return window.jQuery(qexEl).summernote("code");
  }
  return qexEl.value;
}

/* 현재 레벨 문제 렌더링 */
function renderQuestions() {
  const editor = document.querySelector(".question-editor");
  const numbers = document.querySelector(".question-numbers");

  if (!editor || !numbers) return;

  destroyAllSummernote();
  editor.innerHTML = "";
  numbers.innerHTML = "";

  if (currentContentId == null) {
    editor.innerHTML = '<p class="empty-hint">도서를 선택하면 문제를 편집할 수 있습니다.</p>';
    return;
  }

  const list = questionCache
    .filter((q) => q.qlevel === activeLevel)
    .sort((a, b) => String(a.qnum).localeCompare(String(b.qnum)));

  list.forEach((q) => editor.appendChild(buildQuestionCard(q)));

  if (!list.length) {
    editor.innerHTML = '<p class="empty-hint">등록된 문제가 없습니다. ‘+ 문제 추가’로 새 문제를 만들어 주세요.</p>';
  }

  refreshQuestionNumbers();
  editor.querySelectorAll(".question-card").forEach((card) => initSummernoteFor(card.querySelector(".q-qex")));

  if (!isHq) setQuestionsReadonly();
}

/* 문제 카드 DOM 생성 */
function buildQuestionCard(q) {
  const uid = ++questionUid;
  const isPassage = q ? q.qexgb === "Y" : false;

  const card = document.createElement("section");
  card.className = "question-card";
  card.dataset.qnum = q ? q.qnum : ""; // 기존 문제면 원본 qnum, 신규면 빈 값

  card.innerHTML = `
    <div class="question-card-head">
      <h3>문제</h3>
      <button type="button" class="btn danger-outline small btn-delete-question">삭제</button>
    </div>
    <div class="form-row">
      <label>문제 영역</label>
      <select class="q-qtype">
        ${QTYPE_CODES.map((t) => `<option value="${t.code}">${t.name}</option>`).join("")}
      </select>
    </div>
    <div class="form-row">
      <label>문제 유형</label>
      <div class="question-type-group">
        <label class="question-type-chip">
          <input type="radio" name="qtype-${uid}" value="normal">
          <span class="check-box"><i class="fa-solid fa-check"></i></span>
          <span class="label-text">일반 객관식</span>
        </label>
        <label class="question-type-chip">
          <input type="radio" name="qtype-${uid}" value="passage">
          <span class="check-box"><i class="fa-solid fa-check"></i></span>
          <span class="label-text">지문 예시형</span>
        </label>
      </div>
    </div>
    <div class="form-row full">
      <label>문제 질문</label>
      <input type="text" class="q-q" />
    </div>
    <div class="form-row full q-qex-row">
      <label>지문 예시</label>
      <textarea class="q-qex"></textarea>
    </div>
    <div class="form-row full">
      <label>보기</label>
      <div class="answer-list">
        <div><span>1</span><input type="text" class="q-e1"></div>
        <div><span>2</span><input type="text" class="q-e2"></div>
        <div><span>3</span><input type="text" class="q-e3"></div>
        <div><span>4</span><input type="text" class="q-e4"></div>
      </div>
    </div>
    <div class="form-row">
      <label>정답</label>
      <select class="q-ans">
        <option value="1">1</option>
        <option value="2">2</option>
        <option value="3">3</option>
        <option value="4">4</option>
      </select>
    </div>
  `;

  if (q) {
    card.querySelector(".q-qtype").value = q.qtype ?? "";
    card.querySelector(".q-q").value = q.q ?? "";
    card.querySelector(".q-qex").value = q.qex ?? "";
    card.querySelector(".q-e1").value = q.e1 ?? "";
    card.querySelector(".q-e2").value = q.e2 ?? "";
    card.querySelector(".q-e3").value = q.e3 ?? "";
    card.querySelector(".q-e4").value = q.e4 ?? "";
    card.querySelector(".q-ans").value = q.ans ?? "1";
  }

  const normal = card.querySelector('input[value="normal"]');
  const passage = card.querySelector('input[value="passage"]');
  (isPassage ? passage : normal).checked = true;

  const syncType = () => {
    card.querySelectorAll(".question-type-chip").forEach((chip) => {
      chip.classList.toggle("active", chip.querySelector("input").checked);
    });
    card.querySelector(".q-qex-row").style.display = passage.checked ? "" : "none";
  };

  card.querySelectorAll(`input[name="qtype-${uid}"]`).forEach((radio) => radio.addEventListener("change", syncType));
  syncType();

  card.querySelector(".btn-delete-question").addEventListener("click", () => deleteQuestion(card));

  return card;
}

/* 카드 넘버링 + 번호 버튼 재구성 */
function refreshQuestionNumbers() {
  const editor = document.querySelector(".question-editor");
  const numbers = document.querySelector(".question-numbers");

  if (!editor || !numbers) return;

  const cards = [...editor.querySelectorAll(".question-card")];
  numbers.innerHTML = "";

  cards.forEach((card, index) => {
    const no = index + 1;
    card.id = `question-${no}`;
    const heading = card.querySelector("h3");
    if (heading) heading.textContent = `${no}번 문제`;
    numbers.appendChild(buildNumberButton(no, card));
  });

  bindQuestionObserver(cards);
}

function buildNumberButton(no, card) {
  const editor = document.querySelector(".question-editor");
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = no;
  button.dataset.target = card.id;

  button.addEventListener("click", () => {
    document.querySelectorAll(".question-numbers button").forEach((b) => b.classList.remove("active"));
    button.classList.add("active");

    const editorTop = editor.getBoundingClientRect().top;
    const cardTop = card.getBoundingClientRect().top;
    editor.scrollTo({ top: editor.scrollTop + (cardTop - editorTop), behavior: "smooth" });
  });

  return button;
}

/* 스크롤 위치에 따라 번호 버튼 active 동기화 */
function bindQuestionObserver(cards) {
  const editor = document.querySelector(".question-editor");

  if (questionObserver) questionObserver.disconnect();
  if (!editor || !cards.length) return;

  questionObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        document.querySelectorAll(".question-numbers button").forEach((b) => b.classList.remove("active"));
        const activeButton = document.querySelector(`.question-numbers button[data-target="${entry.target.id}"]`);
        activeButton?.classList.add("active");
      });
    },
    { root: editor, threshold: 0.45 }
  );

  cards.forEach((card) => questionObserver.observe(card));
}

/* 문제 추가 (미저장 카드) */
function addQuestion() {
  if (currentContentId == null) {
    alert("먼저 도서를 저장한 뒤 문제를 등록해주세요.");
    return;
  }

  const editor = document.querySelector(".question-editor");
  editor.querySelector(".empty-hint")?.remove();

  const card = buildQuestionCard(null);
  editor.appendChild(card);
  refreshQuestionNumbers();
  initSummernoteFor(card.querySelector(".q-qex"));

  card.scrollIntoView({ behavior: "smooth", block: "start" });
}

/* 문제 삭제 */
async function deleteQuestion(card) {
  const qnum = card.dataset.qnum;

  // 미저장 카드는 DOM에서만 제거
  if (!qnum) {
    destroySummernote(card.querySelector(".q-qex"));
    card.remove();
    if (!document.querySelector(".question-editor .question-card")) {
      renderQuestions();
    } else {
      refreshQuestionNumbers();
    }
    return;
  }

  if (!confirm("이 문제를 삭제하시겠습니까?")) return;

  try {
    await postJson("/question/delete", { contentId: currentContentId, qlevel: activeLevel, qnum });
    await loadQuestions(currentContentId);
  } catch (error) {
    console.error(error);
    alert(error.message ?? "문제 삭제 중 오류가 발생했습니다.");
  }
}

/* 카드 값 수집 → 저장 payload */
function collectCard(card) {
  const isPassage = card.querySelector('input[value="passage"]').checked;

  return {
    contentId: currentContentId,
    qlevel: activeLevel,
    qtype: card.querySelector(".q-qtype").value,
    qexgb: isPassage ? "Y" : "N",
    q: card.querySelector(".q-q").value.trim(),
    qex: isPassage ? readQexValue(card).trim() : "",
    e1: card.querySelector(".q-e1").value.trim(),
    e2: card.querySelector(".q-e2").value.trim(),
    e3: card.querySelector(".q-e3").value.trim(),
    e4: card.querySelector(".q-e4").value.trim(),
    ans: card.querySelector(".q-ans").value,
    state: "Y",
  };
}

/* 문제 저장 (기존은 update, 신규는 register) */
async function saveQuestions() {
  if (currentMode === "new" || currentContentId == null) {
    alert("먼저 도서를 저장한 뒤 문제를 등록해주세요.");
    return;
  }

  const cards = [...document.querySelectorAll(".question-editor .question-card")];

  if (!cards.length) {
    alert("저장할 문제가 없습니다.");
    return;
  }

  // 신규 카드에 부여할 문제 번호 계산 (이미 사용 중인 qnum 회피)
  const used = new Set(cards.map((c) => c.dataset.qnum).filter(Boolean));
  let nextNum = 1;
  const nextQnum = () => {
    while (used.has(String(nextNum).padStart(2, "0"))) nextNum++;
    const value = String(nextNum).padStart(2, "0");
    used.add(value);
    return value;
  };

  try {
    for (const card of cards) {
      const payload = collectCard(card);

      if (card.dataset.qnum) {
        payload.qnum = card.dataset.qnum;
        await postJson("/question/update", payload);
      } else {
        payload.qnum = nextQnum();
        await postJson("/question/register", payload);
      }
    }

    alert("문제가 저장되었습니다.");
    await loadQuestions(currentContentId);
  } catch (error) {
    console.error(error);
    alert(error.message ?? "문제 저장 중 오류가 발생했습니다.");
  }
}
