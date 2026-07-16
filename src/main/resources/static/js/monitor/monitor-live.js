document.addEventListener("DOMContentLoaded", async () => {
  initDatePicker();
  initSlotPicker();
  await loadLiveView();
  connectFirestore();
  initReadingLogPanel();
  // Firestore 미설정/연결 끊김 대비 백업 polling — 경과시간/상태는 항상 서버(DB 시계) 계산값을
  // 그대로 신뢰한다(클라이언트에서 timestamp로 재계산하면 브라우저-DB 타임존 차이만큼 오차가 생김)
  setInterval(loadLiveView, 30000);
});

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TOKEN = "hohoedu-master-csrf-token";

const ATTITUDE_CODES = [
  { code: "GOOD_POSTURE", label: "바른 자세로 차분하게 정독했어요." },
  { code: "SELF_DIRECTED", label: "스스로 책 읽기를 끝까지 이어갔어요." },
  { code: "LOW_FOCUS", label: "집중력이 자주 흐트러졌어요." },
  { code: "RUSHED", label: "책장을 빠르게 넘기며 서둘러 읽었어요." },
  { code: "DISTRACTED", label: "산만한 모습을 보였어요." },
];

const HELP_NEEDED_CODES = [{ code: "ALONE_HARD", label: "혼자 읽기 어려워요!" }];

// 교시 마스터 데이터가 아직 없어 고정 목록으로 둔다 — entered_at 시(hour) 기준으로만 카드를 거른다
const TIME_SLOTS = [
  { key: "ALL", label: "타임 선택" },
  { key: "1", label: "1교시(14:00~15:00)", startHour: 14, endHour: 15 },
  { key: "2", label: "2교시(15:00~16:00)", startHour: 15, endHour: 16 },
  { key: "3", label: "3교시(16:00~17:00)", startHour: 16, endHour: 17 },
  { key: "4", label: "4교시(17:00~18:00)", startHour: 17, endHour: 18 },
];

const FILTERS = [
  { key: "ALL", label: "전체", countKey: "total", cls: "chip-all" },
  { key: "READING", label: "독서 중", countKey: "reading", cls: "chip-reading" },
  { key: "QUIZ_IN_PROGRESS", label: "문제 푸는 중", countKey: "quizInProgress", cls: "chip-quiz" },
  { key: "TIME_OVER", label: "시간 초과", countKey: "timeOver", cls: "chip-timeover" },
  { key: "RETRY_NEEDED", label: "재도전 필요", countKey: "retryNeeded", cls: "chip-retry" },
  { key: "LOG_MISSING", label: "독서일지 미등록", countKey: "readingLogMissing", cls: "chip-logmissing" },
];

const STATUS_BADGE = {
  READING: { text: "독서 중", icon: "fa-book-open", cls: "status-reading" },
  QUIZ_IN_PROGRESS: { text: "문제 푸는 중", icon: "fa-pen", cls: "status-quiz" },
  RETRY_NEEDED: { text: "재도전 필요", icon: "fa-triangle-exclamation", cls: "status-retry" },
  TIME_OVER: { text: "권장시간 초과", icon: "fa-hourglass-end", cls: "status-timeover" },
  EXITED: { text: "퇴실", icon: "fa-right-from-bracket", cls: "status-exited" },
};

let cards = [];
let activeFilter = "ALL";
let activeSlot = "ALL";
let selectedCard = null;
let firestoreUnsubscribe = null;

/* 공통 요청 헬퍼 */
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

function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function initDatePicker() {
  const input = document.getElementById("monitorDate");
  const display = document.getElementById("monitorDateDisplay");
  const trigger = document.querySelector(".monitor-date-trigger");

  input.value = todayStr();
  updateDateDisplay(input, display);

  trigger.addEventListener("click", () => input.showPicker());

  input.addEventListener("change", async () => {
    updateDateDisplay(input, display);
    await loadLiveView();
    connectFirestore(); // 날짜가 바뀌면 구독 쿼리도 그 날짜로 다시 건다
  });
}

