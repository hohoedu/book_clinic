document.addEventListener("DOMContentLoaded", async () => {
  initDatePicker();
  initSlotPicker();
  await loadLiveView();
  // Firestore 연결 여부가 확인되기 전까지는 안전하게 30초(빠른 주기)로 시작한다 — connectFirestore()가
  // 실제로 스냅샷을 받으면 onFirestoreHealthy()가 5분 주기로 늦춰준다(2026-08-07, N+1 제거 이후 이
  // 폴링 자체가 평소(Firestore 정상)에도 상시로 돌아가는 부담이라 줄였다). 완전히 끄지 않고 5분
  // 안전망을 남긴 이유는, Firestore가 에러 콜백 없이 조용히 끊기는 경우까지 대비하기 위함이다 —
  // 폴링을 아예 껐다가 그런 경우가 나면 화면이 영원히 멈춘 채로 아무도 모르게 방치된다.
  schedulePoll(POLL_FAST_MS);
  connectFirestore();
  initReadingLogPanel();
  // 시간이 흘러야만 바뀌는 값(독서 경과시간)은 폴링에 기대지 않고 startElapsedTicker가 브라우저에서
  // 1초마다 로컬로 올려준다 — 서버가 준 elapsedMinutes를 기준점으로 삼아 그 위로 초를 더하는 방식이라
  // DB/브라우저 시계 차이 문제가 없다.
  startElapsedTicker();
});

const POLL_FAST_MS = 30000;    // Firestore 연결이 불확실하거나 끊겼을 때
const POLL_SLOW_MS = 300000;   // Firestore가 정상 동작 중인 것으로 확인됐을 때의 안전망 주기(5분)

let pollTimer = null;

function schedulePoll(intervalMs) {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(loadLiveView, intervalMs);
}

/** 스냅샷을 실제로 받았다는 것 자체가 연결이 살아있다는 증거 — 받을 때마다 안전망 타이머를 다시 늦춘다 */
function onFirestoreHealthy() {
  schedulePoll(POLL_SLOW_MS);
}

function onFirestoreUnhealthy(reason) {
  console.warn(`[monitor] Firestore 비정상(${reason}) — 폴링 주기를 30초로 되돌립니다`);
  schedulePoll(POLL_FAST_MS);
}

const CSRF_HEADER = "X-XSRF-TOKEN";
// 서버가 세션마다 다른 값을 XSRF-TOKEN 쿠키로 내려준다(CookieCsrfTokenRepository, 2026-07-31) —
// 예전처럼 고정 문자열을 하드코딩하지 않고 매 요청마다 쿠키에서 읽는다.
function getCsrfToken() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}

// 독서태도 체크박스는 erp_bookstore_attitude_code(use_yn=1)를 /admin/monitor/live 응답의
// attitudeCodeOptions로 받아 렌더링한다 — 문구가 자주 바뀔 수 있어 하드코딩하지 않는다.

const HELP_NEEDED_CODES = [{ code: "ALONE_HARD", label: "혼자 읽기 어려워요!" }];

const FILTERS = [
  { key: "ALL", label: "전체", countKey: "total", cls: "chip-all" },
  { key: "NOT_ENTERED", label: "미입실", countKey: "notEntered", cls: "chip-not-entered" },
  { key: "READING", label: "독서 중", countKey: "reading", cls: "chip-reading" },
  { key: "QUIZ_IN_PROGRESS", label: "문제 푸는 중", countKey: "quizInProgress", cls: "chip-quiz" },
  { key: "TIME_OVER", label: "시간 초과", countKey: "timeOver", cls: "chip-timeover" },
  { key: "RETRY_NEEDED", label: "재도전 필요", countKey: "retryNeeded", cls: "chip-retry" },
  { key: "COMPLETED", label: "완료", countKey: "completed", cls: "chip-completed" },
  { key: "LOG_MISSING", label: "독서일지 미등록", countKey: "readingLogMissing", cls: "chip-logmissing" },
];

