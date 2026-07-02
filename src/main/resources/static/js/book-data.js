document.addEventListener("DOMContentLoaded", async () => {
  initBookList();
  initNewBookButton();
  initSaveBookButton();
  initQuestionTabs();
  initQuestionNavigation();
  initChipToggle();
  initCategoryTagInput();
  initQuestionTypeRadio();

  await loadGradeOptions();
  await loadBookList();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TOKEN = "hohoedu-master-csrf-token";

let bookListCache = [];
let currentMode = "edit"; // "edit" | "new"
let currentContentId = null;
let originalSnapshot = null;

/* 도서 목록 active + 선택 시 도서 정보 렌더링 */
function initBookList() {
  document.addEventListener("click", (event) => {
    const bookItem = event.target.closest(".book-item");

    if (!bookItem) return;

    document.querySelectorAll(".book-item").forEach((item) => {
      item.classList.remove("active");
    });

    bookItem.classList.add("active");

    const book = bookListCache.find((b) => String(b.contentId) === bookItem.dataset.contentId);

    renderBookInfo(book);
  });
}

/* 도서 목록 조회 */
async function loadBookList() {
  const listEl = document.querySelector(".book-list");

  if (!listEl) return;

  try {
    const response = await fetch("/book/search");
    const data = await response.json();

    if (!data.success) throw new Error(data.error?.message ?? "도서 목록 조회에 실패했습니다.");

    bookListCache = data.response;
    renderBookList(bookListCache);
    renderBookInfo(bookListCache[0]);
  } catch (error) {
    console.error(error);
  }
}

// /code/list/{groupCode}는 존재하지 않는 테이블(erp_code)을 조회하는 별개 버그가 있어 당장은 못 씀 -> 시드 코드값 하드코딩
const GRADE_CODES = [
  { code: "01", name: "초1" },
  { code: "02", name: "초2" },
  { code: "03", name: "초3" },
  { code: "04", name: "초4" },
  { code: "05", name: "초5" },
  { code: "06", name: "초6" },
];

function loadGradeOptions() {
  const select = document.getElementById("bookInfoGradeSelect");

  if (!select) return;

  select.innerHTML = "";

  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.textContent = "선택";
  select.appendChild(placeholder);

  GRADE_CODES.forEach(({ code, name }) => {
    const option = document.createElement("option");
    option.value = code;
    option.textContent = name;
    select.appendChild(option);
  });
}

const GRADE_COLORS = ["orange", "yellow", "green"];

function renderBookList(books) {
  const listEl = document.querySelector(".book-list");

  listEl.innerHTML = "";

  books.forEach((book, index) => {
    const li = document.createElement("li");
    li.className = "book-item" + (index === 0 ? " active" : "");
    li.dataset.contentId = book.contentId;

    const img = document.createElement("img");
    img.src = "/images/book-sample.png";
    img.alt = "";

    const info = document.createElement("div");

    const title = document.createElement("strong");
    title.textContent = book.originalTitle;

    const genre = document.createElement("p");
    genre.textContent = book.genreName ?? "";

    info.append(title, genre);

    const grade = document.createElement("span");
    grade.className = "grade " + GRADE_COLORS[index % GRADE_COLORS.length];
    grade.textContent = book.schoolyearName ?? "";

    li.append(img, info, grade);
    listEl.appendChild(li);
  });
}

/* 도서 정보 패널 렌더링 */
function renderBookInfo(book) {
  if (!book) return;

  currentMode = "edit";
  currentContentId = book.contentId;

  const titleEl = document.getElementById("bookInfoTitle");
  const titleInput = document.getElementById("bookInfoTitleInput");
  const authorInput = document.getElementById("bookInfoAuthorInput");
  const gradeSelect = document.getElementById("bookInfoGradeSelect");
  const summaryTextarea = document.getElementById("bookInfoSummary");

  if (titleEl) titleEl.textContent = `[${book.originalTitle}]`;
  if (titleInput) titleInput.value = book.originalTitle ?? "";
  if (authorInput) authorInput.value = book.author ?? "";
  if (summaryTextarea) summaryTextarea.value = book.summary ?? "";
  if (gradeSelect) gradeSelect.value = book.schoolyear ?? "";

  document.querySelectorAll(".chip-group .chip").forEach((chip) => {
    chip.classList.toggle("active", !!book.contentTypeName && chip.textContent.includes(book.contentTypeName));
  });

  setCategoryTags(book.keywords ? book.keywords.split(",").map((k) => k.trim()).filter(Boolean) : []);

  originalSnapshot = getFormSnapshot();
}

/* 신규 도서 등록 - 도서 정보 패널 비우기 */
function initNewBookButton() {
  const button = document.getElementById("btnNewBook");

  if (!button) return;

  button.addEventListener("click", () => {
    document.querySelectorAll(".book-item").forEach((item) => item.classList.remove("active"));

    currentMode = "new";
    currentContentId = null;

    const titleEl = document.getElementById("bookInfoTitle");
    const titleInput = document.getElementById("bookInfoTitleInput");
    const authorInput = document.getElementById("bookInfoAuthorInput");
    const gradeSelect = document.getElementById("bookInfoGradeSelect");
    const summaryTextarea = document.getElementById("bookInfoSummary");

    if (titleEl) titleEl.textContent = "[신규 도서]";
    if (titleInput) titleInput.value = "";
    if (authorInput) authorInput.value = "";
    if (summaryTextarea) summaryTextarea.value = "";
    if (gradeSelect) gradeSelect.value = "";

    document.querySelectorAll(".chip-group .chip").forEach((chip) => chip.classList.remove("active"));

    setCategoryTags([]);

    originalSnapshot = getFormSnapshot();
  });
}

/* 현재 도서 정보 패널 값 스냅샷 */
function getFormSnapshot() {
  return {
    title: document.getElementById("bookInfoTitleInput")?.value.trim() ?? "",
    author: document.getElementById("bookInfoAuthorInput")?.value.trim() ?? "",
    schoolYear: document.getElementById("bookInfoGradeSelect")?.value ?? "",
    summary: document.getElementById("bookInfoSummary")?.value.trim() ?? "",
    keywords: categoryTags.join(","),
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
    schoolYear: snapshot.schoolYear,
    summary: snapshot.summary,
    keywords: snapshot.keywords,
  };

  if (mode === "update") payload.contentId = currentContentId;

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [CSRF_HEADER]: CSRF_TOKEN,
      },
      body: JSON.stringify(payload),
    });

    const data = await response.json();

    if (!data.success) throw new Error(data.error?.message ?? "저장에 실패했습니다.");

    alert("저장되었습니다.");
    await loadBookList();
  } catch (error) {
    console.error(error);
    alert(error.message ?? "저장 중 오류가 발생했습니다.");
  }
}