function updateDateDisplay(input, display) {
  const [y, m, d] = input.value.split("-");
  display.textContent = `${y}. ${m}. ${d}`;
}

function selectedDate() {
  return document.getElementById("monitorDate").value || todayStr();
}

function initSlotPicker() {
  const select = document.getElementById("monitorSlot");
  select.innerHTML = "";
  TIME_SLOTS.forEach(({ key, label }) => {
    const option = document.createElement("option");
    option.value = key;
    option.textContent = label;
    select.appendChild(option);
  });
  select.addEventListener("change", () => {
    activeSlot = select.value;
    render();
  });
}

function matchesSlot(card) {
  if (activeSlot === "ALL") return true;
  const slot = TIME_SLOTS.find((s) => s.key === activeSlot);
  if (!slot || !card.enteredAt) return true;
  const hour = new Date(card.enteredAt).getHours();
  return hour >= slot.startHour && hour < slot.endHour;
}

/* 최초 진입(또는 날짜 변경) 시 1회 조회 — 이후 갱신은 Firestore 구독으로 받는다 */
async function loadLiveView() {
  try {
    const view = await getJson(`/admin/monitor/live?date=${selectedDate()}`);
    cards = view.cards ?? [];
    render();
  } catch (e) {
    console.error("실시간 모니터링 초기 조회 실패", e);
  }
}

/* Firestore 문서 변경 → 카드 배열에 반영 (없으면 추가, 있으면 갱신) */
function applyFirestoreCard(doc) {
  const idx = cards.findIndex((c) => c.sessionId === doc.sessionId);
  if (idx === -1) cards.push(doc);
  else cards[idx] = doc;
  render();
}

async function connectFirestore() {
  const fb = window.__monitorFirebase;
  if (!fb) return;
  if (firestoreUnsubscribe) {
    firestoreUnsubscribe();
    firestoreUnsubscribe = null;
  }

  try {
    const body = document.body;
    const firebaseConfig = {
      apiKey: body.dataset.firebaseApiKey,
      authDomain: body.dataset.firebaseAuthDomain,
      projectId: body.dataset.firebaseProjectId,
      storageBucket: body.dataset.firebaseStorageBucket,
      messagingSenderId: body.dataset.firebaseMessagingSenderId,
      appId: body.dataset.firebaseAppId,
    };
    if (!firebaseConfig.apiKey) return; // 설정 미교체 상태(REPLACE_ME) — 초기 목록만으로 동작

    const { token } = await postJson("/admin/monitor/firebase-token", {});

    const app = fb.initializeApp(firebaseConfig, "monitor-live");
    const auth = fb.getAuth(app);
    await fb.signInWithCustomToken(auth, token);

    const db = fb.getFirestore(app);
    const q = fb.query(fb.collection(db, "clinic_monitor"), fb.where("sessionDate", "==", selectedDate()));
    firestoreUnsubscribe = fb.onSnapshot(q, (snapshot) => {
      snapshot.docChanges().forEach((change) => {
        if (change.type === "removed") return;
        applyFirestoreCard(change.doc.data());
      });
    });
  } catch (e) {
    console.warn("Firestore 실시간 구독 연결 실패 — 초기 목록만 표시됩니다", e);
  }
}

/* ── 렌더링 ── */

function render() {
  const counts = computeCounts();
  renderFilters(counts);
  renderGrid();
}

function computeCounts() {
  const slotCards = cards.filter(matchesSlot);
  const counts = { total: slotCards.length, reading: 0, quizInProgress: 0, timeOver: 0, retryNeeded: 0, readingLogMissing: 0 };
  slotCards.forEach((c) => {
    if (c.cardStatus === "READING") counts.reading++;
    if (c.cardStatus === "QUIZ_IN_PROGRESS") counts.quizInProgress++;
    if (c.cardStatus === "TIME_OVER") counts.timeOver++;
    if (c.cardStatus === "RETRY_NEEDED") counts.retryNeeded++;
    if (!c.readingLogId) counts.readingLogMissing++;
  });
  return counts;
}