// 2026-08-28 확정 6종. 결과류(문제풀이 후)는 grade/심화 결과에 따라 라벨이 갈린다.
const RESULT_STATUSES = ["KING", "FRIEND", "RETRY_NEEDED", "ADV_DONE", "ADV_KING"];
const STATUS_BADGE = {
  NOT_ENTERED: { text: "미입실", icon: "fa-clock", cls: "status-not-entered" },
  READING: { text: "독서 중", icon: "fa-book-open", cls: "status-reading" },
  QUIZ_IN_PROGRESS: { text: "문제 푸는 중", icon: "fa-pen", cls: "status-quiz" },
  TIME_OVER: { text: "시간초과", icon: "fa-hourglass-end", cls: "status-timeover" },
  KING: { text: "독서왕", icon: "fa-crown", cls: "status-completed" },
  FRIEND: { text: "독서친구", icon: "fa-circle-check", cls: "status-completed" },
  RETRY_NEEDED: { text: "재도전 필요", icon: "fa-triangle-exclamation", cls: "status-retry" },
  ADV_DONE: { text: "심화완료", icon: "fa-book-open-reader", cls: "status-completed" },
  ADV_KING: { text: "심화왕", icon: "fa-crown", cls: "status-completed" },
  EXITED: { text: "퇴실", icon: "fa-right-from-bracket", cls: "status-exited" },
};

/* "문제 푸는 중" 배지에 회차/심화를 덧붙인다 — 기본 회차는 (지금까지 제출한 회차 + 1), 심화면 "심화" (2026-08-28) */
function statusBadgeFor(card) {
  const badge = STATUS_BADGE[card.cardStatus] ?? STATUS_BADGE.READING;
  if (card.cardStatus !== "QUIZ_IN_PROGRESS") return badge;
  const suffix = card.quizQlevel === "02"
    ? "심화"
    : `${(card.basicAttemptRounds ?? 0) + 1}회차`;
  return { ...badge, text: `${badge.text} (${suffix})` };
}

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
    headers: { "Content-Type": "application/json", [CSRF_HEADER]: getCsrfToken() },
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
  select.innerHTML = `<option value="ALL">전체</option>`;
  select.addEventListener("change", () => {
    activeSlot = select.value;
    render();
  });
}

/* 회차(교시) 마스터 데이터가 없다 — 센터마다 회차 수·시간이 달라(2026-08-18 신규 예약 스키마)
   고정 목록을 둘 수 없으므로, 그날 실제로 불러온 카드들의 timeSlot(회차 번호) 값에서
   드롭다운을 그때그때 만든다. 선택돼 있던 값이 새 목록에도 있으면 그대로 유지한다. */
function refreshSlotOptions() {
  const select = document.getElementById("monitorSlot");
  const current = select.value || "ALL";
  const slots = [...new Set(cards.map((c) => c.timeSlot).filter(Boolean))].sort(
    (a, b) => Number(a) - Number(b)
  );

  select.innerHTML = `<option value="ALL">전체</option>`;
  slots.forEach((seq) => {
    const option = document.createElement("option");
    option.value = seq;
    option.textContent = `${seq}회차`;
    select.appendChild(option);
  });

  select.value = current === "ALL" || slots.includes(current) ? current : "ALL";
  activeSlot = select.value;
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
    refreshSlotOptions();
    render();
  } catch (e) {
    console.error("실시간 모니터링 초기 조회 실패", e);
  }
}

/* Firestore 문서 변경 → 카드 배열에 반영 (없으면 추가, 있으면 갱신).
   studentId + timeSlot으로 매칭한다 — 미입실 카드는 sessionId가 없어서(null) sessionId로
   매칭하면 미입실→입실 전환 시 기존 카드를 못 찾고 중복으로 추가돼 버린다(그래서 studentId를
   기준으로 삼는다). timeSlot을 함께 봐야 하는 이유는 한 학생이 같은 날 두 타임을 예약하면
   카드가 2건 생기는데(findReservationCards가 예약 단위로 행을 뽑음), studentId만 보면
   findIndex가 항상 첫 번째 카드만 찾아 두 번째 타임슬롯 카드가 영영 갱신되지 않기 때문이다.
   timeSlot은 예약 자체의 값이라 입실 전후로 바뀌지 않으므로 미입실→입실 매칭에는 영향 없다. */
