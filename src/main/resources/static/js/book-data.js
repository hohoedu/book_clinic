// PWA 설치 버튼(beforeinstallprompt)이 뜨려면 이 페이지에도 서비스워커가 등록돼 있어야 함
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch((err) => console.error("Service worker 등록 실패:", err));
  });
}

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
  initGradeSelectChange();
  initGuideLogout();
  initPwaInstallButton();
  initUnsavedChangesGuard();

  loadGradeOptions();
  await loadCurrentUser();
  await loadBookList();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TOKEN = "hohoedu-master-csrf-token";
const DEFAULT_BOOK_IMAGE = "/images/book-sample.png";

// 본사 센터 코드 — 이 센터만 마스터 도서(content) 편집 가능, 그 외는 하위 도서(item) 등록
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
  { code: "07", name: "중등" },
];

/* 학년별 뱃지 색상 클래스 (book-data.css) */
const GRADE_BADGE_CLASS = {
  "01": "grade-01",
  "02": "grade-02",
  "03": "grade-03",
  "04": "grade-04",
  "05": "grade-05",
  "06": "grade-06",
  "07": "grade-07",
};

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
  { code: "08", name: "문법" },
];

/*
 * 레벨/학년별 문제 영역
 * - 심화(02): 어휘, 문법 고정
 * - 기본(01), 초1·초2(schoolyear 01·02): 이해, 논리, 사고, 표현, 어휘, 감정
 * - 기본(01), 초3~초6(schoolyear 03~06): 이해, 논리, 사고, 표현, 어휘, 지식
 */
function qtypeCodesForActiveLevel() {
  if (activeLevel === "02") return QTYPE_CODES.filter((t) => ["06", "08"].includes(t.code));

  const schoolyear = getCurrentSchoolyear();
  const excluded = schoolyear === "01" || schoolyear === "02" ? "07" : "05";
  return QTYPE_CODES.filter((t) => t.code !== excluded && t.code !== "08");
}

/* 도서 정보 폼에서 현재 선택된 권장학년 (신규 도서도 저장 전에 폼 값을 그대로 기준으로 삼는다) */
function getCurrentSchoolyear() {
  return document.getElementById("bookInfoGradeSelect")?.value ?? "";
}

/* 이 학년에 심화문제 탭이 있는지 (초1·초2는 없음) */
function hasAdvancedLevel(schoolyear) {
  return schoolyear !== "01" && schoolyear !== "02";
}

/*
 * 학년+레벨별 고정 문제 수
 * - 기본(01): 초1~초4는 12문제, 초5·초6은 15문제
 * - 심화(02): 초1·초2는 없음(0), 초3·초4는 10문제, 초5·초6은 12문제
 */
function requiredQuestionCount(schoolyear, level) {
  if (level === "02") {
    if (!hasAdvancedLevel(schoolyear)) return 0;
    return schoolyear === "03" || schoolyear === "04" ? 10 : 12;
  }
  return schoolyear === "05" || schoolyear === "06" ? 15 : 12;
}


let bookListCache = [];
let questionsOriginalSnapshot = "[]"; // 뒤로가기/새로고침/닫기 시 저장 안 된 변경사항 감지용
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

  // 비본사: 마스터 도서 정보 read-only, 신규 등록 = 하위 도서 등록
  if (newBtn) newBtn.textContent = "+ 우리 센터 도서 등록";
  ["btnSaveBook", "btnDeleteSelected"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.hidden = true;
  });
  setBookFormReadonly(true);
}