function renderFilters(counts) {
  const wrap = document.getElementById("monitorFilters");
  wrap.innerHTML = "";
  FILTERS.forEach(({ key, label, countKey, cls }) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `filter-chip ${cls}` + (key === activeFilter ? " active" : "");
    btn.innerHTML = `${label} <span class="filter-count">${counts[countKey]}</span>`;
    btn.addEventListener("click", () => {
      activeFilter = key;
      render();
    });
    wrap.appendChild(btn);
  });
}

function filteredCards() {
  const slotCards = cards.filter(matchesSlot);
  if (activeFilter === "ALL") return slotCards;
  if (activeFilter === "LOG_MISSING") return slotCards.filter((c) => !c.readingLogId);
  return slotCards.filter((c) => c.cardStatus === activeFilter);
}

function renderGrid() {
  const grid = document.getElementById("monitorGrid");
  grid.innerHTML = "";

  filteredCards().forEach((card) => {
    grid.appendChild(buildCardEl(card));
  });
}

function buildCardEl(card) {
  const el = document.createElement("div");
  const badge = STATUS_BADGE[card.cardStatus] ?? STATUS_BADGE.READING;
  el.className = "monitor-card " + badge.cls;

  const basicText = card.basicTotalCount ? `${card.basicCorrectCount ?? 0}/${card.basicTotalCount}` : "-";
  const advancedText = card.advancedTotalCount ? `${card.advancedCorrectCount ?? 0}/${card.advancedTotalCount}` : "-";
  const readingTimeText = card.elapsedMinutes != null ? `${card.elapsedMinutes}분` : "-";
  const recommendedText = card.readingTimeMinutes != null ? `권장 ${card.readingTimeMinutes}분` : "";
  const exited = card.sessionStatus === "EXITED";

  // 기본 문제풀이 통과/재도전 pill — 아직 안 풀었으면(basicCorrectCount null) 표시 없음
  const basicPill = card.basicStatus === "DONE"
    ? `<span class="stat-pill pill-pass">통과</span>`
    : card.basicCorrectCount != null ? `<span class="stat-pill pill-retry">재도전</span>` : "";
  // 심화 문제는 완독 개념이 없어 "제출 이력이 있으면" 완료 표시
  const advancedPill = card.advancedCorrectCount != null ? `<span class="stat-pill pill-pass">완료</span>` : "";

  // 아카이브 카드는 이번 범위에서 실제 발급 조건이 정해지지 않아, 기본 문제 통과 여부로만
  // 잠정 표시한다(발급 로직은 별도 작업으로 남겨둠)
  const archiveIssued = card.basicStatus === "DONE";
  const archiveBadge = archiveIssued
    ? `<span class="archive-chip archive-done">아카이브 카드 발급 완료</span>`
    : `<span class="archive-chip">아카이브 카드 발급</span>`;

  el.innerHTML = `
    ${card.helpNeeded ? `<div class="help-flag"><i class="fa-solid fa-seedling"></i> 혼자 읽기 어려워요!</div>` : ""}
    <div class="card-top">
      <span class="student-name">${card.studentName ?? ""}</span>
      <span class="status-badge"><i class="fa-solid ${badge.icon}"></i> ${badge.text}</span>
    </div>
    <div class="book-row">
      <img class="book-cover" src="${card.imageUrl || "/images/book-sample.png"}" alt="" onerror="this.src='/images/book-sample.png'" />
      <div class="book-info">
        <div class="book-title">${card.bookTitle ?? "추천 도서 없음"}</div>
        <div class="book-sub">${[card.publisher, card.author].filter(Boolean).join(" | ")}</div>
        ${archiveBadge}
      </div>
      <button type="button" class="log-open-btn${card.readingLogId != null ? " filled" : ""}" title="독서일지 등록"><i class="fa-regular fa-comment-dots"></i></button>
    </div>
    <div class="stat-row">
      <div class="stat-cell">
        <div class="stat-label">독서 시간</div>
        <div class="stat-value ${card.cardStatus === "TIME_OVER" ? "value-danger" : ""}">${readingTimeText}</div>
        <div class="stat-sub">${recommendedText}</div>
      </div>
      <div class="stat-cell">
        <div class="stat-label">기본 문제</div>
        <div class="stat-value">${basicText}</div>
        <div class="stat-sub">${basicPill}</div>
      </div>
      <div class="stat-cell">
        <div class="stat-label">심화 문제</div>
        <div class="stat-value">${advancedText}</div>
        <div class="stat-sub">${advancedPill}</div>
      </div>
      <div class="stat-cell">
        <div class="stat-label">획득 뱃지</div>
        <div class="stat-value badge-value">${card.badgeCount ? `<i class="fa-solid fa-shield-halved badge-icon"></i>` : "-"}</div>
        <div class="stat-sub">${card.latestBadgeName ?? ""}</div>
      </div>
    </div>
    <div class="card-bottom">
      <span class="entered-at">${formatTime(card.enteredAt)} 입실</span>
      <button type="button" class="btn ${exited ? "outline" : "primary"} small exit-btn" ${exited ? "disabled" : ""}>
        ${exited ? "퇴실 완료" : "퇴실 처리"}
      </button>
    </div>
  `;

  el.querySelector(".log-open-btn").addEventListener("click", () => openReadingLogPanel(card));
  const exitBtn = el.querySelector(".exit-btn");
  if (!exited) {
    exitBtn.addEventListener("click", async () => {
      try {
        await postJson("/admin/monitor/exit", { studentId: card.studentId });
        await loadLiveView();
      } catch (e) {
        alert(e.message);
      }
    });
  }

  return el;
}