function applyFirestoreCard(doc) {
  doc._syncedAt = Date.now(); // 경과시간 로컬 카운트업 기준점 (이 카드가 서버 값으로 갱신된 시각)
  const idx = cards.findIndex((c) => c.studentId === doc.studentId && c.timeSlot === doc.timeSlot);
  // 예약 취소 동기화(MonitorService.syncCanceledReservationCard)는 cardStatus를 "CANCELED"로
  // 강제 표시해서 보낸다 — Firestore 문서 삭제(type="removed")는 프론트가 안 듣고 있으므로
  // (connectFirestore 참고), 삭제 대신 이 값을 신호로 받아 카드를 직접 목록에서 제거한다(2026-08-20).
  if (doc.cardStatus === "CANCELED") {
    if (idx !== -1) cards.splice(idx, 1);
    refreshSlotOptions();
    render();
    return;
  }
  if (idx === -1) cards.push(doc);
  else cards[idx] = doc;
  refreshSlotOptions();
  render();
}

async function connectFirestore() {
  // 구독을 새로 걸 때마다 Firestore가 "지금까지 저장된 문서"를 최초 1회 무조건 통째로 내려준다
  // (실제 변경이 아니어도 발생하는 정상 동작). 그 문서의 elapsedMinutes는 마지막 이벤트(입실/퇴실/
  // 문제풀이 시작 등) 시점에 멈춰있는 스냅샷이라, loadLiveView()가 방금 받아온 신선한 값을 그
  // 오래된 값으로 덮어써버린다(새로고침 직후 잠깐 0분으로 보였다가 30초 폴링 후 되돌아오는 원인이었음,
  // 2026-07-30). 구독 직후 오는 최초 스냅샷은 건너뛰고, 그 이후의 진짜 변경분부터 반영한다.
  let skippedInitialSnapshot = false;
  const fb = window.__monitorFirebase;
  if (!fb) {
    // window.__monitorFirebase는 head의 <script type="module">이 gstatic CDN에서 Firebase SDK를
    // import한 뒤 세팅한다 — 여기가 비어있으면 CDN 로드 실패(오프라인/차단망)로 실시간 구독 자체가
    // 시작되지 못하고 30초 폴백 폴링에만 의존하게 된다. 조용히 넘어가지 말고 원인을 남긴다.
    console.warn("[monitor] Firestore 구독 미시작 — Firebase SDK(window.__monitorFirebase) 로드 안 됨. "
      + "head의 gstatic CDN import 실패(오프라인/차단망) 가능성 → 30초 폴백 폴링에만 의존합니다");
    onFirestoreUnhealthy("SDK 미로드");
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
      onFirestoreUnhealthy("설정 없음");
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
        // 스냅샷을 실제로 받았다는 것 자체가 연결이 살아있다는 증거 — 최초 스냅샷이든 아니든
        // 매번 안전망 폴링 주기를 5분으로 늦춘다(2026-08-07).
        onFirestoreHealthy();
        console.info(`[monitor] Firestore 스냅샷 수신 (${new Date().toLocaleTimeString()}): 변경 ${snapshot.docChanges().length}건`);
        if (!skippedInitialSnapshot) {
          // 최초 스냅샷은 구독 시점에 이미 저장돼 있던(오래됐을 수 있는) 문서 전체라 무시한다 —
          // 방금 loadLiveView()가 받아온 값이 이미 최신이다.
          skippedInitialSnapshot = true;
          return;
        }
        snapshot.docChanges().forEach((change) => {
          if (change.type === "removed") return;
          applyFirestoreCard(change.doc.data());
        });
      },
      (err) => {
        console.warn("[monitor] Firestore 구독 중 에러 — 이후 갱신은 30초 폴백 폴링에만 의존합니다", err);
        onFirestoreUnhealthy("구독 에러");
      }
    );
    console.info("[monitor] Firestore 실시간 구독 연결 성공 — sessionDate =", selectedDate());
  } catch (e) {
    console.warn("Firestore 실시간 구독 연결 실패 — 초기 목록만 표시됩니다", e);
    onFirestoreUnhealthy("연결 실패");
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
  const counts = { total: slotCards.length, notEntered: 0, reading: 0, quizInProgress: 0, timeOver: 0, retryNeeded: 0, completed: 0, readingLogMissing: 0 };
  slotCards.forEach((c) => {
    if (c.cardStatus === "NOT_ENTERED") counts.notEntered++;
    if (c.cardStatus === "READING") counts.reading++;
    if (c.cardStatus === "QUIZ_IN_PROGRESS") counts.quizInProgress++;
    if (c.cardStatus === "TIME_OVER") counts.timeOver++;
    if (c.cardStatus === "RETRY_NEEDED") counts.retryNeeded++;
    if (["KING", "FRIEND", "ADV_DONE", "ADV_KING"].includes(c.cardStatus)) counts.completed++;
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
  else if (activeFilter === "COMPLETED") result = slotCards.filter((c) => ["KING", "FRIEND", "ADV_DONE", "ADV_KING"].includes(c.cardStatus));
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
    recommendId: card.recommendId,
    contentId: card.contentId,
    bookTitle: card.bookTitle,
    author: card.author,
    publisher: card.publisher,
    imageUrl: card.imageUrl,
    readingTimeMinutes: card.readingTimeMinutes,
    elapsedMinutes: card.elapsedMinutes,
    basicCorrectCount: card.basicCorrectCount,
    basicFinalCorrectCount: card.basicFinalCorrectCount,
    basicTotalCount: card.basicTotalCount,
    basicStatus: card.basicStatus,
    basicGrade: card.basicGrade,
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
  const badge = statusBadgeFor(card);
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
    <div class="book-dots-row"></div>
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

/* book-row(표지/책 정보/독서일지 버튼) + 여러 장 추천 시 그 아래(book-dots-row)에 점 페이지네이션.
   표지·정보는 세로 가운데 정렬, 페이지네이션은 그와 무관하게 카드 하단에 고정한다(2026-08-26). */
function renderBookRow(el, card, pages, pageIndex) {
  const page = pages[pageIndex];
  const notEntered = card.cardStatus === "NOT_ENTERED";

  const bookRow = el.querySelector(".book-row");
  bookRow.innerHTML = `
    <img class="book-cover" src="${page.imageUrl || "/images/book-sample.png"}" alt="" onerror="this.src='/images/book-sample.png'" />
    <div class="book-info">
      <div class="book-title">${page.bookTitle ?? "추천 도서 없음"}</div>
      <div class="book-sub">${[page.publisher, page.author].filter(Boolean).join(" | ")}</div>
    </div>
    <div class="book-actions">
      ${notEntered ? "" : `<button type="button" class="log-open-btn${hasAttitude(card) ? " filled" : ""}" title="독서일지 등록"><i class="fa-regular fa-comment-dots"></i></button>`}
    </div>
  `;

  if (!notEntered) {
    bookRow.querySelector(".log-open-btn").addEventListener("click", () => toggleReadingLogPanel(card));
  }

  const dotsRow = el.querySelector(".book-dots-row");
  dotsRow.innerHTML = pages.length > 1
    ? `<div class="book-dots">${pages.map((_, i) => `<span class="dot${i === pageIndex ? " active" : ""}" data-idx="${i}"></span>`).join("")}</div>`
    : "";
  dotsRow.querySelectorAll(".dot").forEach((dot) => {
    dot.addEventListener("click", () => {
      selectedBookPage[card.studentId] = Number(dot.dataset.idx);
      render();
    });
  });
}

/* 지울 문제풀이 기록이 실제로 있는 책 페이지인지 — 아직 한 번도 안 푼 책엔 삭제 버튼을 안 띄운다.
   recommendId는 구버전 Firestore 문서(캐러셀 이전 형식)엔 없을 수 있어 함께 확인한다. */
function hasQuizRecord(page) {
  if (page.recommendId == null) return false;
  return page.basicCorrectCount != null || page.advancedCorrectCount != null;
}

/* 문제풀이 기록 삭제 — 학생이 "지워주세요"라고 할 때 직원이 실행한다. 되돌릴 수 없고, 그 책에서
   딴 뱃지/카드는 물론 뒤에 추천받은 책까지 취소되므로 무엇이 사라지는지 확인 문구에 그대로 적는다. */
async function resetQuiz(card, page, laterCount) {
  const bookTitle = page.bookTitle ?? "이 책";
  const ok = confirm(
    `[${card.studentName ?? ""}] ${bookTitle}\n\n` +
    "이 책의 문제풀이 기록을 삭제합니다.\n" +
    "· 기본/심화 풀이 기록과 점수\n" +
    "· 이 책에서 받은 뱃지와 카드\n" +
    "· 독서일지에 적힌 이 책의 점수\n" +
    (laterCount > 0 ? `· 이 책 다음에 추천받은 책 ${laterCount}권 (추천 자체가 취소됩니다)\n` : "") +
    "\n삭제 후 학생은 이 책을 다시 읽으며 문제를 처음부터 다시 풉니다.\n되돌릴 수 없습니다. 삭제할까요?"
  );
  if (!ok) return;

  try {
    const result = await postJson("/admin/monitor/quiz/reset", {
      studentId: card.studentId,
      recommendId: page.recommendId,
    });
    await loadLiveView();
    alert(resetResultMessage(bookTitle, result));
  } catch (e) {
    alert(e.message);
  }
}

/* 삭제 후 직원이 곧바로 해야 할 행동을 알려준다 — 핵심은 "실물 책을 지금 줄 수 있느냐"다.
   되돌린 책을 그 사이 다른 학생이 가져간 경우(A가 끝낸 책을 B가 추천받음)가 실제로 생긴다. */
function resetResultMessage(bookTitle, result) {
  const head = `${bookTitle} 문제풀이 기록을 삭제했습니다.\n`;
  if (!result || !result.bookSecured) {
    return head +
      "\n[실물 책 없음] 이 책의 남은 재고가 없습니다(다른 학생이 대여 중).\n" +
      "문제는 화면으로 풀 수 있으니 진행에는 문제없지만, 책이 필요하면 직접 챙겨주세요.";
  }
  if (result.copySwitched) {
    return head + "\n같은 책의 다른 사본으로 대여했습니다. 학생에게 그 책을 전달해 주세요.";
  }
  return head + "\n이 책을 다시 대여 처리했습니다. 학생에게 책을 전달해 주세요.";
}

/* stat-row — 독서시간/기본문제/심화문제/획득뱃지 전부 선택된 책 페이지(content_id) 기준이다.
   뱃지도 예전엔 학생 전체 합산이라 A책 카드에 B책 뱃지가 같이 보이는 문제가 있었다(2026-07-29 수정) */
function renderStatRow(el, card, page) {
  // 처음 점수 → 최종 점수(재도전으로 갱신됐을 때만 화살표로 함께 표시), 2026-08-28
  let basicText = "-";
  if (page.basicTotalCount) {
    basicText = `${page.basicCorrectCount ?? 0}/${page.basicTotalCount}`;
    if (page.basicFinalCorrectCount != null && page.basicFinalCorrectCount !== page.basicCorrectCount) {
      basicText += ` → ${page.basicFinalCorrectCount}/${page.basicTotalCount}`;
    }
  }
  const advancedText = page.advancedTotalCount ? `${page.advancedCorrectCount ?? 0}/${page.advancedTotalCount}` : "-";
  // 문제풀이를 시작한 뒤로는 독서 시간이 더 흐르면 안 된다(서버가 이미 그 시점 값으로 얼려서 내려줌) —
  // READING/TIME_OVER(아직 순수 독서 중)일 때만 로컬로 초를 더 올려서 보여준다.
  const stillReading = card.cardStatus === "READING" || card.cardStatus === "TIME_OVER";
  const elapsed = stillReading ? liveElapsed(page.elapsedMinutes, card._syncedAt) : page.elapsedMinutes;
  const readingTimeText = elapsed != null ? `${elapsed}분` : "-";
  const recommendedText = page.readingTimeMinutes != null ? `권장 ${page.readingTimeMinutes}분` : "";
  const isOverTime = page.readingTimeMinutes != null && elapsed != null && elapsed > page.readingTimeMinutes;

  // 첫 제출 뒤 basicStatus는 항상 DONE — 합격 여부는 grade로 가른다(2026-08-28).
  //   KING=만점 / FRIEND=통과 / grade 없음=재도전 필요
  const basicPill = page.basicStatus === "DONE"
    ? (page.basicGrade === "KING"
        ? `<span class="stat-pill pill-pass">만점</span>`
        : page.basicGrade === "FRIEND"
          ? `<span class="stat-pill pill-pass">통과</span>`
          : `<span class="stat-pill pill-retry">재도전</span>`)
    : "";
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
  if (!confirm("저장하시겠습니까?")) return;

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
    alert("저장되었습니다.");
    closeReadingLogPanel();
  } catch (e) {
    alert(e.message);
  }
}