/* 마스터 도서 정보 폼 읽기전용 처리 (하위 도서 등록 폼 입력칸은 제외) */
const MASTER_FIELD_IDS = [
  "bookInfoTitleInput", "bookInfoAuthorInput", "bookInfoPublisherInput",
  "bookInfoGradeSelect", "bookInfoGenreSelect", "bookInfoReadingTime", "bookInfoDifficulty",
  "bookInfoSummary", "categoryInput", "bookImageInput",
  "btnImageChange", "btnImageDelete",
  "bookInfoExtraDetail",
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
  const button = document.getElementById("btnGuideLogout");
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

/* PWA 설치 버튼 — 브라우저가 설치 가능하다고 판단하면 beforeinstallprompt가 발생함 */
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

/* 저장 안 된 변경사항이 있을 때 뒤로가기/새로고침/브라우저 닫기 경고 */
function initUnsavedChangesGuard() {
  window.addEventListener("beforeunload", (event) => {
    const bookDirty = JSON.stringify(getFormSnapshot()) !== JSON.stringify(originalSnapshot);
    const questionsDirty = getQuestionsSnapshot() !== questionsOriginalSnapshot;

    if (!bookDirty && !questionsDirty) return;

    event.preventDefault();
    event.returnValue = "";
  });
}

/* ===================== 비본사: 하위 도서 ===================== */

// 하위 도서 캐시 (contentId → item[])
let branchItemsByContent = {};

/* 하위 도서 전체 로드 후 contentId별로 그룹핑 */
async function loadBranchCenterItems() {
  branchItemsByContent = {};

  if (isHq || !currentUser?.centerCode) return;

  try {
    const response = await fetch(`/book/item/center/${encodeURIComponent(currentUser.centerCode)}`);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "하위 도서 조회에 실패했습니다.");

    (data.response ?? []).forEach((it) => {
      (branchItemsByContent[it.contentId] ??= []).push(it);
    });
  } catch (error) {
    console.error(error);
  }
}

/* 선택된 마스터의 하위 도서 패널 렌더링 (캐시 기준) */
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
    list.innerHTML = '<p class="empty-hint">우리 센터에 등록된 하위 도서가 없습니다.</p>';
  } else {
    items.forEach((it) => list.appendChild(buildItemCard(it, null)));
  }
}

/* CRUD 후 센터 하위 도서 재조회 → 패널 + 목록 갱신 */
async function refreshBranchItems() {
  await loadBranchCenterItems();
  renderBranchPanel();
  renderBookList(bookListCache);
}

/* 새 하위 도서 입력 카드 추가 (마스터 값으로 기본 채움) */
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

/* 하위 도서 카드 (기존 item=수정/삭제, 신규=취소/등록) */
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
    ${isExisting ? `
    <div class="item-loan-panel">
      <div class="item-loan-stock">
        보유 <b class="loan-qty-total">-</b> · 대여중 <b class="loan-qty-loaned">-</b> · 대여가능 <b class="loan-qty-available">-</b>
      </div>
      <div class="item-loan-form">
        <input type="text" class="it-loan-appid" placeholder="학생 앱ID">
        <button type="button" class="btn primary small it-loan-btn">대여</button>
        <button type="button" class="btn outline small it-loan-list-btn">대여 중 목록</button>
      </div>
      <div class="item-loan-list" hidden></div>
    </div>` : ''}
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
    renderLoanStock(card, item);
    card.querySelector(".it-loan-btn").addEventListener("click", () => loanBranchItem(card, item));
    card.querySelector(".it-loan-list-btn").addEventListener("click", () => toggleLoanList(card, item));
  } else {
    card.querySelector(".it-cancel").addEventListener("click", () => card.remove());
    card.querySelector(".it-register").addEventListener("click", () => registerBranchItem(card));
  }

  return card;
}

/* 보유/대여중/대여가능 수량 표시 */
function renderLoanStock(card, item) {
  const total = item.quantity ?? 0;
  const loaned = item.loanedQty ?? 0;
  const lost = item.lostQty ?? 0;
  const available = Math.max(total - loaned - lost, 0);
  card.querySelector(".loan-qty-total").textContent = total;
  card.querySelector(".loan-qty-loaned").textContent = loaned;
  card.querySelector(".loan-qty-available").textContent = available;
}

/* 학생에게 대여 처리 (재고 확인은 서버에서 최종 판단) */
async function loanBranchItem(card, item) {
  const appIdInput = card.querySelector(".it-loan-appid");
  const appId = appIdInput.value.trim();
  if (!appId) {
    alert("학생 앱ID를 입력해주세요.");
    return;
  }
  try {
    await postJson("/book/item/loan", { bcode: item.bcode, centerCode: currentUser?.centerCode, appId });
    alert("대여 처리되었습니다.");
    appIdInput.value = "";
    await refreshBranchItems();
  } catch (error) {
    console.error(error);
    alert(error.message ?? "대여 처리 중 오류가 발생했습니다.");
  }
}

