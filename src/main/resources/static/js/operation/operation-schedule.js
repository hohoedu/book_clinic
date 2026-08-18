/*
 * 운영 스케줄 설정 — 서버 연동 (2026-08-18)
 *
 * [화면이 다루는 것은 "규칙"이다] 저장 버튼이 만드는 것은 요일 규칙(schedule)이고, 학생이 예약하는
 * 실제 슬롯(slot_instance)은 서버의 materialize 엔진이 그 규칙으로 찍어낸다. 그래서 이 화면은
 * 슬롯을 직접 만들지 않으며, 저장 후 "언제부터 적용되는지"만 안내한다.
 *
 * [왼쪽 카드가 두 가지 일을 한다] 평소에는 요일 규칙 편집기지만, 오른쪽에서 '운영시간 변경'이나
 * '운영회차 변경'을 고르면 그 순간부터 예외 일정의 입력 폼이 된다(화면 설계상 정해진 동작).
 * 그래서 예외 모드에서의 편집은 요일 규칙 쪽 변경으로 세지 않는다 — 안 그러면 예외를 만들려고
 * 바꾼 값이 요일 규칙 저장에 딸려 들어간다.
 */
(function () {
  "use strict";

  const API = "/admin/operation/schedule";
  const CSRF_HEADER = "X-XSRF-TOKEN";

  // 서버가 세션마다 다른 값을 XSRF-TOKEN 쿠키로 내려준다(CookieCsrfTokenRepository) —
  // 고정 문자열을 하드코딩하지 않고 매 요청마다 쿠키에서 읽는다.
  function getCsrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : "";
  }

  async function getJson(url) {
    const res = await fetch(url);
    const data = await res.json();
    if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");
    return data.response;
  }

  async function sendJson(url, body, method = "POST") {
    const res = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json", [CSRF_HEADER]: getCsrfToken() },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const data = await res.json();
    if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");
    return data.response;
  }

  /* ── 요일 매핑 ─────────────────────────────────────────────────────────
     서버는 ISO 8601(1=월 ~ 7=일)을 쓰고 화면 탭은 mon~sun 키를 쓴다. 둘을 섞으면 실수가
     나므로 경계에서만 변환한다. */
  const DAY_KEYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"];
  const DAY_LABEL = {
    mon: "월요일", tue: "화요일", wed: "수요일", thu: "목요일",
    fri: "금요일", sat: "토요일", sun: "일요일",
  };
  const dowOf = (key) => DAY_KEYS.indexOf(key) + 1;
  const keyOf = (dow) => DAY_KEYS[dow - 1];
  const labelOf = (dow) => DAY_LABEL[keyOf(dow)];

  const dayTabs = document.getElementById("dayTabs");
  const dayScheduleTitle = document.getElementById("dayScheduleTitle");
  const dayScheduleBadge = document.getElementById("dayScheduleBadge");
  const dayScheduleStateToggle = document.getElementById("dayScheduleStateToggle");
  const roundListBody = document.getElementById("roundListBody");
  const roundDefaultMinutes = document.getElementById("roundDefaultMinutes");
  const btnAutoGenerate = document.getElementById("btnAutoGenerate");
  const btnSaveSchedule = document.getElementById("btnSaveSchedule");
  const editModeToggle = document.getElementById("editModeToggle");
  const dayHoursCard = document.getElementById("dayHoursCard");
  const roundManageCard = document.getElementById("roundManageCard");
  const maxCapacityInput = document.getElementById("maxCapacity");
  const dayOpenTime = document.getElementById("dayOpenTime");
  const dayCloseTime = document.getElementById("dayCloseTime");
  const effectiveFromDate = document.getElementById("effectiveFromDate");
  const applyNowCheck = document.getElementById("applyNowCheck");

  const exceptionTypeGroup = document.getElementById("exceptionTypeGroup");
  const exceptionEditHint = document.getElementById("exceptionEditHint");
  const exceptionStartDate = document.getElementById("exceptionStartDate");
  const exceptionEndDate = document.getElementById("exceptionEndDate");
  const exceptionReason = document.getElementById("exceptionReason");
  const exceptionList = document.getElementById("exceptionList");
  const btnRegisterException = document.getElementById("btnRegisterException");

  const btnRecordSchedule = document.getElementById("btnRecordSchedule");
  const exceptionPanel = document.getElementById("exceptionPanel");
  const historyPanel = document.getElementById("historyPanel");
  const historyList = document.getElementById("historyList");

  /* ── 상태 ────────────────────────────────────────────────────────────
     days[dow] = 서버에서 받은 요일 규칙 + 화면에서 편집 중인 값.
     dirty = 사용자가 실제로 건드린 요일만 담는다. 저장할 때 이 요일들만 서버로 보낸다 —
     7개를 항상 다 보내면, 예약이 걸린 요일 하나 때문에 나머지 6개까지 저장이 거부된다. */
  // mode: view(기본, 읽기 전용) / edit(요일 규칙 편집) / exception(예외 입력)
  const state = { days: {}, dirty: new Set(), exceptions: [], activeDow: 1, loaded: false, mode: "view" };

  function todayStr() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  function toMinutes(hhmm) {
    const [h, m] = hhmm.split(":").map(Number);
    return h * 60 + m;
  }

  function addMinutes(hhmm, minutes) {
    const total = toMinutes(hhmm) + minutes;
    const nh = Math.floor((total % (24 * 60)) / 60);
    const nm = total % 60;
    return `${String(nh).padStart(2, "0")}:${String(nm).padStart(2, "0")}`;
  }

  const numberOf = (value) => parseInt(String(value).replace(/\D/g, ""), 10) || 0;

  /**
   * 회차 사이 쉬는 시간(분).
   *
   * 자동 생성이 회차를 연달아 붙이면 앞 수업이 끝나는 순간 다음 수업이 시작돼 실제 운영과 맞지 않는다.
   * 기존 1~4교시 체계도 50분 수업 + 10분 휴식이라 같은 값을 쓴다. 화면에 입력란이 없어 상수로 두되,
   * 저장할 때 schedule.break_minutes에 함께 기록해 DB가 실제 운영과 어긋나지 않게 한다.
   */
  const BREAK_MINUTES = 10;

  /* ── 회차 표 ─────────────────────────────────────────────────────────── */

  // data-seq는 서버가 매긴 회차 번호다. 화면 순서와 분리해 두는 이유는 예외(회차 변경)에서
  // "3회차를 마감" 같은 지시를 만들 때 원래 번호가 있어야 하기 때문이다.
  function buildRoundRow({ seq, startTime, endTime, capacity, closed }) {
    const minutes = toMinutes(endTime) - toMinutes(startTime);
    const tr = document.createElement("tr");
    tr.dataset.seq = seq;
    if (closed) tr.classList.add("round-closed");
    tr.innerHTML = `
      <td><i class="fa-solid fa-grip-vertical round-drag"></i></td>
      <td class="round-name">${seq}회차${closed ? ' <span class="round-closed-tag">마감</span>' : ""}</td>
      <td><input type="time" value="${startTime}" /></td>
      <td><input type="time" value="${endTime}" /></td>
      <td>
        <select>
          ${[30, 40, 50, 60].map((m) => `<option value="${m}" ${minutes === m ? "selected" : ""}>${m}분</option>`).join("")}
          ${[30, 40, 50, 60].includes(minutes) ? "" : `<option value="${minutes}" selected>${minutes}분</option>`}
        </select>
      </td>
      <td>
        <div class="stepper">
          <button type="button" class="stepper-minus">-</button>
          <input type="text" class="stepper-value" value="${capacity}명" inputmode="numeric" />
          <button type="button" class="stepper-plus">+</button>
        </div>
      </td>
      <td><button type="button" class="round-delete" aria-label="삭제"><i class="fa-solid fa-trash-can"></i></button></td>
    `;
    return tr;
  }

  function renderRounds(slots) {
    roundListBody.innerHTML = "";
    slots.forEach((slot, idx) => {
      roundListBody.appendChild(buildRoundRow({
        seq: slot.seq ?? idx + 1,
        startTime: slot.startTime,
        endTime: slot.endTime,
        capacity: slot.capacity,
        closed: slot.closed,
      }));
    });
  }

  function readRounds() {
    return [...roundListBody.querySelectorAll("tr")].map((tr) => ({
      seq: Number(tr.dataset.seq),
      startTime: tr.children[2].querySelector("input").value,
      endTime: tr.children[3].querySelector("input").value,
      capacity: numberOf(tr.children[5].querySelector(".stepper-value").value),
    }));
  }

  /** 회차 삭제·추가 후 번호를 1부터 다시 매긴다. 예외 모드에서는 부르지 않는다 —
      그쪽은 "원래 3회차"를 가리켜야 해서 번호가 고정돼 있어야 한다. */
  function renumberRounds() {
    roundListBody.querySelectorAll("tr").forEach((tr, idx) => {
      tr.dataset.seq = idx + 1;
      tr.querySelector(".round-name").textContent = `${idx + 1}회차`;
    });
  }

  /**
   * 운영시간을 회차 길이로 잘라 회차 목록을 만든다.
   *
   * 서버 ScheduleMaterializer.generateSlots()와 같은 규칙이어야 한다 — 마지막 자투리 시간은
   * 회차로 만들지 않고, 번호는 시작시각 순으로 1부터 매긴다. 두 곳이 어긋나면 화면에서 본
   * 회차와 실제로 찍히는 슬롯이 달라진다.
   */
  function buildSlotGrid(open, close, minutes, capacity) {
    const slots = [];
    let cursor = open;
    while (slots.length < 255) {
      const next = addMinutes(cursor, minutes);
      if (toMinutes(next) > toMinutes(close)) break;
      slots.push({ seq: slots.length + 1, startTime: cursor, endTime: next, capacity });
      cursor = addMinutes(next, BREAK_MINUTES);   // 다음 회차는 쉬는 시간만큼 밀린다
    }
    return slots;
  }

  function autoGenerateRounds() {
    const slots = buildSlotGrid(
      dayOpenTime.value || "13:00",
      dayCloseTime.value || "19:00",
      Number(roundDefaultMinutes.value) || 50,
      numberOf(maxCapacityInput.value) || 10);

    if (!slots.length) {
      alert("운영 시간이 회차 기본 시간보다 짧아 회차를 만들 수 없습니다.");
      return;
    }
    renderRounds(slots);
    if (state.days[state.activeDow]) state.days[state.activeDow].breakMinutes = BREAK_MINUTES;
    markDirty();
  }

  /* ── 요일 렌더링 ─────────────────────────────────────────────────────── */

  /**
   * 요일 탭의 운영/휴무 표시.
   *
   * 보기 모드에서는 이번 주 실제 상태를 보여준다 — 8/21(금)이 예외로 휴무면 금요일 탭도
   * 휴무여야 한다. 왼쪽 카드는 그날 모습을 보여주는데 탭만 "운영"이면 서로 어긋난다.
   * 편집 모드에서는 고치는 대상이 평소 규칙이므로 규칙 그대로 표시한다.
   */
  function renderTabs() {
    dayTabs.querySelectorAll(".day-tab").forEach((tab) => {
      const dow = dowOf(tab.dataset.day);
      const day = state.days[dow];

      let isOpen = !!day?.isOpen;
      if (state.mode === "view") {
        const closedByException = (thisWeekExceptionFor(dow)?.exceptions ?? [])
          .some((ex) => ex.exceptionType === "CLOSED");
        if (closedByException) isOpen = false;
      }

      tab.dataset.state = isOpen ? "open" : "closed";
      // 예외 입력 중에는 어떤 요일도 선택 상태로 두지 않는다
      tab.classList.toggle("active", state.mode !== "exception" && dow === state.activeDow);
      const stateEl = tab.querySelector(".day-tab-state");
      stateEl.textContent = isOpen ? "운영" : "휴무";
      stateEl.classList.toggle("closed", !isOpen);
    });
  }

  /** 이번 주(월~일) 안에서 그 요일에 해당하는 날짜 */
  function dateInThisWeek(dow) {
    const now = new Date();
    const todayDow = now.getDay() === 0 ? 7 : now.getDay();
    const target = new Date(now);
    target.setDate(now.getDate() - (todayDow - 1) + (dow - 1));
    return `${target.getFullYear()}-${String(target.getMonth() + 1).padStart(2, "0")}-${String(target.getDate()).padStart(2, "0")}`;
  }

  /**
   * 이번 주 그 요일에 걸린 예외.
   *
   * 다음 주 이후의 예외는 보여주지 않는다 — 편집기는 "이번 주 수요일이 어떻게 돌아가는가"를
   * 확인하는 자리이고, 2주 뒤 예외까지 끌어오면 지금 화면이 어느 날짜를 말하는지 흐려진다.
   * 먼 미래의 예외는 이력 패널에서 기간과 함께 본다.
   */
  function thisWeekExceptionFor(dow) {
    const date = dateInThisWeek(dow);
    const exceptions = (state.exceptions ?? [])
      .filter((ex) => !ex.past && ex.startDate <= date && ex.endDate >= date);
    return exceptions.length ? { date, exceptions } : null;
  }

  /**
   * 요일 화면 그리기. 모드에 따라 보여주는 값이 다르다.
   *
   *   view      기본. 이번 주 그 요일의 실제 모습(예외 적용)을 읽기 전용으로 보여준다.
   *   edit      [편집]을 눌렀을 때. 평소 요일 규칙만 보여주고 고칠 수 있다.
   *   exception 오른쪽에서 운영시간/회차 변경을 골랐을 때. 그 카드만 예외 입력 폼이 된다.
   *
   * 모드를 나눈 이유는, 한 카드가 "그날 모습"과 "요일 규칙" 두 가지를 상황에 따라 보여주면
   * 지금 화면의 값이 저장 대상인지 아닌지를 사용자도 코드도 헷갈리기 때문이다.
   */
  /** 예외 입력 중에는 선택한 예외 날짜의 요일이 기준이다 (요일 탭이 아니라) */
  function exceptionDow() {
    const d = new Date(exceptionStartDate.value);
    const dow = d.getDay();
    return Number.isNaN(dow) ? state.activeDow : (dow === 0 ? 7 : dow);
  }

  /** 지금 화면이 다루는 요일 — 예외 입력 중이면 예외 날짜의 요일, 아니면 선택한 탭 */
  function baseDow() {
    return state.mode === "exception" ? exceptionDow() : state.activeDow;
  }

  function renderDay(dow) {
    // 예외 입력 중에는 탭에서 고른 요일이 아니라 "예외 날짜의 요일"을 그린다.
    // 8/21을 골랐는데 화요일 스케줄이 떠 있으면 무엇을 고치는 중인지 알 수 없다.
    if (state.mode === "exception") dow = exceptionDow();

    const day = state.days[dow];

    dayOpenTime.value = day.openTime || "13:00";
    dayCloseTime.value = day.closeTime || "19:00";
    maxCapacityInput.value = `${day.defaultCapacity ?? 10}명`;

    const minutesOption = [...roundDefaultMinutes.options].find((o) => Number(o.value) === day.slotMinutes);
    if (minutesOption) roundDefaultMinutes.value = String(day.slotMinutes);

    // 예외는 하루일 수도 기간일 수도 있어 제목에 날짜를 박지 않는다
    dayScheduleTitle.textContent = state.mode === "exception"
      ? "예외일 스케줄"
      : `${labelOf(dow)} 스케줄`;

    if (state.mode === "view") {
      renderDayView(dow, day);
    } else {
      // 편집·예외 입력은 둘 다 평소 요일 규칙에서 출발한다
      applyDayBadge(!!day.isOpen);
      renderRounds(day.slots ?? []);
    }

    renderTabs();       // 예외로 휴무가 되는 날이 있으면 탭 표시도 함께 바뀐다
    applyModeLock();
  }

  /** 보기 모드 — 이번 주 그 요일에 예외가 걸려 있으면 적용된 결과를 그린다 */
  function renderDayView(dow, day) {
    const preview = thisWeekExceptionFor(dow);
    if (!preview) {
      applyDayBadge(!!day.isOpen);
      renderRounds(day.slots ?? []);
      return;
    }

    const period = preview.exceptions.find((ex) => ex.exceptionType !== "SLOT_CHANGE");
    const slotChange = preview.exceptions.find((ex) => ex.exceptionType === "SLOT_CHANGE");
    const closed = period?.exceptionType === "CLOSED" || !day.isOpen;
    applyDayBadge(!closed);

    if (period?.exceptionType === "TIME_CHANGE") {
      dayOpenTime.value = period.openTime;
      dayCloseTime.value = period.closeTime;
    }

    let slots = closed ? [] : (day.slots ?? []).map((slot) => ({ ...slot }));

    // 아래 단계는 서버 ScheduleMaterializer와 같은 순서·같은 규칙이어야 한다.
    if (!closed && period?.exceptionType === "TIME_CHANGE") {
      // 등록할 때 확정한 회차가 있으면 그것이 그날의 회차다. 템플릿에서 걸러내는 것은
      // 회차를 따로 저장하지 않던 시절의 예외를 위한 폴백일 뿐이다(서버 로직과 동일).
      const defined = (period.slotOverrides ?? []).filter((o) => o.startTime && o.endTime);
      if (defined.length) {
        slots = defined.map((o) => ({
          seq: o.seq,
          startTime: o.startTime,
          endTime: o.endTime,
          capacity: o.capacity ?? day.defaultCapacity,
          closed: o.isClosed,
        }));
      } else {
        const open = toMinutes(period.openTime);
        const close = toMinutes(period.closeTime);
        slots = slots.filter((slot) => toMinutes(slot.startTime) >= open && toMinutes(slot.endTime) <= close);
      }
    }
    if (!closed && slotChange) {
      slots = slots.map((slot) => {
        const override = (slotChange.slotOverrides ?? []).find((o) => o.seq === slot.seq);
        return override
          ? { ...slot, closed: override.isClosed, capacity: override.capacity ?? slot.capacity }
          : slot;
      });
    }

    renderRounds(slots);
  }

  /** 편집 모드에서만 입력이 열린다. 저장 버튼도 그때만 쓸 수 있다 */
  function applyModeLock() {
    const editable = state.mode === "edit";
    [dayHoursCard, roundManageCard].forEach((card) => {
      card.classList.toggle("view-locked", !editable && state.mode !== "exception");
      card.querySelectorAll("input, select, button").forEach((el) => {
        el.disabled = !editable;
      });
    });
    dayScheduleStateToggle.disabled = !editable;
    btnSaveSchedule.disabled = !editable;

    // 예외 입력 중에는 요일 탭을 잠근다 — 지금 다루는 것은 "요일"이 아니라 "그 날짜"라서,
    // 탭이 눌리면 어느 쪽을 편집하는 중인지 화면이 스스로 모순된다.
    const tabsLocked = state.mode === "exception";
    dayTabs.classList.toggle("tabs-locked", tabsLocked);
    dayTabs.querySelectorAll(".day-tab").forEach((tab) => {
      tab.disabled = tabsLocked;
    });

    // 예외 입력 모드의 잠금은 예외 유형이 결정한다(운영시간/회차 중 하나만 열림)
    if (state.mode === "exception") applyExceptionCardLock(currentExceptionType());
  }

  function applyDayBadge(isOpen) {
    dayScheduleBadge.textContent = isOpen ? "운영중" : "휴무";
    dayScheduleBadge.classList.toggle("badge-open", isOpen);
    dayScheduleBadge.classList.toggle("badge-closed", !isOpen);
    dayScheduleStateToggle.checked = isOpen;
  }

  /** 화면의 입력값을 상태로 걷어온다. 요일 탭을 옮기거나 저장할 때 호출 */
  function captureDay(dow) {
    // 편집 모드에서 들어온 값만 규칙으로 걷어간다. 보기 화면에는 예외가 적용된 값이 떠 있을 수
    // 있어서, 그걸 걷어가면 하루짜리 예외가 요일 규칙으로 굳어버린다.
    if (!state.loaded || state.mode !== "edit") return;
    const day = state.days[dow];
    if (!day) return;

    day.isOpen = dayScheduleStateToggle.checked;
    day.openTime = dayOpenTime.value;
    day.closeTime = dayCloseTime.value;
    day.slotMinutes = Number(roundDefaultMinutes.value) || 50;
    day.defaultCapacity = numberOf(maxCapacityInput.value);
    day.slots = readRounds();
  }

  function markDirty() {
    if (!state.loaded || state.mode !== "edit") return;
    state.dirty.add(state.activeDow);
  }

  /* ── 조회 ────────────────────────────────────────────────────────────── */

  async function loadWeek() {
    const week = await getJson(`${API}/week`);
    state.days = {};
    week.days.forEach((day) => {
      state.days[day.dayOfWeek] = day;
    });
    state.dirty.clear();
    state.loaded = true;

    renderTabs();
    renderDay(state.activeDow);
  }

  async function loadExceptions() {
    const list = await getJson(`${API}/exception`);
    state.exceptions = list;
    renderTabs();                 // 예외가 걸린 요일 표시를 갱신한다
    exceptionList.innerHTML = "";

    if (!list.length) {
      const empty = document.createElement("li");
      empty.className = "exception-empty";
      empty.textContent = "등록된 예외 일정이 없습니다.";
      exceptionList.appendChild(empty);
      return;
    }

    list.forEach((ex) => exceptionList.appendChild(buildExceptionItem(ex)));
  }

  function buildExceptionItem(ex) {
    const li = document.createElement("li");
    li.className = "exception-item" + (ex.past ? " past" : "");
    li.dataset.exceptionId = ex.exceptionId;

    const singleDay = ex.startDate === ex.endDate;
    const dateText = singleDay ? ex.startDate : `${ex.startDate} ~ ${ex.endDate}`;
    const weekday = singleDay ? `(${["일", "월", "화", "수", "목", "금", "토"][new Date(ex.startDate).getDay()]})` : "";

    const typeClass = { CLOSED: "type-closed", TIME_CHANGE: "type-time", SLOT_CHANGE: "type-round" }[ex.exceptionType] ?? "";
    const detail = exceptionDetail(ex);

    li.innerHTML = `
      <div class="exception-date">
        <strong>${dateText}</strong>
        <span>${weekday}</span>
      </div>
      <div class="exception-body">
        <span class="exception-type ${typeClass}">${ex.exceptionTypeLabel}</span>
        <p>${escapeHtml(detail)}</p>
      </div>
      ${ex.past ? "" : `<button type="button" class="exception-delete" aria-label="삭제"><i class="fa-solid fa-trash-can"></i></button>`}
    `;
    // 여기는 예외를 등록·관리하는 자리라 방금 쓴 사유를 다시 보여줄 필요가 없다.
    // 사유 툴팁은 흐름을 훑는 이력 패널에만 둔다.
    return li;
  }

  /**
   * 예외 종류 라벨에 사유를 툴팁으로 단다.
   *
   * 사유는 "광복절", "공사로 인한 임시 휴무"처럼 길이가 제각각이라 목록에 그대로 깔면 카드 높이가
   * 들쭉날쭉해지고 정작 중요한 날짜·종류가 묻힌다. 필요할 때만 꺼내 보도록 툴팁으로 옮긴다.
   * innerHTML이 아니라 property로 넣는 이유는 사유에 따옴표가 들어가도 안전해서다.
   */
  /**
   * 카드 한 줄에 보여줄 예외 내용.
   *
   * 운영시간 변경은 바뀐 시간을, 회차 변경은 조정한 회차를 적는다. 휴무는 보여줄 조정 내용이
   * 없어 그 자리가 비므로 사유를 넣는다 — "왜 쉬는지"가 휴무에서 가장 궁금한 정보이기도 하다.
   */
  function exceptionDetail(ex) {
    if (ex.exceptionType === "TIME_CHANGE" && ex.openTime) {
      return `${ex.openTime} ~ ${ex.closeTime}`;
    }
    if (ex.exceptionType === "CLOSED") {
      return ex.reason ?? "";
    }
    return ex.slotSummary ?? "";
  }

  function setReasonTooltip(item, reason) {
    const label = item.querySelector(".exception-type");
    // title이 아니라 data 속성에 담는다 — title은 브라우저가 1초쯤 뒤에야 띄우고,
    // 아래 커스텀 툴팁과 겹쳐서 두 개가 동시에 뜬다.
    if (label && reason) label.dataset.reason = reason;
  }

  /* ── 사유 툴팁 ────────────────────────────────────────────────────────
     목록이 세로 스크롤되는 패널 안에 있어서, 라벨 안에 absolute로 붙이면 첫 항목·마지막 항목의
     툴팁이 잘린다. 그래서 body에 하나만 만들어 두고 fixed로 띄운다. */
  let reasonTooltip = null;

  function showReasonTooltip(target) {
    if (!reasonTooltip) {
      reasonTooltip = document.createElement("div");
      reasonTooltip.className = "reason-tooltip";
      document.body.appendChild(reasonTooltip);
    }
    reasonTooltip.textContent = target.dataset.reason;
    reasonTooltip.style.visibility = "hidden";
    reasonTooltip.style.display = "block";

    // 기본은 라벨 아래. 화면 아래가 좁을 때만 위로 뒤집고, 가로는 화면 안쪽으로 밀어 넣는다
    const rect = target.getBoundingClientRect();
    const tip = reasonTooltip.getBoundingClientRect();
    const left = Math.min(Math.max(8, rect.left), window.innerWidth - tip.width - 8);
    const below = rect.bottom + 6;
    const fitsBelow = below + tip.height <= window.innerHeight - 8;

    reasonTooltip.style.left = `${left}px`;
    reasonTooltip.style.top = `${fitsBelow ? below : rect.top - tip.height - 6}px`;
    reasonTooltip.style.visibility = "visible";
  }

  function hideReasonTooltip() {
    if (reasonTooltip) reasonTooltip.style.display = "none";
  }

  document.addEventListener("mouseover", (e) => {
    const label = e.target.closest(".exception-type[data-reason]");
    if (label) showReasonTooltip(label);
  });

  document.addEventListener("mouseout", (e) => {
    if (e.target.closest(".exception-type[data-reason]")) hideReasonTooltip();
  });

  // 목록을 스크롤하면 라벨이 움직이므로 툴팁을 따라 옮기지 않고 그냥 닫는다
  document.addEventListener("scroll", hideReasonTooltip, true);

  function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
  }

  /* ── 저장 ────────────────────────────────────────────────────────────── */

  /**
   * 저장·등록·삭제로 스케줄이 바뀐 뒤 이력 목록을 다시 읽는다.
   *
   * 이력 패널이 숨어 있으면 굳이 조회하지 않는다 — 다시 펼칠 때 showPanel이 어차피 그리므로
   * 안 보이는 목록 때문에 API를 두 번 더 호출할 이유가 없다.
   */
  async function refreshHistoryIfVisible() {
    if (visiblePanel === "history") await renderHistory();
  }

  async function saveSchedule() {
    if (state.mode !== "edit") {
      alert("편집을 켠 뒤 수정하고 저장해주세요.");
      return;
    }
    captureDay(state.activeDow);

    if (!state.dirty.size) {
      alert("변경된 요일이 없습니다.");
      return;
    }

    const days = [...state.dirty].sort().map((dow) => {
      const day = state.days[dow];
      return {
        dayOfWeek: dow,
        isOpen: !!day.isOpen,
        openTime: day.isOpen ? day.openTime : null,
        closeTime: day.isOpen ? day.closeTime : null,
        slotMinutes: day.slotMinutes,
        breakMinutes: day.breakMinutes ?? 0,
        defaultCapacity: day.defaultCapacity,
        // 휴무면 서버가 회차를 무시하지만, 보내지 않는 편이 의도가 분명하다
        slots: day.isOpen ? (day.slots ?? []).map(({ startTime, endTime, capacity }) => ({ startTime, endTime, capacity })) : [],
      };
    });

    // 안전장치 — 이번 주에 예외가 걸린 요일을 저장할 때 그 사실을 알려준다. 규칙을 바꿔도 예외는
    // 그대로 남아 그날만 다르게 운영되는데, 모르고 저장하면 "왜 그날만 안 바뀌지"가 된다.
    // 범위를 이번 주로 맞춘 것은 보기 화면이 보여주는 범위와 같게 하기 위해서다 — 화면에 없던
    // 날짜가 확인창에만 튀어나오면 무엇을 확인하라는 것인지 알 수 없다.
    const warned = [...state.dirty]
      .map((dow) => ({ dow, preview: thisWeekExceptionFor(dow) }))
      .filter(({ preview }) => preview)
      .map(({ dow, preview }) => `${labelOf(dow)} — ${preview.date} `
        + preview.exceptions.map((ex) => ex.exceptionTypeLabel).join(", "));

    if (warned.length) {
      const ok = await customConfirm(
        `이번 주 아래 날짜에는 예외 일정이 등록돼 있습니다.\n\n${warned.join("\n")}\n\n`
        + "예외는 그대로 유지되어 그날은 다르게 운영됩니다. 저장할까요?");
      if (!ok) return;
    }

    const payload = {
      applyNow: applyNowCheck.checked,
      effectiveFrom: applyNowCheck.checked ? null : effectiveFromDate.value,
      days,
    };

    await withBusy(btnSaveSchedule, async () => {
      const message = await sendJson(`${API}/week`, payload);
      alert(message);
      state.mode = "view";    // 저장이 끝났으면 편집을 닫고 보기 화면으로
      editModeToggle.checked = false;
      await loadWeek();       // 서버가 매긴 회차 번호·적용일로 화면을 다시 맞춘다
      await loadExceptions();
      await refreshHistoryIfVisible();   // 방금 만든 버전이 이력에 바로 보여야 한다
    });
  }

  /* ── 예외 일정 ───────────────────────────────────────────────────────── */

  const EXCEPTION_TYPE = { closed: "CLOSED", time: "TIME_CHANGE", round: "SLOT_CHANGE" };

  function currentExceptionType() {
    return exceptionTypeGroup.querySelector("input:checked")?.value ?? "closed";
  }

  const isExceptionMode = () => currentExceptionType() !== "closed";

  async function registerException() {
    const uiType = currentExceptionType();
    const type = EXCEPTION_TYPE[uiType];

    const payload = {
      startDate: exceptionStartDate.value,
      endDate: uiType === "round" ? exceptionStartDate.value : exceptionEndDate.value,
      exceptionType: type,
      reason: exceptionReason.value.trim(),
    };

    if (type === "TIME_CHANGE") {
      payload.openTime = dayOpenTime.value;
      payload.closeTime = dayCloseTime.value;
      // 그날 회차를 그대로 확정해 보낸다. 번호는 서버가 시작시각 순으로 다시 매긴다.
      payload.slots = readRounds().map(({ startTime, endTime, capacity }) => ({
        startTime, endTime, capacity, isClosed: false,
      }));
      if (!payload.slots.length) {
        alert("회차를 1개 이상 남겨주세요.");
        return;
      }
    } else if (type === "SLOT_CHANGE") {
      payload.slots = buildSlotOverrides();
      if (!payload.slots.length) {
        alert("회차를 삭제하거나 인원을 바꾼 뒤 등록해주세요.");
        return;
      }
    }

    await withBusy(btnRegisterException, async () => {
      const message = await sendJson(`${API}/exception`, payload);
      alert(message);
      exceptionReason.value = "";
      state.mode = editModeToggle.checked ? "edit" : "view";
      resetExceptionType();         // 예외 입력용으로 바꿔둔 왼쪽 카드를 원래 상태로
      await refreshAfterExceptionChange();
    });
  }

  /**
   * 회차 변경(SLOT_CHANGE) 지시를 만든다.
   *
   * 화면에 별도의 "마감" 체크박스가 없어서, 왼쪽 표를 원래 회차와 비교해 지시를 뽑아낸다 —
   * 행을 지웠으면 그 회차 마감, 인원을 바꿨으면 정원 변경. 화면 안내문("왼쪽에서 일정을 변경한 후
   * 예외 일정 등록을 눌러주세요")이 가리키는 동작이 이것이다.
   */
  function buildSlotOverrides() {
    const base = state.days[exceptionDow()]?.slots ?? [];
    const current = readRounds();
    const currentBySeq = new Map(current.map((slot) => [slot.seq, slot]));

    const overrides = [];
    base.forEach((slot) => {
      const now = currentBySeq.get(slot.seq);
      if (!now) {
        overrides.push({ seq: slot.seq, isClosed: true });
      } else if (now.capacity !== slot.capacity) {
        overrides.push({ seq: slot.seq, isClosed: false, capacity: now.capacity });
      }
    });
    return overrides;
  }

  /**
   * 예외를 등록·삭제한 뒤 화면 전체를 맞춘다.
   *
   * 예외 목록 → 요일 탭 → 왼쪽 카드 → 이력 순으로 빠짐없이 다시 그린다. 한 군데라도 빠지면
   * "새로고침해야 반영되는" 화면이 생긴다 — 실제로 삭제 후 왼쪽 카드가 예외 적용 상태로
   * 남아 있었다. 순서도 중요해서, 목록을 먼저 읽어야(state.exceptions 갱신) 그다음 렌더링이
   * 새 데이터를 쓴다.
   */
  async function refreshAfterExceptionChange() {
    await loadExceptions();            // state.exceptions 갱신 + 요일 탭 다시 그림
    renderDay(state.activeDow);        // 갱신된 예외 기준으로 왼쪽 카드 다시 그림
    await refreshHistoryIfVisible();
  }

  async function deleteException(exceptionId) {
    const ok = await customConfirm("이 예외 일정을 삭제할까요?\n삭제하면 해당 날짜는 요일 스케줄대로 되돌아갑니다.");
    if (!ok) return;
    const message = await sendJson(`${API}/exception/${exceptionId}`, undefined, "DELETE");
    alert(message);
    await refreshAfterExceptionChange();
  }

  function resetExceptionType() {
    const closedRadio = exceptionTypeGroup.querySelector('input[value="closed"]');
    closedRadio.checked = true;
    applyExceptionType("closed");
  }

  /**
   * 운영시간 변경 예외의 회차 자동 구성.
   *
   * 운영시간을 13:00 → 15:00으로 바꾸면 회차도 15:00부터 다시 끊는다. 남는 회차만 걸러내면
   * 첫 회차가 15:30처럼 어정쩡하게 시작해서, 관리자가 매번 [회차 자동 생성]을 다시 눌러야 했다.
   * 시간을 바꾸는 순간 회차가 그 시간에 맞춰지는 게 화면에서 기대하는 동작이다.
   *
   * 여기서 만든 것은 출발값일 뿐이라 표에서 그대로 고칠 수 있고, 등록 시에는 표에 있는
   * 회차가 그대로 저장된다.
   */
  function fillTimeChangeRounds() {
    if (!state.loaded) return;

    const day = state.days[baseDow()];
    renderRounds(buildSlotGrid(
      dayOpenTime.value,
      dayCloseTime.value,
      Number(roundDefaultMinutes.value) || day?.slotMinutes || 50,
      numberOf(maxCapacityInput.value) || day?.defaultCapacity || 10));
  }

  function setCardLocked(card, locked) {
    card.classList.toggle("locked", locked);
    card.querySelectorAll("input, select, button").forEach((el) => {
      el.disabled = locked;
    });
  }

  /**
   * 예외 입력 중 열어둘 카드.
   *
   * 운영시간 변경은 시간과 회차를 함께 고쳐야 한다 — 시간을 늘리거나 옮기면 그 시간대에
   * 넣을 회차를 관리자가 직접 정해야 하고, 줄일 때도 어떤 회차를 남길지는 사람이 판단할 몫이다.
   * 회차 변경은 회차만 건드리므로 운영시간 카드를 잠근다.
   */
  function applyExceptionCardLock(type) {
    setCardLocked(dayHoursCard, type === "round");
    setCardLocked(roundManageCard, false);
  }

  function applyExceptionType(type) {
    exceptionTypeGroup.querySelectorAll(".check-pill").forEach((p) => {
      p.classList.toggle("checked", p.dataset.exceptionType === type);
    });

    exceptionEditHint.hidden = type === "closed";
    // 회차 변경은 하루만 지정할 수 있다(서버 CK 제약과 동일) — 종료일을 시작일에 묶는다
    exceptionEndDate.disabled = type === "round";
    if (type === "round") exceptionEndDate.value = exceptionStartDate.value;

    // 휴무 예외는 날짜만 있으면 되므로 왼쪽 카드를 쓰지 않는다 → 보기 모드로 되돌린다
    if (type === "closed") {
      // 예외 입력을 벗어나면 편집 토글 상태로 돌아간다
      if (state.mode === "exception") state.mode = editModeToggle.checked ? "edit" : "view";
      if (state.loaded) renderDay(state.activeDow);
      return;
    }

    // 예외 입력은 평소 규칙에서 출발한다 — 예외가 적용된 보기 화면의 값에서 시작하면
    // 예외 위에 예외를 얹는 꼴이 된다.
    state.mode = "exception";
    if (state.loaded) renderDay(state.activeDow);
    applyExceptionCardLock(type);
    if (type === "time") fillTimeChangeRounds();
  }

  /* ── 공통 ────────────────────────────────────────────────────────────── */

  async function withBusy(button, task) {
    button.disabled = true;
    try {
      await task();
    } catch (e) {
      alert(e.message);
    } finally {
      button.disabled = false;
    }
  }

  function getMaxCapacity() {
    return numberOf(maxCapacityInput.value);
  }

  function flashLimit(input) {
    input.classList.add("stepper-limit");
    setTimeout(() => input.classList.remove("stepper-limit"), 400);
  }

  // 회차별 인원이 최대 예약 인원을 넘지 않도록 제한
  function clampRoundCapacity(input) {
    const max = getMaxCapacity();
    const suffix = input.value.replace(/^\d+/, "");
    let value = numberOf(input.value);
    if (value > max) {
      input.value = `${max}${suffix}`;
      flashLimit(input);
    }
  }

  function clampAllRoundCapacities() {
    roundListBody.querySelectorAll(".stepper-value").forEach(clampRoundCapacity);
  }

  /* ── 이벤트 ──────────────────────────────────────────────────────────── */

  dayTabs.addEventListener("click", (e) => {
    const tab = e.target.closest(".day-tab");
    if (!tab || !state.loaded) return;

    captureDay(state.activeDow);        // 탭을 옮겨도 편집 중이던 값이 날아가지 않게 먼저 걷어둔다
    state.activeDow = dowOf(tab.dataset.day);
    state.mode = editModeToggle.checked ? "edit" : "view";

    resetExceptionType();
    renderTabs();
    renderDay(state.activeDow);
  });

  /**
   * 편집 토글 — 켜면 요일 규칙을 고칠 수 있고 저장이 열린다. 끄면 전부 읽기 전용.
   *
   * 끌 때 고치던 값을 버리는 이유는, 화면에는 편집 결과가 남아 있는데 저장은 잠긴 어정쩡한
   * 상태가 가장 헷갈리기 때문이다. 버릴 게 있으면 먼저 물어본다.
   */
  editModeToggle.addEventListener("change", async () => {
    if (editModeToggle.checked) {
      state.mode = "edit";
      resetExceptionType();       // 예외 입력 중이었다면 정리하고 규칙 편집으로 들어간다
      state.mode = "edit";
      renderDay(state.activeDow);
      return;
    }

    if (state.dirty.size) {
      const ok = await customConfirm("저장하지 않은 변경이 있습니다.\n편집을 끄면 변경한 내용이 사라집니다. 계속할까요?");
      if (!ok) {
        editModeToggle.checked = true;
        return;
      }
    }

    state.mode = "view";
    state.dirty.clear();
    try {
      await loadWeek();           // 서버 값으로 되돌린다
    } catch (err) {
      alert(err.message);
    }
  });

  dayScheduleStateToggle.addEventListener("change", () => {
    const isOpen = dayScheduleStateToggle.checked;
    applyDayBadge(isOpen);
    if (!state.loaded) return;

    state.days[state.activeDow].isOpen = isOpen;
    markDirty();
    renderTabs();
  });

  // 스테퍼 공용 처리 (± 버튼)
  document.addEventListener("click", (e) => {
    const minusBtn = e.target.closest(".stepper-minus");
    const plusBtn = e.target.closest(".stepper-plus");
    const btn = minusBtn || plusBtn;
    if (!btn) return;

    const input = btn.closest(".stepper").querySelector(".stepper-value");
    const suffix = input.value.replace(/^\d+/, "");
    const current = numberOf(input.value);
    const isRoundCapacity = input !== maxCapacityInput && roundListBody.contains(input);

    if (plusBtn && isRoundCapacity && current >= getMaxCapacity()) {
      flashLimit(input);
      return;
    }

    input.value = `${minusBtn ? Math.max(0, current - 1) : current + 1}${suffix}`;
    if (input === maxCapacityInput) clampAllRoundCapacities();
    markDirty();
  });

  roundListBody.addEventListener("change", (e) => {
    if (e.target.classList.contains("stepper-value")) clampRoundCapacity(e.target);
    markDirty();
  });

  maxCapacityInput.addEventListener("change", () => {
    clampAllRoundCapacities();
    markDirty();
  });

  [dayOpenTime, dayCloseTime, roundDefaultMinutes].forEach((el) =>
    el.addEventListener("change", () => {
      markDirty();
      // 운영시간 변경 예외를 만드는 중이면 회차 미리보기를 즉시 다시 계산한다
      if (currentExceptionType() === "time") fillTimeChangeRounds();
    }));

  roundListBody.addEventListener("click", (e) => {
    const delBtn = e.target.closest(".round-delete");
    if (!delBtn) return;
    delBtn.closest("tr").remove();

    // 예외(회차 변경) 모드에서는 번호를 다시 매기지 않는다 — 지운 행이 "몇 회차였는지"가
    // 그대로 서버에 보낼 지시가 되기 때문이다.
    if (!isExceptionMode()) {
      renumberRounds();
      markDirty();
    }
  });

  btnAutoGenerate.addEventListener("click", autoGenerateRounds);
  btnSaveSchedule.addEventListener("click", saveSchedule);
  btnRegisterException.addEventListener("click", registerException);

  exceptionTypeGroup.addEventListener("click", (e) => {
    const pill = e.target.closest(".check-pill");
    if (!pill) return;
    if (e.target.tagName !== "INPUT") pill.querySelector("input").checked = true;
    applyExceptionType(pill.dataset.exceptionType);
  });

  exceptionStartDate.addEventListener("change", () => {
    if (exceptionEndDate.disabled || toMinutesDate(exceptionEndDate.value) < toMinutesDate(exceptionStartDate.value)) {
      exceptionEndDate.value = exceptionStartDate.value;
    }
    // 날짜가 바뀌면 대상 요일도 바뀐다 — 왼쪽 카드를 그 요일 스케줄로 다시 그린다
    if (state.mode === "exception") {
      renderDay(state.activeDow);
      if (currentExceptionType() === "time") fillTimeChangeRounds();
    }
  });

  const toMinutesDate = (value) => new Date(value).getTime() || 0;

  exceptionList.addEventListener("click", async (e) => {
    const delBtn = e.target.closest(".exception-delete");
    if (!delBtn) return;
    const item = delBtn.closest(".exception-item");
    try {
      await deleteException(Number(item.dataset.exceptionId));
    } catch (err) {
      alert(err.message);
    }
  });

  applyNowCheck.addEventListener("change", () => {
    const applyNow = applyNowCheck.checked;
    effectiveFromDate.disabled = applyNow;
    if (applyNow) effectiveFromDate.value = todayStr();
  });

  /* ── 오른쪽 패널 토글 (예외 설정 ↔ 예약 변경 이력) ─────────────────────
     두 패널이 같은 그리드 칸을 번갈아 쓴다. 버튼 라벨은 항상 "지금 누르면 무엇이 나오는지"를
     가리킨다 — 현재 보고 있는 화면 이름을 달면 눌러도 그대로인 것처럼 읽힌다. */
  const PANEL_LABEL = { exception: "예약 변경 이력", history: "특정일 예외 설정" };
  let visiblePanel = "exception";

  function showPanel(panel) {
    visiblePanel = panel;
    const showingHistory = panel === "history";

    exceptionPanel.hidden = showingHistory;
    historyPanel.hidden = !showingHistory;
    btnRecordSchedule.textContent = PANEL_LABEL[panel];

    // 예외 모드로 왼쪽 카드를 고쳐 쓰던 중에 이력으로 넘어가면, 그 편집 상태가 남아 요일 규칙
    // 편집기와 섞인다. 패널을 떠날 때 평소 상태로 되돌린다.
    // 예외 모드로 왼쪽 카드를 고쳐 쓰던 중에 이력으로 넘어가면 그 편집 상태가 남는다
    if (showingHistory) {
      resetExceptionType();
      renderHistory();
    }
  }

  /**
   * 스케줄 변경 예약 이력 — 적용 시작일 기준 버전 목록.
   *
   * 여기 나오는 "기간"은 테이블에 있는 값이 아니라 계산 결과다(종료일 = 다음 버전 시작일 - 1일).
   * 요일 구성도 그 버전에서 바뀐 요일만이 아니라 "그 시점부터 실제로 운영되는 주간 전체"다 —
   * 관리자가 궁금한 것은 그날부터 학원이 어떻게 도는가이기 때문. 둘 다 서버가 계산해 내려준다.
   */
  async function renderHistory() {
    historyList.innerHTML = "";

    // 요일 규칙 버전과 특정일 예외를 한 타임라인에 합친다. 둘 다 "예약된 변경"이라 따로 보면
    // 특정 시점에 무엇이 걸려 있는지 알려면 두 목록을 머릿속에서 합쳐야 한다.
    let versions;
    let exceptions;
    try {
      [versions, exceptions] = await Promise.all([
        getJson(`${API}/versions`),
        getJson(`${API}/exception`),
      ]);
    } catch (e) {
      historyList.appendChild(emptyRow(e.message));
      return;
    }

    const entries = [
      ...versions.map((v) => ({ kind: "version", data: v })),
      ...exceptions.map((x) => ({ kind: "exception", data: x })),
    ];

    if (!entries.length) {
      historyList.appendChild(emptyRow("예약된 변경이 없습니다."));
      return;
    }

    // 운영중·진행중 → 예정(가까운 순) → 지난(최근 순). 서버가 두 종류 모두 같은 status를 준다.
    const rank = { CURRENT: 0, UPCOMING: 1, PAST: 2 };
    entries.sort((a, b) => {
      const diff = rank[a.data.status] - rank[b.data.status];
      if (diff !== 0) return diff;
      const aStart = startOf(a);
      const bStart = startOf(b);
      return a.data.status === "PAST" ? bStart.localeCompare(aStart) : aStart.localeCompare(bStart);
    });

    entries.forEach((entry) => {
      historyList.appendChild(entry.kind === "version"
        ? buildVersionItem(entry.data)
        : buildExceptionHistoryItem(entry.data));
    });
  }

  const startOf = (entry) => entry.kind === "version" ? entry.data.effectiveFrom : entry.data.startDate;

  function emptyRow(message) {
    const li = document.createElement("li");
    li.className = "history-empty";
    li.textContent = message;
    return li;
  }

  const WEEKDAY_CHARS = ["일", "월", "화", "수", "목", "금", "토"];

  /** "2026-08-15 (토)" — 요일까지 붙여야 관리자가 날짜만 보고 주중/주말을 판단할 수 있다 */
  function formatDateWithDay(value) {
    if (!value) return "";
    return `${value} (${WEEKDAY_CHARS[new Date(value).getDay()]})`;
  }

  const BADGE_CLASS = { CURRENT: "badge-current", UPCOMING: "badge-upcoming", PAST: "badge-past" };

  function buildVersionItem(version) {
    const li = document.createElement("li");
    li.className = "version-item";
    li.dataset.effectiveFrom = version.effectiveFrom;

    // 마지막 버전은 종료일이 없다(무기한) — "~"로 열어 둔 채 보여준다
    const range = version.endDate
      ? `${formatDateWithDay(version.effectiveFrom)} ~ ${formatDateWithDay(version.endDate)}`
      : `${formatDateWithDay(version.effectiveFrom)} ~`;

    li.innerHTML = `
      <div class="version-body">
        <span class="version-badge ${BADGE_CLASS[version.status] ?? ""}">${version.statusLabel}</span>
        <p class="version-range">${range}</p>
        <p class="version-days">${version.openDayLabel ? `${escapeHtml(version.openDayLabel)} 운영` : "운영 요일 없음"}</p>
      </div>
      <div class="version-actions">
        <button type="button" class="btn outline version-detail-btn">상세보기</button>
        ${version.deletable ? '<button type="button" class="btn version-delete-btn">삭제</button>' : ""}
      </div>
      <div class="version-detail" hidden></div>
    `;
    return li;
  }

  /**
   * 예외 카드 — 버전 카드와 같은 뼈대를 쓰되 상세보기가 없다.
   * 예외는 사유와 조정 내용이 이미 한 줄로 다 들어가서 펼칠 것이 없다.
   */
  function buildExceptionHistoryItem(ex) {
    const li = document.createElement("li");
    li.className = "version-item exception-entry";
    li.dataset.exceptionId = ex.exceptionId;

    const range = ex.startDate === ex.endDate
      ? formatDateWithDay(ex.startDate)
      : `${formatDateWithDay(ex.startDate)} ~ ${formatDateWithDay(ex.endDate)}`;

    const detail = exceptionDetail(ex);

    const typeClass = { CLOSED: "type-closed", TIME_CHANGE: "type-time", SLOT_CHANGE: "type-round" }[ex.exceptionType] ?? "";

    li.innerHTML = `
      <div class="version-body">
        <span class="version-badge ${BADGE_CLASS[ex.status] ?? ""}">${ex.statusLabel}</span>
        <p class="version-range">${range}</p>
        <p class="version-days">
          <span class="exception-type ${typeClass}">${ex.exceptionTypeLabel}</span>
          ${detail ? `· ${escapeHtml(detail)}` : ""}
        </p>
      </div>
      <div class="version-actions">
        ${ex.past ? "" : '<button type="button" class="btn version-delete-btn exception-delete-btn">삭제</button>'}
      </div>
    `;
    // 휴무는 사유가 이미 카드에 적혀 있어 툴팁이 같은 말을 반복한다
    if (ex.exceptionType !== "CLOSED") setReasonTooltip(li, ex.reason);
    return li;
  }

  /**
   * 버전 상세 — 전용 API를 만들지 않고 GET /week?baseDate=적용시작일을 그대로 쓴다.
   * 그 API가 이미 "그 날짜에 유효한 주간 스케줄"을 돌려주므로 같은 값을 두 곳에서 계산할 이유가 없다.
   */
  async function toggleVersionDetail(item) {
    const detail = item.querySelector(".version-detail");
    if (!detail.hidden) {
      detail.hidden = true;
      return;
    }

    if (!detail.dataset.loaded) {
      detail.textContent = "불러오는 중…";
      detail.hidden = false;
      try {
        const week = await getJson(`${API}/week?baseDate=${item.dataset.effectiveFrom}`);
        detail.innerHTML = week.days.map(describeDay).join("");
        detail.dataset.loaded = "1";
      } catch (e) {
        detail.textContent = e.message;
        return;
      }
    }
    detail.hidden = false;
  }

  function describeDay(day) {
    const label = labelOf(day.dayOfWeek);
    if (!day.isOpen) {
      return `<p class="version-detail-row closed"><strong>${label}</strong><span>휴무</span></p>`;
    }
    const slotCount = (day.slots ?? []).length;
    return `<p class="version-detail-row"><strong>${label}</strong>`
      + `<span>${day.openTime}~${day.closeTime} · ${slotCount}회차 · 최대 ${day.defaultCapacity}명</span></p>`;
  }

  historyList.addEventListener("click", async (e) => {
    const item = e.target.closest(".version-item");
    if (!item) return;

    // 예외 삭제는 예외 패널과 같은 API를 쓴다 — 목록만 두 곳에 보일 뿐 동작은 하나다
    if (e.target.closest(".exception-delete-btn")) {
      try {
        await deleteException(Number(item.dataset.exceptionId));
        await renderHistory();
      } catch (err) {
        alert(err.message);
      }
      return;
    }

    if (e.target.closest(".version-detail-btn")) {
      await toggleVersionDetail(item);
      return;
    }

    if (e.target.closest(".version-delete-btn")) {
      const ok = await customConfirm(
        "예정된 스케줄 변경을 삭제할까요?\n삭제하면 해당 기간은 이전 스케줄대로 운영됩니다.");
      if (!ok) return;
      try {
        const message = await sendJson(`${API}/version/${item.dataset.effectiveFrom}`, undefined, "DELETE");
        alert(message);
        await renderHistory();
        await loadWeek();     // 지운 버전이 화면에 남아 있지 않도록 요일 탭도 다시 읽는다
      } catch (err) {
        alert(err.message);
      }
    }
  });

  btnRecordSchedule.addEventListener("click", () => {
    showPanel(visiblePanel === "exception" ? "history" : "exception");
  });

  /**
   * 버튼 너비를 두 라벨 중 넓은 쪽("특정일 예외 설정")에 고정한다.
   *
   * 안 하면 누를 때마다 버튼이 늘었다 줄었다 하면서 옆의 저장 버튼까지 밀린다. 폭을 CSS에
   * 픽셀로 박지 않고 재서 정하는 이유는, 글자 수가 아니라 실제 폰트 렌더링 결과가 폭을
   * 결정하기 때문이다 — 라벨 문구가 바뀌어도 이 코드는 그대로 맞는다.
   */
  function lockToggleButtonWidth() {
    const original = btnRecordSchedule.textContent;
    btnRecordSchedule.style.minWidth = "";      // 이전 측정값이 다음 측정을 부풀리지 않도록 먼저 푼다

    let widest = 0;
    Object.values(PANEL_LABEL).forEach((label) => {
      btnRecordSchedule.textContent = label;
      widest = Math.max(widest, btnRecordSchedule.getBoundingClientRect().width);
    });

    btnRecordSchedule.textContent = original;
    btnRecordSchedule.style.minWidth = `${Math.ceil(widest)}px`;
  }

  /* ── 이탈 방지 (미저장 변경 경고) ─────────────────────────────────────
     저장하지 않은 요일이 남은 채로 뒤로가기·새로고침·탭 닫기·사이드바 이동을 하면 브라우저
     기본 확인창을 띄운다. 이 화면은 요일 탭을 오가며 여러 요일을 고친 뒤 마지막에 한 번
     저장하는 구조라, 저장을 잊고 나가면 그동안의 편집이 통째로 사라진다.

     문구는 지정할 수 없다 — 예전에 이 자리에 임의 메시지를 띄우는 피싱이 많아서 브라우저들이
     자체 문구로 고정했다. preventDefault()와 returnValue를 둘 다 넣는 것은 구형 브라우저까지
     같이 지원하기 위한 관용적인 형태다. */
  window.addEventListener("beforeunload", (e) => {
    if (!state.dirty.size) return;
    e.preventDefault();
    e.returnValue = "";
  });

  /* ── 초기화 ──────────────────────────────────────────────────────────── */

  (async function init() {
    const today = todayStr();
    effectiveFromDate.value = today;
    effectiveFromDate.disabled = applyNowCheck.checked;
    exceptionStartDate.value = today;
    exceptionEndDate.value = today;
    exceptionStartDate.min = today;      // 지난 날짜 예외는 서버가 거부한다
    exceptionEndDate.min = today;
    effectiveFromDate.min = today;

    state.activeDow = new Date().getDay() === 0 ? 7 : new Date().getDay();
    applyExceptionType("closed");

    lockToggleButtonWidth();
    // 웹폰트가 늦게 붙으면 글자 폭이 달라진다. 로드 후 한 번 더 재서 맞춘다.
    document.fonts?.ready?.then(lockToggleButtonWidth);

    try {
      await loadWeek();
      await loadExceptions();
    } catch (e) {
      alert(e.message);
    }
  })();
})();
