document.addEventListener("DOMContentLoaded", async () => {
  initDatePicker();
  initSlotPicker();
  await loadLiveView();
  connectFirestore();
  initReadingLogPanel();
  // Firestore 미설정/연결 끊김 대비 백업 polling — 경과시간/상태는 항상 서버(DB 시계) 계산값을
  // 그대로 신뢰한다(클라이언트에서 timestamp로 재계산하면 브라우저-DB 타임존 차이만큼 오차가 생김).
  // 실시간 반영은 Firestore push가 담당한다(입실/제출/퇴실 등 이벤트는 즉시 푸시됨). 폴링은 푸시가
  // 끊겼을 때를 위한 백업이라 30초로 충분하다. 시간이 흘러야만 바뀌는 값(독서 경과시간)은 폴링에
  // 기대지 않고 startElapsedTicker가 브라우저에서 1초마다 로컬로 올려준다 — 서버가 준 elapsedMinutes를
  // 기준점으로 삼아 그 위로 초를 더하는 방식이라 DB/브라우저 시계 차이 문제가 없다.
  setInterval(loadLiveView, 30000);
  startElapsedTicker();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TOKEN = "hohoedu-master-csrf-token";

// 독서태도 체크박스는 erp_bookstore_attitude_code(use_yn=1)를 /admin/monitor/live 응답의
// attitudeCodeOptions로 받아 렌더링한다 — 문구가 자주 바뀔 수 있어 하드코딩하지 않는다.

const HELP_NEEDED_CODES = [{ code: "ALONE_HARD", label: "혼자 읽기 어려워요!" }];

// 교시 마스터 데이터가 아직 없어 고정 목록으로 둔다 — 예약(erp_bookstore_clinic_reservation)의
// time_slot 값('1'~'4')과 그대로 매칭한다
const TIME_SLOTS = [
  { key: "ALL", label: "타임 선택" },
  { key: "1", label: "1교시(08:00~09:00)" },
  { key: "2", label: "2교시(09:00~10:00)" },
  { key: "3", label: "3교시(10:00~11:00)" },
  { key: "4", label: "4교시(11:00~12:00)" },
];

const FILTERS = [
  { key: "ALL", label: "전체", countKey: "total", cls: "chip-all" },
  { key: "NOT_ENTERED", label: "미입실", countKey: "notEntered", cls: "chip-not-entered" },
  { key: "READING", label: "독서 중", countKey: "reading", cls: "chip-reading" },
  { key: "QUIZ_IN_PROGRESS", label: "문제 푸는 중", countKey: "quizInProgress", cls: "chip-quiz" },
  { key: "RESULT_VIEWING", label: "결과 확인중", countKey: "resultViewing", cls: "chip-result" },
  { key: "TIME_OVER", label: "시간 초과", countKey: "timeOver", cls: "chip-timeover" },
  { key: "RETRY_NEEDED", label: "재도전 필요", countKey: "retryNeeded", cls: "chip-retry" },
  { key: "LOG_MISSING", label: "독서일지 미등록", countKey: "readingLogMissing", cls: "chip-logmissing" },
];

const STATUS_BADGE = {
  NOT_ENTERED: { text: "미입실", icon: "fa-clock", cls: "status-not-entered" },
  READING: { text: "독서 중", icon: "fa-book-open", cls: "status-reading" },
  QUIZ_IN_PROGRESS: { text: "문제 푸는 중", icon: "fa-pen", cls: "status-quiz" },
  RESULT_VIEWING: { text: "결과 확인중", icon: "fa-clipboard-check", cls: "status-result" },
  RETRY_NEEDED: { text: "재도전 필요", icon: "fa-triangle-exclamation", cls: "status-retry" },
  COMPLETED: { text: "완료", icon: "fa-circle-check", cls: "status-completed" },
  TIME_OVER: { text: "권장시간 초과", icon: "fa-hourglass-end", cls: "status-timeover" },
  EXITED: { text: "퇴실", icon: "fa-right-from-bracket", cls: "status-exited" },
};

let cards = [];
let activeFilter = "ALL";
let activeSlot = "ALL";
let selectedCard = null;
let firestoreUnsubscribe = null;
// 카드 캐러셀에서 학생이 지금 보고 있는 책 페이지 인덱스 — studentId 기준으로 렌더링을 넘나들며
// 유지된다(안 그러면 Firestore/폴링 갱신이 올 때마다 보고 있던 페이지가 리셋됨).
// 단, 책이 새로 추가되면(pages.length가 늘어나면) 그 새 책이 "지금 읽는 중"인 실시간 상태이므로
// 직원이 옛날 책 페이지를 보고 있던 중이었어도 새 책으로 강제 이동시킨다(2026-07-29).
let selectedBookPage = {};
let lastBookPageCount = {};

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

function todayStr(d = new Date()) {
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

/* 일지 헤더(diaryKey)는 채점 제출만 해도 시스템이 자동 생성한다(recordDiaryDetail→ensureDiary) —
   선생님이 실제로 태도 체크를 했는지는 diaryKey 존재가 아니라 attitudeCodes 값으로 판단해야 한다. */
function hasAttitude(card) {
  return !!card.attitudeCodes && card.attitudeCodes.length > 0;
}

function matchesSlot(card) {
  if (activeSlot === "ALL") return true;
  return card.timeSlot === activeSlot;
}

/* 최초 진입(또는 날짜 변경) 시 1회 조회 — 이후 갱신은 Firestore 구독으로 받는다 */
async function loadLiveView() {
  try {
    const view = await getJson(`/admin/monitor/live?date=${selectedDate()}`);
    cards = view.cards ?? [];
    const now = Date.now();
    cards.forEach((c) => (c._syncedAt = now)); // 경과시간 로컬 카운트업 기준점
    initAttitudeCheckboxesOnce(view.attitudeCodeOptions);
    render();
  } catch (e) {
    console.error("실시간 모니터링 초기 조회 실패", e);
  }
}

/* Firestore 문서 변경 → 카드 배열에 반영 (없으면 추가, 있으면 갱신).
   studentId로 매칭한다 — 미입실 카드는 sessionId가 없어서(null) sessionId로 매칭하면 미입실
   →입실 전환 시 기존 카드를 못 찾고 중복으로 추가돼 버린다. */
function applyFirestoreCard(doc) {
  doc._syncedAt = Date.now(); // 경과시간 로컬 카운트업 기준점 (이 카드가 서버 값으로 갱신된 시각)
  const idx = cards.findIndex((c) => c.studentId === doc.studentId);
  if (idx === -1) cards.push(doc);
  else cards[idx] = doc;
  render();
}

async function connectFirestore() {
  const fb = window.__monitorFirebase;
  if (!fb) {
    // window.__monitorFirebase는 head의 <script type="module">이 gstatic CDN에서 Firebase SDK를
    // import한 뒤 세팅한다 — 여기가 비어있으면 CDN 로드 실패(오프라인/차단망)로 실시간 구독 자체가
    // 시작되지 못하고 30초 폴백 폴링에만 의존하게 된다. 조용히 넘어가지 말고 원인을 남긴다.
    console.warn("[monitor] Firestore 구독 미시작 — Firebase SDK(window.__monitorFirebase) 로드 안 됨. "
      + "head의 gstatic CDN import 실패(오프라인/차단망) 가능성 → 30초 폴백 폴링에만 의존합니다");
    return;
  }
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
    if (!firebaseConfig.apiKey) {
      // body data-firebase-api-key가 비어있음 — Thymeleaf가 ${firebaseWebApiKey}를 못 채운 것.
      // 실행 프로파일에 application-secrets.yml이 안 물렸거나 FIREBASE_WEB_API_KEY 미설정. 조용히
      // 넘어가면 원인 없이 30초 폴링만 남으므로 명시적으로 남긴다.
      console.warn("[monitor] Firestore 구독 미시작 — body의 firebase apiKey가 비어있음. "
        + "실행 프로파일에 application-secrets.yml 미적용/FIREBASE_WEB_API_KEY 미설정 가능성 → 30초 폴백 폴링에만 의존합니다");
      return;
    }

    const { token } = await postJson("/admin/monitor/firebase-token", {});

    const app = fb.initializeApp(firebaseConfig, "monitor-live");
    const auth = fb.getAuth(app);
    await fb.signInWithCustomToken(auth, token);

    const db = fb.getFirestore(app);
    // 날짜 + 센터로 스코핑 — 다른 센터 학생의 푸시가 이 브라우저로 새어들어오지 않게 한다.
    // (두 개의 등가 필터라 Firestore 복합 인덱스가 필요할 수 있음 — 없으면 구독 에러 콜백에
    //  인덱스 생성 URL이 찍히므로 그 링크로 한 번 만들어주면 된다.)
    const centerCode = document.body.dataset.centerCode;
    const q = fb.query(
      fb.collection(db, "clinic_monitor"),
      fb.where("sessionDate", "==", selectedDate()),
      fb.where("centerCode", "==", centerCode)
    );
    firestoreUnsubscribe = fb.onSnapshot(
      q,
      (snapshot) => {
        console.info(`[monitor] Firestore 스냅샷 수신 (${new Date().toLocaleTimeString()}): 변경 ${snapshot.docChanges().length}건`);
        snapshot.docChanges().forEach((change) => {
          if (change.type === "removed") return;
          applyFirestoreCard(change.doc.data());
        });
      },
      (err) => console.warn("[monitor] Firestore 구독 중 에러 — 이후 갱신은 30초 폴백 폴링에만 의존합니다", err)
    );
    console.info("[monitor] Firestore 실시간 구독 연결 성공 — sessionDate =", selectedDate());
  } catch (e) {
    console.warn("Firestore 실시간 구독 연결 실패 — 초기 목록만 표시됩니다", e);
  }
}

/* 서버가 준 경과분(baseMinutes)에, 그 값을 받은 뒤(syncedAt) 브라우저에서 흐른 시간을 더해
   "지금 경과분"을 만든다. 절대시각(recommendedAt)을 브라우저 시계로 다시 재지 않으므로 DB-브라우저
   타임존 차이 문제가 없다 — 서버가 계산한 값을 기준점으로만 쓰고 그 위로 초를 올린다. */
function liveElapsed(baseMinutes, syncedAt) {
  if (baseMinutes == null) return null;
  if (!syncedAt) return baseMinutes;
  return baseMinutes + Math.floor((Date.now() - syncedAt) / 60000);
}

/* 독서 경과시간은 시간이 흐르는 것만으로 바뀌는데 서버 이벤트가 없어 푸시가 안 온다 — 폴링(30초)에만
   기대면 최대 30초 늦으므로, 화면을 주기적으로 다시 그려 경과분을 로컬로 올려준다. 표시는 '분' 단위라
   15초 주기면 분 넘어가는 순간을 충분히 촘촘히 잡는다(초당 렌더는 불필요한 DOM 갱신). */
function startElapsedTicker() {
  setInterval(render, 15000);
}

/* ── 렌더링 ── */

function render() {
  const counts = computeCounts();
  renderFilters(counts);
  renderGrid();
}

function computeCounts() {
  const slotCards = cards.filter(matchesSlot);
  const counts = { total: slotCards.length, notEntered: 0, reading: 0, quizInProgress: 0, resultViewing: 0, timeOver: 0, retryNeeded: 0, readingLogMissing: 0 };
  slotCards.forEach((c) => {
    if (c.cardStatus === "NOT_ENTERED") counts.notEntered++;
    if (c.cardStatus === "READING") counts.reading++;
    if (c.cardStatus === "QUIZ_IN_PROGRESS") counts.quizInProgress++;
    if (c.cardStatus === "RESULT_VIEWING") counts.resultViewing++;
    if (c.cardStatus === "TIME_OVER") counts.timeOver++;
    if (c.cardStatus === "RETRY_NEEDED") counts.retryNeeded++;
    if (!hasAttitude(c) && c.cardStatus !== "NOT_ENTERED") counts.readingLogMissing++;
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
  let result;
  if (activeFilter === "ALL") result = slotCards;
  else if (activeFilter === "LOG_MISSING") result = slotCards.filter((c) => !hasAttitude(c));
  else result = slotCards.filter((c) => c.cardStatus === activeFilter);
  return sortedCards(result);
}

/* 카드 정렬 규칙: 입실 중(활성) 카드는 입실 시각순으로 맨 위 → 미입실 카드는 예약 타임순으로
   그 아래 → 퇴실 카드는 퇴실 시각순으로 맨 아래(흐리게 표시는 status-exited CSS가 이미 처리). */
function sortedCards(list) {
  const rank = (c) => {
    if (c.sessionStatus === "EXITED") return 2;
    if (c.cardStatus === "NOT_ENTERED") return 1;
    return 0;
  };
  return [...list].sort((a, b) => {
    const ra = rank(a);
    const rb = rank(b);
    if (ra !== rb) return ra - rb;
    if (ra === 0) return new Date(a.enteredAt) - new Date(b.enteredAt);
    if (ra === 2) return new Date(a.exitedAt) - new Date(b.exitedAt);
    if (a.timeSlot !== b.timeSlot) return (a.timeSlot ?? "").localeCompare(b.timeSlot ?? "");
    return (a.studentName ?? "").localeCompare(b.studentName ?? "", "ko");
  });
}

/* 그날 예약된 학생 전체를 입실/미입실/퇴실 3개 영역으로 나눠서 보여준다(2026-07-29) — 타임별로
   순차 노출하던 방식보다 지금 누가 어느 상태인지 한눈에 파악하기 쉽다. filteredCards()가 이미
   같은 기준(rank)으로 정렬해 내려주므로 여기서는 그 순서 그대로 그룹만 나눈다. */
function renderGrid() {
  const grouped = { entered: [], notEntered: [], exited: [] };
  filteredCards().forEach((card) => {
    if (card.sessionStatus === "EXITED") grouped.exited.push(card);
    else if (card.cardStatus === "NOT_ENTERED") grouped.notEntered.push(card);
    else grouped.entered.push(card);
  });

  renderSection("enteredGrid", "enteredCount", grouped.entered, "지금 입실한 학생이 없어요.");
  renderSection("notEnteredGrid", "notEnteredCount", grouped.notEntered, "미입실 학생이 없어요.");
  renderSection("exitedGrid", "exitedCount", grouped.exited, "퇴실한 학생이 없어요.");
}

function renderSection(gridId, countId, list, emptyText) {
  const grid = document.getElementById(gridId);
  document.getElementById(countId).textContent = String(list.length);
  grid.innerHTML = "";

  if (list.length === 0) {
    const empty = document.createElement("div");
    empty.className = "monitor-section-empty";
    empty.textContent = emptyText;
    grid.appendChild(empty);
    return;
  }
  list.forEach((card) => grid.appendChild(buildCardEl(card)));
}

/* 카드 캐러셀의 페이지 목록 — books가 있으면 그대로, 없으면(구버전 Firestore 문서·미입실 등)
   카드 root의 단일 책 필드를 페이지 1개짜리 배열로 흉내내서 기존 카드도 그대로 렌더링되게 한다 */
function bookPages(card) {
  if (card.books && card.books.length > 0) return card.books;
  return [{
    bookTitle: card.bookTitle,
    author: card.author,
    publisher: card.publisher,
    imageUrl: card.imageUrl,
    readingTimeMinutes: card.readingTimeMinutes,
    elapsedMinutes: card.elapsedMinutes,
    basicCorrectCount: card.basicCorrectCount,
    basicTotalCount: card.basicTotalCount,
    basicStatus: card.basicStatus,
    advancedCorrectCount: card.advancedCorrectCount,
    advancedTotalCount: card.advancedTotalCount,
    badgeCount: card.badgeCount,
    latestBadgeName: card.latestBadgeName,
  }];
}

/* 학생이 보고 있던 페이지를 기억한다 — 지정 안 돼있거나 책이 새로 늘어났으면(2번째 책을 막
   추천받은 경우 등) 최신 책으로 이동시킨다. 책 수가 그대로면(단순 데이터 갱신) 보던 페이지를 유지한다. */
function currentPageIndex(card, pages) {
  const prevCount = lastBookPageCount[card.studentId] ?? 0;
  if (selectedBookPage[card.studentId] == null || pages.length > prevCount) {
    selectedBookPage[card.studentId] = pages.length - 1;
  }
  lastBookPageCount[card.studentId] = pages.length;
  return Math.min(selectedBookPage[card.studentId], pages.length - 1);
}

function buildCardEl(card) {
  const el = document.createElement("div");
  const badge = STATUS_BADGE[card.cardStatus] ?? STATUS_BADGE.READING;
  el.className = "monitor-card " + badge.cls;

  const notEntered = card.cardStatus === "NOT_ENTERED";
  const exited = card.sessionStatus === "EXITED";
  const pages = bookPages(card);
  const pageIndex = currentPageIndex(card, pages);
  const page = pages[pageIndex];

  el.innerHTML = `
    ${card.helpNeeded ? `<div class="help-flag"><i class="fa-solid fa-seedling"></i> 혼자 읽기 어려워요!</div>` : ""}
    <div class="card-top">
      <span class="student-name">${card.studentName ?? ""}${card.helpNeeded ? `<i class="fa-solid fa-seedling name-flair"></i>` : ""}</span>
      <span class="status-badge"><i class="fa-solid ${badge.icon}"></i> ${badge.text}</span>
    </div>
    <div class="book-row"></div>
    <div class="stat-row"></div>
    <div class="card-bottom">
      <span class="entered-at">${notEntered ? "미입실" : `${formatTime(card.enteredAt)} 입실`}</span>
      ${notEntered ? "" : `
      <button type="button" class="btn outline small exit-btn" ${exited ? "disabled" : ""}>
        ${exited ? "퇴실 완료" : "퇴실 처리"}
      </button>`}
    </div>
  `;

  renderBookRow(el, card, pages, pageIndex);
  renderStatRow(el, card, page);

  if (!notEntered) {
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
  }

  return el;
}

/* book-row(표지/제목/아카이브 배지/독서일지 버튼) + 페이지가 여러 장이면 하단에 점 페이지네이션 */
function renderBookRow(el, card, pages, pageIndex) {
  const page = pages[pageIndex];
  const notEntered = card.cardStatus === "NOT_ENTERED";
  const archiveIssued = page.basicStatus === "DONE";
  const archiveBadge = archiveIssued
    ? `<span class="archive-chip archive-done">아카이브 카드 발급 완료</span>`
    : `<span class="archive-chip">아카이브 카드 발급</span>`;

  const bookRow = el.querySelector(".book-row");
  bookRow.innerHTML = `
    <img class="book-cover" src="${page.imageUrl || "/images/book-sample.png"}" alt="" onerror="this.src='/images/book-sample.png'" />
    <div class="book-info">
      <div class="book-title">${page.bookTitle ?? "추천 도서 없음"}</div>
      <div class="book-sub">${[page.publisher, page.author].filter(Boolean).join(" | ")}</div>
      <div class="book-info-bottom">
        ${archiveBadge}
        ${pages.length > 1 ? `<div class="book-dots">${pages.map((_, i) => `<span class="dot${i === pageIndex ? " active" : ""}" data-idx="${i}"></span>`).join("")}</div>` : ""}
      </div>
    </div>
    ${notEntered ? "" : `<button type="button" class="log-open-btn${hasAttitude(card) ? " filled" : ""}" title="독서일지 등록"><i class="fa-regular fa-comment-dots"></i></button>`}
  `;

  if (!notEntered) {
    bookRow.querySelector(".log-open-btn").addEventListener("click", () => toggleReadingLogPanel(card));
  }
  bookRow.querySelectorAll(".dot").forEach((dot) => {
    dot.addEventListener("click", () => {
      selectedBookPage[card.studentId] = Number(dot.dataset.idx);
      render();
    });
  });
}

/* stat-row — 독서시간/기본문제/심화문제/획득뱃지 전부 선택된 책 페이지(content_id) 기준이다.
   뱃지도 예전엔 학생 전체 합산이라 A책 카드에 B책 뱃지가 같이 보이는 문제가 있었다(2026-07-29 수정) */
function renderStatRow(el, card, page) {
  const basicText = page.basicTotalCount ? `${page.basicCorrectCount ?? 0}/${page.basicTotalCount}` : "-";
  const advancedText = page.advancedTotalCount ? `${page.advancedCorrectCount ?? 0}/${page.advancedTotalCount}` : "-";
  // 문제풀이를 시작한 뒤로는 독서 시간이 더 흐르면 안 된다(서버가 이미 그 시점 값으로 얼려서 내려줌) —
  // READING/TIME_OVER(아직 순수 독서 중)일 때만 로컬로 초를 더 올려서 보여준다.
  const stillReading = card.cardStatus === "READING" || card.cardStatus === "TIME_OVER";
  const elapsed = stillReading ? liveElapsed(page.elapsedMinutes, card._syncedAt) : page.elapsedMinutes;
  const readingTimeText = elapsed != null ? `${elapsed}분` : "-";
  const recommendedText = page.readingTimeMinutes != null ? `권장 ${page.readingTimeMinutes}분` : "";
  const isOverTime = page.readingTimeMinutes != null && elapsed != null && elapsed > page.readingTimeMinutes;

  const basicPill = page.basicStatus === "DONE"
    ? `<span class="stat-pill pill-pass">통과</span>`
    : page.basicCorrectCount != null ? `<span class="stat-pill pill-retry">재도전</span>` : "";
  const advancedPill = page.advancedCorrectCount != null ? `<span class="stat-pill pill-pass">완료</span>` : "";

  el.querySelector(".stat-row").innerHTML = `
    <div class="stat-cell">
      <div class="stat-label">독서 시간</div>
      <div class="stat-value ${isOverTime ? "value-danger" : ""}">${readingTimeText}</div>
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
      <div class="stat-value badge-value">${page.badgeCount ? `<i class="fa-solid fa-shield-halved badge-icon"></i>` : "-"}</div>
      <div class="stat-sub">${page.latestBadgeName ?? ""}</div>
    </div>
  `;
}

function formatTime(isoString) {
  if (!isoString) return "-";
  const d = new Date(isoString);
  if (Number.isNaN(d.getTime())) return "-";
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/* ── 독서일지 패널 ── */

function initReadingLogPanel() {
  renderCheckboxGroup("helpNeededCheckboxGroup", HELP_NEEDED_CODES, false);

  document.getElementById("btnSaveReadingLog").addEventListener("click", saveReadingLog);
  document.getElementById("btnCloseReadingLog").addEventListener("click", closeReadingLogPanel);
}

let attitudeCheckboxesBuilt = false;

/* 태도 체크박스는 DB(erp_bookstore_attitude_code)에서 내려온 목록으로 딱 한 번만 그린다 — loadLiveView가
   30초마다 다시 불려도 여기서 매번 다시 그리면 직원이 체크 중이던 항목이 초기화돼 버린다. */
function initAttitudeCheckboxesOnce(options) {
  if (attitudeCheckboxesBuilt || !options) return;
  renderCheckboxGroup(
    "attitudeCheckboxGroup",
    options.map(({ attitudeCode, attitudeName }) => ({ code: attitudeCode, label: attitudeName })),
    true
  );
  attitudeCheckboxesBuilt = true;
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

/* 같은 카드의 아이콘을 다시 누르면 닫고, 다른 카드면 그 카드로 전환해서 연다 */
function toggleReadingLogPanel(card) {
  const panel = document.getElementById("readingLogPanel");
  const alreadyOpenForThisCard = panel.classList.contains("open") && selectedCard?.sessionId === card.sessionId;
  if (alreadyOpenForThisCard) {
    closeReadingLogPanel();
  } else {
    openReadingLogPanel(card);
  }
}

function openReadingLogPanel(card) {
  selectedCard = card;
  document.getElementById("readingLogStudentName").textContent = `[${card.studentName ?? ""}]`;

  const attitudeCodes = (card.attitudeCodes ?? "").split(",").filter(Boolean);
  document.querySelectorAll("#attitudeCheckboxGroup input").forEach((cb) => {
    cb.checked = attitudeCodes.includes(cb.value);
  });
  // 도움 필요는 선택지가 하나뿐이라 서버에서 boolean으로 내려온다
  document.querySelectorAll("#helpNeededCheckboxGroup input").forEach((cb) => {
    cb.checked = !!card.helpNeeded;
  });
  document.getElementById("readingLogNote").value = card.memo ?? "";

  document.getElementById("readingLogPanel").classList.add("open");
}

function closeReadingLogPanel() {
  selectedCard = null;
  document.getElementById("readingLogPanel").classList.remove("open");
}

async function saveReadingLog() {
  if (!selectedCard) return;

  const attitudeCodes = [...document.querySelectorAll("#attitudeCheckboxGroup input:checked")].map((cb) => cb.value);
  const helpNeeded = !!document.querySelector("#helpNeededCheckboxGroup input:checked");
  const memo = document.getElementById("readingLogNote").value;

  try {
    await postJson("/admin/monitor/diary", {
      sessionId: selectedCard.sessionId,
      studentId: selectedCard.studentId,
      attitudeCodes,
      helpNeeded,
      memo,
    });
    await loadLiveView();
  } catch (e) {
    alert(e.message);
  }
}