function formatTime(isoString) {
  if (!isoString) return "-";
  const d = new Date(isoString);
  if (Number.isNaN(d.getTime())) return "-";
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/* ── 독서일지 패널 ── */

function initReadingLogPanel() {
  renderCheckboxGroup("attitudeCheckboxGroup", ATTITUDE_CODES, true);
  renderCheckboxGroup("helpNeededCheckboxGroup", HELP_NEEDED_CODES, false);

  document.getElementById("btnSaveReadingLog").addEventListener("click", saveReadingLog);
}

function renderCheckboxGroup(containerId, options, multiple) {
  const wrap = document.getElementById(containerId);
  wrap.innerHTML = "";
  options.forEach(({ code, label }) => {
    const item = document.createElement("label");
    item.className = "log-checkbox";
    item.dataset.code = code;
    item.innerHTML = `<input type="checkbox" ${multiple ? "" : 'name="helpNeeded"'} value="${code}" /> ${label}`;
    wrap.appendChild(item);
  });
}

function openReadingLogPanel(card) {
  selectedCard = card;
  document.getElementById("readingLogStudentName").textContent = `[${card.studentName ?? ""}]`;

  const attitudeCodes = (card.attitudeCodes ?? "").split(",").filter(Boolean);
  document.querySelectorAll("#attitudeCheckboxGroup input").forEach((cb) => {
    cb.checked = attitudeCodes.includes(cb.value);
  });
  document.querySelectorAll("#helpNeededCheckboxGroup input").forEach((cb) => {
    cb.checked = cb.value === card.helpNeeded;
  });
  document.getElementById("readingLogNote").value = card.note ?? "";

  document.getElementById("readingLogPanel").hidden = false;
}

async function saveReadingLog() {
  if (!selectedCard) return;

  const attitudeCodes = [...document.querySelectorAll("#attitudeCheckboxGroup input:checked")].map((cb) => cb.value);
  const checkedHelp = document.querySelector("#helpNeededCheckboxGroup input:checked");
  const note = document.getElementById("readingLogNote").value;

  try {
    await postJson("/admin/monitor/reading-log", {
      sessionId: selectedCard.sessionId,
      studentId: selectedCard.studentId,
      attitudeCodes,
      helpNeeded: checkedHelp ? checkedHelp.value : null,
      note,
    });
    await loadLiveView();
  } catch (e) {
    alert(e.message);
  }
}
