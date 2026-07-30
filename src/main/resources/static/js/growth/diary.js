// 독서일지 — 예약 내역을 기본 골격으로 깔고, 일지(diary/diary_detail/attitude) 데이터가 있으면
// 그 값으로 갈아끼운 목록을 /admin/growth/diary/list에서 받아 렌더링한다 (2026-07-30).
// 덮어쓰기 판단은 서버(DiaryMapper의 COALESCE)가 하고, 화면은 내려온 값을 그대로 그린다.
// 저장 경로는 두 개다: 등/퇴실 시각은 행의 "변경" 버튼으로 즉시(POST /time), 독서태도·기타
// 전달사항은 상단 "저장" 버튼으로 바뀐 행만 모아 일괄(POST /save).

// 교시 목록은 별도 마스터 데이터가 없어 monitor-live.js와 같은 고정 목록을 쓴다
const TIME_SLOTS = [
  { key: "", label: "타임 선택" },
  { key: "1", label: "1교시(08:00~09:00)" },
  { key: "2", label: "2교시(09:00~10:00)" },
  { key: "3", label: "3교시(10:00~11:00)" },
  { key: "4", label: "4교시(11:00~12:00)" },
];

// 문제풀이 합격선 = 전체 문항의 2/3 (ClinicService.QUIZ_PASS_RATIO와 같은 규칙 — 서버가 뱃지
// 등급을 따로 내려주지 않아 화면에서 같은 식으로 다시 판정한다)
const QUIZ_PASS_RATIO = 2 / 3;

let attitudeCodeOptions = [];

document.addEventListener("DOMContentLoaded", () => {
  initDatePicker();
  initSlotPicker();
  initSearchControls();
  initSaveAllButton();
  loadDiaryList();
});

// 정적 CSRF 토큰 — SecurityConfig의 StaticCsrfTokenRepository와 짝을 맞춘다(다른 관리자 화면과 동일)
const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TOKEN = "hohoedu-master-csrf-token";

async function getJson(url) {
  const response = await fetch(url);
  const data = await response.json();
  if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");
  return data.response;
}

async function postJson(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", [CSRF_HEADER]: CSRF_TOKEN },
    body: JSON.stringify(body),
  });
  const data = await response.json();
  if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");
  return data.response;
}

function todayStr(d = new Date()) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function initDatePicker() {
  const dateInput = document.getElementById("diaryDate");
  const dateDisplay = document.getElementById("diaryDateDisplay");
  if (!dateInput || !dateDisplay) return;

  dateInput.value = todayStr();
  updateDateDisplay(dateInput, dateDisplay);

  dateInput.closest(".monitor-date-trigger").addEventListener("click", () => {
    if (typeof dateInput.showPicker === "function") dateInput.showPicker();
  });

  dateInput.addEventListener("change", () => {
    updateDateDisplay(dateInput, dateDisplay);
    loadDiaryList();
  });
}

function updateDateDisplay(input, display) {
  const [y, m, d] = input.value.split("-");
  display.textContent = `${y}. ${m}. ${d}`;
}

function initSlotPicker() {
  const select = document.getElementById("diarySlot");
  select.innerHTML = "";
  TIME_SLOTS.forEach(({ key, label }) => {
    const option = document.createElement("option");
    option.value = key;
    option.textContent = label;
    select.appendChild(option);
  });
  select.addEventListener("change", loadDiaryList);
}

function initSearchControls() {
  document.getElementById("btnSearchDiary").addEventListener("click", loadDiaryList);
  document.getElementById("diaryStudentKeyword").addEventListener("keydown", (e) => {
    if (e.key === "Enter") loadDiaryList();
  });
}

/* 상단 "저장" — 독서태도/기타 전달사항을 행별로 나눠 보내지 않고 한 번에 저장한다.
   조회 시점 값(data-original*)과 다른 행만 담아 보내서, 손대지 않은 행의 일지를 괜히
   덮어쓰지 않는다(다른 직원이 그 사이 고쳐둔 값이 날아가는 걸 막는 목적도 있다). */