/* 대여 중 목록 토글 (열 때마다 최신 상태로 다시 조회) */
async function toggleLoanList(card, item) {
  const listEl = card.querySelector(".item-loan-list");
  if (!listEl.hidden) {
    listEl.hidden = true;
    return;
  }
  listEl.hidden = false;
  await loadLoanList(listEl, item);
}

async function loadLoanList(listEl, item) {
  listEl.innerHTML = '<p class="empty-hint">불러오는 중...</p>';
  try {
    const response = await fetch(`/book/item/loan/${encodeURIComponent(item.bcode)}/${encodeURIComponent(currentUser?.centerCode)}`);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "대여 목록 조회에 실패했습니다.");

    const loans = data.response ?? [];
    if (!loans.length) {
      listEl.innerHTML = '<p class="empty-hint">대여 중인 건이 없습니다.</p>';
      return;
    }

    listEl.innerHTML = "";
    loans.forEach((loan) => {
      const row = document.createElement("div");
      row.className = "loan-row";
      const loanedAt = loan.loanedAt ? new Date(loan.loanedAt).toLocaleString() : "-";
      row.innerHTML = `
        <span class="loan-row-student">${loan.studentName ?? loan.studentId} (${loan.studentId})</span>
        <span class="loan-row-date">${loanedAt}</span>
        <button type="button" class="btn outline small loan-row-return">반납</button>
      `;
      row.querySelector(".loan-row-return").addEventListener("click", async () => {
        try {
          await postJson("/book/item/loan/return", { loanId: loan.loanId });
          await refreshBranchItems();
          const refreshedCard = document.querySelector(`.item-card[data-bcode="${CSS.escape(item.bcode)}"]`);
          const refreshedList = refreshedCard?.querySelector(".item-loan-list");
          if (refreshedList) {
            refreshedList.hidden = false;
            await loadLoanList(refreshedList, item);
          }
        } catch (error) {
          console.error(error);
          alert(error.message ?? "반납 처리 중 오류가 발생했습니다.");
        }
      });
      listEl.appendChild(row);
    });
  } catch (error) {
    console.error(error);
    listEl.innerHTML = '<p class="empty-hint">대여 목록을 불러오지 못했습니다.</p>';
  }
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

/* 신규 하위 도서 등록 (bcode는 서버에서 숫자 UUID 자동 생성) */
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

/* 기존 하위 도서 수정 */
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