/* 기본문제 / 심화문제 tab */
function initQuestionTabs() {
  document.addEventListener("click", (event) => {
    const tabButton = event.target.closest(".question-tabs button");

    if (!tabButton) return;

    document.querySelectorAll(".question-tabs button").forEach((button) => {
      button.classList.remove("active");
    });
 
    tabButton.classList.add("active");
  });
}

/* 문제 번호 클릭 시 해당 문제로 이동 */
function initQuestionNavigation() {
  const questionEditor = document.querySelector(".question-editor");
  const buttons = document.querySelectorAll(".question-numbers button");
  const cards = document.querySelectorAll(".question-card");

  if (!questionEditor || buttons.length === 0 || cards.length === 0) return;

  buttons.forEach((button) => {
    button.addEventListener("click", () => {
      const targetId = button.dataset.target;
      const targetCard = document.getElementById(targetId);

      if (!targetCard) return;

      buttons.forEach((btn) => btn.classList.remove("active"));
      button.classList.add("active");

      const editorTop = questionEditor.getBoundingClientRect().top;
      const cardTop = targetCard.getBoundingClientRect().top;
      const scrollTop = questionEditor.scrollTop + (cardTop - editorTop);

      questionEditor.scrollTo({
        top: scrollTop,
        behavior: "smooth",
      });
    });
  });

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;

        buttons.forEach((btn) => btn.classList.remove("active"));

        const activeButton = document.querySelector(
          `.question-numbers button[data-target="${entry.target.id}"]`
        );

        if (activeButton) {
          activeButton.classList.add("active");
        }
      });
    },
    {
      root: questionEditor,
      threshold: 0.45,
    }
  );

  cards.forEach((card) => observer.observe(card));
}

/* 분류 chip 선택 */
function initChipToggle() {
  document.addEventListener("click", (event) => {
    const chip = event.target.closest(".chip");

    if (!chip) return;

    chip.classList.toggle("active");
  });
}

/* 카테고리 태그 */
let categoryTags = [];

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

function initQuestionTypeRadio() {
  const chips = document.querySelectorAll(".question-type-chip");

  if (!chips.length) return;

  function syncActive() {
    chips.forEach((chip) => {
      const input = chip.querySelector("input");
      chip.classList.toggle("active", input.checked);
    });
  }

  chips.forEach((chip) => {
    const input = chip.querySelector("input");

    input.addEventListener("change", syncActive);
  });

  syncActive();
}