function initSaveAllButton() {
  document.getElementById("btnSaveAllDiaries").addEventListener("click", async (e) => {
    const button = e.currentTarget;
    const items = collectDirtyItems();
    if (items.length === 0) {
      alert("변경된 내용이 없습니다.");
      return;
    }

    button.disabled = true;
    try {
      const message = await postJson("/admin/growth/diary/save", { items });
      alert(message ?? "저장되었습니다.");
      await loadDiaryList(); // 저장 결과(서버 값)로 목록을 다시 그려 기준값을 갱신한다
    } catch (err) {
      alert(err.message);
    } finally {
      button.disabled = false;
    }
  });
}

function collectDirtyItems() {
  const items = [];
  document.querySelectorAll(".diary-row").forEach((rowEl) => {
    if (!rowEl.dataset.sessionId) return; // 미입실 행 — 저장할 일지 헤더를 만들 세션이 없다

    const attitudeCodes = [...rowEl.querySelectorAll(".diary-attitude input[type='checkbox']")]
      .filter((checkbox) => checkbox.checked)
      .map((checkbox) => checkbox.value);
    const memo = rowEl.querySelector(".diary-note textarea").value;

    const attitudesChanged = [...attitudeCodes].sort().join(",") !== rowEl.dataset.originalAttitudes;
    const memoChanged = memo !== rowEl.dataset.originalMemo;
    if (!attitudesChanged && !memoChanged) return;

    items.push({
      sessionId: Number(rowEl.dataset.sessionId),
      studentId: rowEl.dataset.studentId,
      attitudeCodes,
      memo,
    });
  });
  return items;
}

async function loadDiaryList() {
  const params = new URLSearchParams({ date: document.getElementById("diaryDate").value || todayStr() });
  const slot = document.getElementById("diarySlot").value;
  const keyword = document.getElementById("diaryStudentKeyword").value.trim();
  if (slot) params.set("timeSlot", slot);
  if (keyword) params.set("keyword", keyword);

  try {
    const view = await getJson(`/admin/growth/diary/list?${params}`);
    attitudeCodeOptions = view.attitudeCodeOptions ?? [];
    renderRows(view.rows ?? []);
  } catch (e) {
    console.error("독서일지 조회 실패", e);
  }
}

function renderRows(rows) {
  const list = document.getElementById("diaryList");
  list.innerHTML = "";

  if (rows.length === 0) {
    const empty = document.createElement("p");
    empty.className = "diary-empty";
    empty.textContent = "해당 조건의 예약이 없습니다.";
    list.appendChild(empty);
    return;
  }

  rows.forEach((row, index) => list.appendChild(buildRow(row, index + 1)));
  initTimeInputs();
  initTimeEditButtons();
  initAttitudeCheckboxes();
}

function buildRow(row, no) {
  const article = document.createElement("article");
  article.className = "diary-row";
  article.dataset.studentId = row.studentId ?? "";
  article.dataset.sessionId = row.sessionId ?? "";
  article.dataset.diaryKey = row.diaryKey ?? "";
  // 일괄 저장 때 "바뀐 행"만 골라내기 위한 조회 시점 기준값
  article.dataset.originalAttitudes = [...(row.attitudeCodes ?? [])].sort().join(",");
  article.dataset.originalMemo = row.memo ?? "";

  const locked = row.sessionId == null;

  article.innerHTML = `
    <div class="diary-row-no">${no}</div>

    <div class="diary-student">
      <div class="diary-student-name">${escapeHtml(row.studentName ?? "")}${
        row.helpNeeded ? `<i class="fa-solid fa-seedling name-flair" title="혼자 읽기 어려워요"></i>` : ""
      }</div>
      <div class="diary-student-grade">${escapeHtml(row.gradeName ?? "")}</div>
      ${timeBoxesHtml(row)}
    </div>

    <div class="diary-books">${booksHtml(row)}</div>

    <div class="diary-attitude">
      <div class="diary-section-title">독서 태도</div>
      ${attitudeHtml(row)}
    </div>

    <div class="diary-note">
      <div class="diary-section-title">기타 전달사항</div>
      <textarea placeholder="전달사항 내용이 들어갑니다." maxlength="500"${locked ? " disabled" : ""}>${escapeHtml(row.memo ?? "")}</textarea>
    </div>

    <div class="diary-send">${sendButtonHtml(row, locked)}</div>
  `;
  return article;
}