/* 기존 하위 도서 삭제 */
async function deleteBranchItem(card) {
  if (!confirm("우리 센터 보유에서 삭제하시겠습니까?")) return;
  try {
    // item_del로 이관 후 우리 센터 매핑만 해제 (하위 레코드는 유지 → 삭제 도서함에서 복구 가능)
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

  if (grade) SCHOOLYEAR_CODES.forEach(({ code, name }) => grade.add(new Option(name, code)));
  // 분류(filterContentType) 옵션은 서버(Thymeleaf)에서 렌더링됨
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
let deletedBooksCache = [];

function initDeletedBooks() {
  const modal = document.getElementById("deletedModal");

  document.getElementById("btnDeletedBooks")?.addEventListener("click", () => {
    if (modal) modal.hidden = false;
    const searchInput = document.getElementById("deletedSearchInput");
    if (searchInput) searchInput.value = "";
    loadDeletedBooks();
  });

  document.getElementById("btnCloseDeleted")?.addEventListener("click", () => {
    if (modal) modal.hidden = true;
  });

  modal?.addEventListener("click", (event) => {
    if (event.target === modal) modal.hidden = true;
  });

  document.getElementById("deletedSearchInput")?.addEventListener("input", (event) => {
    renderDeletedBooks(filterDeletedBooks(deletedBooksCache, event.target.value));
  });
}

function filterDeletedBooks(books, keyword) {
  const term = keyword.trim().toLowerCase();
  if (!term) return books;

  return books.filter((book) => {
    const title = (book.originalTitle ?? book.bookTitle ?? "").toLowerCase();
    const author = (book.author ?? "").toLowerCase();
    return title.includes(term) || author.includes(term);
  });
}

async function loadDeletedBooks() {
  const listEl = document.getElementById("deletedList");

  if (!listEl) return;

  listEl.innerHTML = '<li class="deleted-empty">불러오는 중...</li>';

  // 본사=삭제된 마스터 도서(content_del), 비본사=삭제된 하위 도서(item_del)
  const url = isHq ? "/book/deleted" : "/book/item/deleted";

  try {
    const response = await fetch(url);
    const data = await response.json();
    if (!data.success) throw new Error(data.error?.message ?? "삭제 도서 조회에 실패했습니다.");

    let list = data.response ?? [];
    // 비본사는 우리 센터에서 삭제한 하위 도서만 표시
    if (!isHq) list = list.filter((it) => it.centerCode === currentUser?.centerCode);

    deletedBooksCache = list;
    const keyword = document.getElementById("deletedSearchInput")?.value ?? "";
    renderDeletedBooks(filterDeletedBooks(deletedBooksCache, keyword));
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
    ? "이 도서를 복구하시겠습니까?\n연결된 하위 도서·문제도 함께 복구됩니다."
    : "이 하위 도서를 복구하시겠습니까?";
  if (!confirm(message)) return;

  const modal = document.getElementById("deletedModal");
  if (modal) modal.hidden = true;

  const url = isHq ? "/book/restore" : "/book/item/restore";

  try {
    await postJson(url, { delId });
    alert("복구되었습니다.");
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

    // 비본사면 목록 들여쓰기용으로 우리 센터 하위 도서 캐시 로드
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
    // 도서 사이 구분선 (맨 위 도서 앞에는 넣지 않음)
    if (index > 0) {
      const divider = document.createElement("li");
      divider.className = "book-list-divider";
      listEl.appendChild(divider);
    }

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
    grade.className = "grade " + (GRADE_BADGE_CLASS[book.schoolyear] ?? "");
    grade.textContent = book.schoolyearName ?? "";

    li.append(img, info, grade);
    listEl.appendChild(li);

    // 비본사: 마스터 하위로 우리 센터 하위 도서 들여쓰기 표시
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
    // 마스터 항목 또는 하위 도서 항목 클릭 → 해당 마스터 선택
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

    if (!confirm(`[${title}]를 삭제하시겠습니까?\n연결된 하위 도서·문제도 함께 삭제됩니다.`)) return;

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

/* 분류 chip (단일 선택) - chip 자체는 서버(Thymeleaf)에서 렌더링, 여기서는 클릭만 바인딩 */
function initContentTypeChips() {
  const wrap = document.getElementById("contentTypeChips");

  if (!wrap) return;

  wrap.querySelectorAll(".chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      setContentTypeChip(chip.classList.contains("active") ? "" : chip.dataset.code);
    });
  });
}

/* 분류 선택 상태 반영 + 그에 딸린 부가 입력칸(연계교과/추천기관/수상명) 갱신 */
function setContentTypeChip(code) {
  selectedContentType = code ?? "";
  document
    .querySelectorAll("#contentTypeChips .chip")
    .forEach((chip) => chip.classList.toggle("active", chip.dataset.code === selectedContentType));
  updateExtraDetailField();
}

/* 분류(content_type)별 부가 입력칸 라벨/placeholder — 교과연계(01)/기관추천(04)/인증수상작(05)만 해당 */
const EXTRA_DETAIL_BY_TYPE = {
  "01": { label: "연계교과", placeholder: "연계 교과목을 입력해 주세요." },
  "04": { label: "추천기관", placeholder: "추천기관명을 입력해 주세요." },
  "05": { label: "수상명", placeholder: "수상명을 입력해 주세요." },
};

/* 사용여부 라디오 (use → Y, stop → N) - 선택 즉시 확인 후 DB 반영 */
function initStatusToggle() {
  document.querySelectorAll('.status-toggle input[name="bookStatus"]').forEach((radio) => {
    radio.addEventListener("change", async () => {
      if (currentMode === "new" || currentContentId == null) return;

      const newState = getStatusValue();
      const prevState = newState === "N" ? "Y" : "N";
      const title = document.getElementById("bookInfoTitleInput")?.value.trim() || "선택한 도서";

      if (!confirm(`[${title}]의 상태를 변경하시겠습니까?`)) {
        setStatusRadio(prevState);
        return;
      }

      try {
        await postJson("/book/update", { contentId: currentContentId, state: newState });
        alert("변경이 완료되었습니다.");
        if (originalSnapshot) originalSnapshot.state = newState;
        const book = bookListCache.find((b) => b.contentId === currentContentId);
        if (book) book.state = newState;
      } catch (error) {
        console.error(error);
        alert(error.message ?? "상태 변경 중 오류가 발생했습니다.");
        setStatusRadio(prevState);
      }
    });
  });
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
  setValue("bookInfoGenreSelect", book.genre ?? "");
  setValue("bookInfoReadingTime", book.readingTime ?? "");
  setValue("bookInfoDifficulty", book.difficulty ?? "");
  setValue("bookInfoSummary", book.summary ?? "");

  setStatusRadio(book.state);
  setBookImage(book.imageUrl);
  setContentTypeChip(book.contentType ?? "");
  setCategoryTags(book.keywords ? book.keywords.split(",").map((k) => k.trim()).filter(Boolean) : []);
  updateExtraDetailField(book.extraDetailName ?? "");

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
  setValue("bookInfoGenreSelect", "");
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
  if (!el) return;
  el[isText ? "textContent" : "value"] = text;
  // 제목이 길어 말줄임(ellipsis) 처리될 때 마우스 오버로 전체 텍스트 확인 가능하도록
  if (isText) el.title = text;
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
    // 비본사는 '신규 등록'이 하위 도서 입력 카드 추가
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
    genre: document.getElementById("bookInfoGenreSelect")?.value ?? "",
    readingTime: document.getElementById("bookInfoReadingTime")?.value ?? "",
    difficulty: document.getElementById("bookInfoDifficulty")?.value ?? "",
    summary: document.getElementById("bookInfoSummary")?.value.trim() ?? "",
    keywords: categoryTags.join(","),
    contentType: selectedContentType,
    state: getStatusValue(),
    imageUrl: currentImageUrl,
    extraDetail: document.getElementById("bookInfoExtraDetail")?.value.trim() ?? "",
  };
}

/* 도서 정보 필수 항목 (해시태그/이미지는 선택 항목이라 제외) */
const REQUIRED_BOOK_FIELDS = [
  { key: "title", label: "도서명" },
  { key: "author", label: "저자" },
  { key: "publisher", label: "출판사" },
  { key: "schoolYear", label: "권장학년" },
  { key: "contentType", label: "분류" },
  { key: "genre", label: "장르" },
  { key: "readingTime", label: "독서 예상 시간" },
  { key: "difficulty", label: "난이도" },
  { key: "summary", label: "도서 소개" },
];

function getMissingBookFields(snapshot) {
  return REQUIRED_BOOK_FIELDS.filter((field) => !snapshot[field.key]).map((field) => field.label);
}

/*
 * 출제되지 않은 문제 번호 확인 — 현재 보고 있는 탭은 화면에 입력된 값을 기준으로,
 * 나머지 탭은 아직 화면에 없으므로 서버에 저장된 값(questionCache)을 기준으로 판단한다.
 */
function getMissingQuestionSlots() {
  const schoolyear = getCurrentSchoolyear();
  if (!schoolyear) return [];

  const levels = hasAdvancedLevel(schoolyear) ? ["01", "02"] : ["01"];
  const missing = [];

  levels.forEach((level) => {
    const count = requiredQuestionCount(schoolyear, level);
    if (!count) return;

    const filledQnums = level === activeLevel
      ? new Set(
          [...document.querySelectorAll(".question-editor .question-card")]
            .filter((card) => collectCard(card).q)
            .map((card) => card.dataset.qnum)
        )
      : new Set(questionCache.filter((q) => q.qlevel === level).map((q) => String(q.qnum)));

    for (let i = 1; i <= count; i++) {
      const qnum = String(i).padStart(2, "0");
      if (!filledQnums.has(qnum)) missing.push({ level, qnum });
    }
  });

  return missing;
}

/* 문제 질문은 입력했는데 보기(1~4)나 정답이 비어있는 카드 — 이 경우는 그대로 저장하면 안 되는 오류라 확인 없이 막는다 */
function getIncompleteQuestionCards() {
  return [...document.querySelectorAll(".question-editor .question-card")]
    .map((card) => ({ qnum: card.dataset.qnum, payload: collectCard(card) }))
    .filter(({ payload }) => payload.q && (!payload.e1 || !payload.e2 || !payload.e3 || !payload.e4 || !payload.ans))
    .map(({ qnum }) => qnum);
}

/* 저장 버튼 - 도서 정보 + 문제 등록을 한 번에 처리 (신규 등록 / 수정 / 변경 없음 분기) */
function initSaveBookButton() {
  const button = document.getElementById("btnSaveBook");

  if (!button) return;

  button.addEventListener("click", async () => {
    const snapshot = getFormSnapshot();

    const missingFields = getMissingBookFields(snapshot);
    if (missingFields.length) {
      alert(`다음 항목을 입력해주세요.\n${missingFields.join(", ")}`);
      return;
    }

    const incompleteQuestions = getIncompleteQuestionCards();
    if (incompleteQuestions.length) {
      alert(`${incompleteQuestions.join(", ")}번 문제의 보기와 정답을 모두 입력해주세요.`);
      return;
    }

    const bookDirty = currentMode !== "edit" || JSON.stringify(snapshot) !== JSON.stringify(originalSnapshot);
    const questionsDirty = getQuestionsSnapshot() !== questionsOriginalSnapshot;

    if (!bookDirty && !questionsDirty) {
      alert("변경 사항이 없습니다.");
      return;
    }

    if (getMissingQuestionSlots().length) {
      const proceed = await customConfirm("출제되지 않은 문제가 있습니다. 그래도 저장하시겠습니까?");
      if (!proceed) return;
    }

    try {
      let contentId = currentContentId;

      if (bookDirty) {
        contentId = await saveBook(currentMode === "edit" ? "update" : "register", snapshot);
      }

      if (questionsDirty) {
        await saveQuestions(contentId);
      }

      alert("저장되었습니다.");
      currentContentId = contentId;
      await loadBookList(currentFilters());
    } catch (error) {
      console.error(error);
      alert(error.message ?? "저장 중 오류가 발생했습니다.");
    }
  });
}

/* 도서 정보 저장 → 저장된(또는 기존) contentId 반환 */
async function saveBook(mode, snapshot) {
  const url = mode === "update" ? "/book/update" : "/book/register";
  const payload = {
    title: snapshot.title,
    author: snapshot.author,
    publisher: snapshot.publisher,
    schoolYear: snapshot.schoolYear,
    genre: snapshot.genre,
    readingTime: snapshot.readingTime,
    difficulty: snapshot.difficulty,
    summary: snapshot.summary,
    keywords: snapshot.keywords,
    contentType: snapshot.contentType,
    state: snapshot.state,
    imageUrl: snapshot.imageUrl,
    extraDetail: snapshot.extraDetail,
  };

  if (mode === "update") payload.contentId = currentContentId;

  const data = await postJson(url, payload);
  return mode === "update" ? currentContentId : data.response;
}

/* ===================== 카테고리 태그 ===================== */

function renderCategoryTags() {
  const tagList = document.querySelector(".tag-list");

  if (!tagList) return;

  tagList.innerHTML = "";

  categoryTags.forEach((tag, index) => {
    const tagItem = document.createElement("div");
    tagItem.className = "tag-item";

    // 저장은 원문 그대로, 표시만 # 접두 (해시태그 스타일)
    const span = document.createElement("span");
    span.textContent = `#${tag}`;

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
  // 표시할 때 #을 붙이므로, 사용자가 #을 직접 입력해도 저장값에서는 제거
  const tagText = text.trim().replace(/,$/, "").replace(/^#+/, "");

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
    if (event.key !== "Enter") return;
    event.preventDefault();
    addCategoryTag(input.value);
    input.value = "";
  });

  // ','는 keydown이 IME 조합에 가로막히므로, 입력값에 쉼표가 생기면 그 앞부분을 태그로 추가
  input.addEventListener("input", () => {
    if (!input.value.includes(",")) return;
    const parts = input.value.split(",");
    const remainder = parts.pop(); // 마지막 조각은 아직 입력 중인 텍스트
    parts.forEach((part) => addCategoryTag(part));
    input.value = remainder;
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

/* ===================== 분류별 부가 입력칸 (연계교과 / 추천기관 / 수상명) ===================== */

/* 선택된 분류(selectedContentType)에 맞춰 라벨/placeholder를 갈아끼우고 노출 여부를 정한다.
 * value를 넘기면 그 값으로 채우고, 생략하면 분류 전환 시 이전 값은 비운다. */
function updateExtraDetailField(value = "") {
  const row = document.getElementById("extraDetailRow");
  const label = document.getElementById("extraDetailLabel");
  const input = document.getElementById("bookInfoExtraDetail");
  if (!row || !label || !input) return;

  const meta = EXTRA_DETAIL_BY_TYPE[selectedContentType];
  if (!meta) {
    row.hidden = true;
    input.value = "";
    return;
  }

  row.hidden = false;
  label.textContent = meta.label;
  input.placeholder = meta.placeholder;
  input.value = value;
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

/* 권장학년(초1·초2)엔 심화문제 탭 자체가 없으므로, 학년이 바뀔 때마다 탭 노출 여부를 갱신 */
function updateQuestionTabsVisibility() {
  const advancedTab = document.querySelector('.question-tabs button[data-level="02"]');
  if (!advancedTab) return;

  const showAdvanced = hasAdvancedLevel(getCurrentSchoolyear());
  advancedTab.hidden = !showAdvanced;

  if (!showAdvanced && activeLevel === "02") {
    activeLevel = "01";
    document.querySelectorAll(".question-tabs button").forEach((b) => b.classList.toggle("active", b.dataset.level === "01"));
  }
}

/* 권장학년 변경 시 문제 입력 폼(개수/탭)을 다시 그린다 */
function initGradeSelectChange() {
  document.getElementById("bookInfoGradeSelect")?.addEventListener("change", renderQuestions);
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

/*
 * 학년+레벨별 고정 문제 수만큼 입력 폼을 항상 그대로 보여준다 (문제 추가 버튼 없음).
 * 이미 저장된 문제가 있으면 해당 슬롯(qnum)에 값을 채워서, 없으면 빈 폼으로 렌더링한다.
 */
function renderQuestions() {
  const editor = document.querySelector(".question-editor");
  const numbers = document.querySelector(".question-numbers");

  if (!editor || !numbers) return;

  updateQuestionTabsVisibility();

  destroyAllSummernote();
  editor.innerHTML = "";
  numbers.innerHTML = "";

  const schoolyear = getCurrentSchoolyear();

  if (!schoolyear) {
    editor.innerHTML = '<p class="empty-hint">권장학년을 선택하면 문제 입력 폼이 표시됩니다.</p>';
    questionsOriginalSnapshot = "[]";
    return;
  }

  const count = requiredQuestionCount(schoolyear, activeLevel);

  if (count === 0) {
    editor.innerHTML = '<p class="empty-hint">이 학년은 심화문제가 없습니다.</p>';
    questionsOriginalSnapshot = "[]";
    return;
  }

  const byQnum = new Map(questionCache.filter((q) => q.qlevel === activeLevel).map((q) => [String(q.qnum), q]));

  if (isHq) {
    // 본사: 빈 슬롯까지 항상 고정 개수만큼 보여줘서 바로 입력할 수 있게 함
    for (let i = 1; i <= count; i++) {
      const qnum = String(i).padStart(2, "0");
      editor.appendChild(buildQuestionCard(byQnum.get(qnum) ?? null, qnum));
    }
  } else {
    // 비본사(읽기전용): 실제로 저장된 문제만 보여줌 (빈 슬롯을 볼 필요 없음)
    [...byQnum.keys()].sort().forEach((qnum) => editor.appendChild(buildQuestionCard(byQnum.get(qnum), qnum)));
    if (!byQnum.size) editor.innerHTML = '<p class="empty-hint">등록된 문제가 없습니다.</p>';
  }

  refreshQuestionNumbers();
  editor.querySelectorAll(".question-card").forEach((card) => initSummernoteFor(card.querySelector(".q-qex")));

  if (!isHq) setQuestionsReadonly();

  questionsOriginalSnapshot = getQuestionsSnapshot();
}

/* 현재 화면에 표시된 문제 카드들의 상태 스냅샷 (변경 여부 비교용) */
function getQuestionsSnapshot() {
  const cards = [...document.querySelectorAll(".question-editor .question-card")];
  return JSON.stringify(cards.map((card) => ({ qnum: card.dataset.qnum ?? "", ...collectCard(card) })));
}

/* 문제 카드 DOM 생성 — qnum은 학년별 고정 슬롯 번호(항상 존재), q는 그 슬롯에 저장된 문제(없으면 null) */
function buildQuestionCard(q, qnum) {
  const uid = ++questionUid;
  const isPassage = q ? q.qexgb === "Y" : false;

  const card = document.createElement("section");
  card.className = "question-card";
  card.dataset.qnum = qnum;
  card.dataset.existing = q ? "1" : ""; // 서버에 이미 저장된 슬롯인지 (저장 시 update/register 분기용)

  card.innerHTML = `
    <div class="question-card-head">
      <h3>문제</h3>
      <button type="button" class="btn danger-outline small btn-delete-question">삭제</button>
    </div>
    <div class="form-row">
      <label>문제 영역</label>
      <select class="q-qtype">
        ${qtypeCodesForActiveLevel().map((t) => `<option value="${t.code}">${t.name}</option>`).join("")}
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

  // 비본사는 문제 수정이 불가하므로 지문을 입력용 textarea 대신 읽기 전용 텍스트로 표시
  if (!isHq) {
    const qexEl = card.querySelector(".q-qex");
    if (qexEl) {
      const view = document.createElement("div");
      view.className = "q-qex-view";
      view.innerHTML = q?.qex ?? "";
      qexEl.replaceWith(view);
    }
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

/*
 * 문제 삭제 — 슬롯 개수는 학년별로 고정돼 있으므로 카드 자체를 없애지 않는다.
 * 서버에 저장된 문제면 삭제 API 호출 후 다시 불러오고(그 슬롯은 빈 폼으로 되돌아옴),
 * 아직 저장 안 한 슬롯이면 그냥 입력값만 비운다.
 */
async function deleteQuestion(card) {
  if (card.dataset.existing !== "1") {
    const qnum = card.dataset.qnum;
    destroySummernote(card.querySelector(".q-qex"));
    const fresh = buildQuestionCard(null, qnum);
    card.replaceWith(fresh);
    initSummernoteFor(fresh.querySelector(".q-qex"));
    refreshQuestionNumbers();
    return;
  }

  if (!confirm("이 문제를 삭제하시겠습니까?")) return;

  try {
    await postJson("/question/delete", { contentId: currentContentId, qlevel: activeLevel, qnum: card.dataset.qnum });
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

/* 문제 저장 — 슬롯의 qnum은 이미 고정돼 있으므로, 서버에 있던 슬롯이면 update, 없던 슬롯이면 register.
   내용을 하나도 입력하지 않은 빈 슬롯은 그냥 건너뛴다(불필요한 빈 문제 등록 방지).
   신규 도서는 저장 버튼 클릭 시 도서 등록이 먼저 끝난 뒤 그 contentId로 문제를 저장한다. */
async function saveQuestions(contentId) {
  currentContentId = contentId;

  const cards = [...document.querySelectorAll(".question-editor .question-card")];

  for (const card of cards) {
    const payload = collectCard(card);
    if (!payload.q) continue; // 아직 입력 안 한 빈 슬롯은 저장하지 않음

    payload.qnum = card.dataset.qnum;

    if (card.dataset.existing === "1") {
      await postJson("/question/update", payload);
    } else {
      await postJson("/question/register", payload);
    }
  }
}