/*
 * 전송 버튼 아이콘 — 3가지 상태를 이미지로 구분한다:
 *   1) 학생 앱 미가입(hasAppToken=false) → no_app.png, 발송 자체가 불가하니 버튼을 잠근다
 *   2) 아직 발송 전(isSend=false)        → send1.png
 *   3) 발송 완료(isSend=true)            → send2.png
 * 미입실 행(locked)은 일지가 없어 발송할 것도 없으므로 앱 미가입과 같은 취급으로 잠근다.
 */
function sendButtonHtml(row, locked) {
  if (locked || !row.hasAppToken) {
    return `<button type="button" class="diary-note-send" title="학생 앱 미가입" disabled>
      <img src="/images/icons/no_app.png" alt="앱 미가입" />
    </button>`;
  }
  const icon = row.isSend ? "send2.png" : "send1.png";
  const title = row.isSend ? "발송 완료" : "발송";
  return `<button type="button" class="diary-note-send" title="${title}">
    <img src="/images/icons/${icon}" alt="${title}" />
  </button>`;
}

/* 등/하원 시각 — 일지 값이 있으면 그 값, 없으면 세션(입·퇴실) 값이 서버에서 이미 채워져 있다.
   미입실(예약만 있는 상태)은 저장할 일지 헤더를 만들 세션이 없어서(diary.session_id가 NOT NULL)
   입력 자체를 막는다 — 서버(DiaryService)도 같은 이유로 400을 돌려준다. */
function timeBoxesHtml(row) {
  const inTime = row.inTime ?? "";
  const outTime = row.outTime ?? "";
  const locked = row.sessionId == null ? " disabled" : "";
  return `
    <div class="time-boxes" data-session-id="${row.sessionId ?? ""}">
      <div class="time-start"> 입실
        <input type="text" class="time_input start-time"
               value="${inTime}" data-original="${inTime}"
               placeholder="00:00" inputmode="numeric" maxlength="5"${locked} />
      </div>
      <div class="time-end"> 퇴실
        <input type="text" class="time_input end-time"
               value="${outTime}" data-original="${outTime}"
               placeholder="00:00" inputmode="numeric" maxlength="5"${locked} />
      </div>
      <button type="button" class="diary-time-edit-btn"${locked}>변경</button>
    </div>
  `;
}

function booksHtml(row) {
  const books = row.books ?? [];
  if (books.length === 0) {
    const message = row.attendStatus === "NOT_ENTERED" ? "아직 입실하지 않았어요." : "읽은 책이 없어요.";
    return `<p class="diary-books-empty">${message}</p>`;
  }

  return books.map((book) => `
    <div class="diary-book-card">
      <img class="diary-book-cover" src="${escapeHtml(book.imageUrl ?? "/images/book-sample.png")}" alt="" />
      <div class="diary-book-info">
        <p class="diary-book-title">${escapeHtml(book.bookTitle ?? "")}</p>
        <p class="diary-book-minutes">${book.readMinutes == null ? "-" : `${book.readMinutes}분`}</p>
      </div>
      ${quizHtml("기본문제", book.basicCorrectCount, book.basicTotalCount, false)}
      ${quizHtml("심화문제", book.advancedCorrectCount, book.advancedTotalCount, true)}
    </div>
  `).join("");
}

/* 아직 안 푼 난이도는 점수 자리에 "-"만 두고 뱃지를 붙이지 않는다(모니터링 카드와 같은 표시 규칙) */
function quizHtml(label, correct, total, advanced) {
  const solved = correct != null && total != null && total > 0;
  return `
    <div class="diary-book-quiz">
      <span class="diary-quiz-label">${label}</span>
      <strong class="diary-quiz-score">${solved ? `${correct} / ${total}` : "-"}</strong>
      ${solved ? quizBadgeHtml(correct, total, advanced) : ""}
    </div>
  `;
}

function quizBadgeHtml(correct, total, advanced) {
  if (correct >= total) {
    return `<span class="diary-quiz-badge badge-king">${advanced ? "심화왕!" : "독서왕!"}</span>`;
  }
  if (correct >= Math.ceil(total * QUIZ_PASS_RATIO)) {
    return `<span class="diary-quiz-badge badge-pass">통과</span>`;
  }
  return `<span class="diary-quiz-badge badge-fail">재도전</span>`;
}

/* 태도 체크박스는 마스터(use_yn=1)로 그리고, 그 행의 일지에 저장된 코드만 체크 상태로 둔다 */
function attitudeHtml(row) {
  const checked = new Set(row.attitudeCodes ?? []);
  const locked = row.sessionId == null ? " disabled" : ""; // 미입실 행은 저장 대상이 아니라 잠근다
  return attitudeCodeOptions.map((option) => `
    <label class="diary-checkbox${checked.has(option.attitudeCode) ? " checked" : ""}">
      <input type="checkbox" value="${escapeHtml(option.attitudeCode)}"${checked.has(option.attitudeCode) ? " checked" : ""}${locked} />
      ${escapeHtml(option.attitudeName)}
    </label>
  `).join("");
}

/* 등/하원 시각 입력 — 숫자만 받아서 HH:MM 꼴로 자동 정리하고, 포커스가 빠질 때
   유효한 시각이 아니면 data-original(원래 값)로 되돌린다. */
function initTimeInputs() {
  document.querySelectorAll(".time_input").forEach((input) => {
    input.addEventListener("input", () => {
      const digits = input.value.replace(/\D/g, "").slice(0, 4);
      input.value = digits.length > 2 ? `${digits.slice(0, 2)}:${digits.slice(2)}` : digits;
    });

    input.addEventListener("blur", () => {
      const value = input.value.trim();
      if (value === "") return; // 빈 값은 "미기록"으로 그대로 둔다
      if (!isValidTime(value)) {
        input.value = input.dataset.original ?? "";
      }
    });
  });
}

/* 변경 버튼 — 등/하원 시각만 이 버튼으로 저장한다(태도·전달사항은 별도). 값이 바뀐 게 없으면 비활성.
   저장 대상은 일지 헤더의 in_time/out_time이고 세션의 입·퇴실 원본은 그대로 둔다 — 그래서 시각을
   고쳐도 실시간 모니터링의 입실/퇴실 상태는 바뀌지 않는다(퇴실 처리는 모니터링 화면 담당). */
function initTimeEditButtons() {
  document.querySelectorAll(".diary-time-edit-btn").forEach((button) => {
    if (button.disabled) return; // 미입실 행 — 저장할 세션이 없어 아예 잠겨 있다
    const box = button.closest(".time-boxes");
    const inputs = [...box.querySelectorAll(".time_input")];
    const startInput = box.querySelector(".start-time");
    const endInput = box.querySelector(".end-time");

    const syncButtonState = () => {
      button.disabled = inputs.every((input) => input.value.trim() === (input.dataset.original ?? ""));
    };

    inputs.forEach((input) => {
      input.addEventListener("input", syncButtonState);
      input.addEventListener("blur", syncButtonState);
    });
    syncButtonState();

    button.addEventListener("click", async () => {
      const invalid = inputs.find((input) => input.value.trim() !== "" && !isValidTime(input.value.trim()));
      if (invalid) {
        alert("시각은 00:00 형식으로 입력해 주세요.");
        invalid.focus();
        return;
      }

      button.disabled = true; // 응답 오기 전 중복 클릭 방지
      try {
        const saved = await postJson("/admin/growth/diary/time", {
          sessionId: Number(box.dataset.sessionId),
          inTime: startInput.value.trim(),
          outTime: endInput.value.trim(),
        });
        // 서버가 실제로 저장한 값으로 입력칸을 다시 맞춘다(형식 보정/미기록 처리 결과 반영)
        applySavedTime(startInput, saved.inTime);
        applySavedTime(endInput, saved.outTime);
        alert("변경되었습니다.");
      } catch (e) {
        alert(e.message);
        // 저장 실패면 입력값을 원래대로 되돌린다 — 화면만 바뀐 채 남아있으면 저장된 줄 착각한다
        inputs.forEach((input) => (input.value = input.dataset.original ?? ""));
      } finally {
        syncButtonState();
      }
    });
  });
}

function applySavedTime(input, value) {
  input.value = value ?? "";
  input.dataset.original = input.value;
}

function isValidTime(value) {
  const matched = /^(\d{2}):(\d{2})$/.exec(value);
  if (!matched) return false;
  return Number(matched[1]) <= 23 && Number(matched[2]) <= 59;
}

function initAttitudeCheckboxes() {
  document.querySelectorAll(".diary-checkbox").forEach((label) => {
    const checkbox = label.querySelector("input[type='checkbox']");
    checkbox.addEventListener("change", () => {
      label.classList.toggle("checked", checkbox.checked);
    });
  });
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (ch) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[ch]));
}
