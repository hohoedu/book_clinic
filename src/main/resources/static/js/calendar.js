/*
  정독 달력 — 앱 책방 메인의 달력 아이콘이 WebView로 여는 화면.

  데이터는 /app/calendar(POST) 한 곳에서 오고, 응답은 예약하기 화면과 같은 회차(슬롯) 목록이다.
  날짜 색은 앱 예약 달력(BookstoreReservationData.dayStatuses)과 같은 규칙으로 여기서 접는다:
    내 예약이 있으면 예약완료 > 여유 있는 열린 회차가 하나라도 있으면 예약가능 > 나머지는 마감.
  판단 기준을 옮기게 되면 앱 쪽도 같이 고쳐야 두 화면이 어긋나지 않는다.

  세션(JSESSIONID)은 앱이 WebView 쿠키로 심어준다 — 학생이 특정돼야 "예약 완료"를 칠할 수 있다.
*/
const monthSwitch = document.getElementById("monthSwitch");
const calendarGrid = document.getElementById("calendarGrid");
const scheduleBox = document.getElementById("scheduleBox");
const slotSchedule = document.getElementById("slotSchedule");

const today = new Date();
const todayKey = formatDateKey(today);

const currentMonthKey = getMonthKey(today.getFullYear(), today.getMonth() + 1);
const nextMonthDate = new Date(today.getFullYear(), today.getMonth() + 1, 1);
const nextMonthKey = getMonthKey(nextMonthDate.getFullYear(), nextMonthDate.getMonth() + 1);

const months = [currentMonthKey, nextMonthKey];
let activeMonth = months[0];

// 날짜(yyyy-MM-dd) → 'complete' | 'available' | 'closed'
let dayStatus = {};
let slotsByDate = {};
let selectedDate = null;

init();

function init() {
    renderTabs();
    renderCalendar(activeMonth);
}

function renderTabs() {
    monthSwitch.innerHTML = "";

    months.forEach((monthKey) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "month-tab" + (monthKey === activeMonth ? " is-active" : "");
        btn.textContent = formatMonth(monthKey);

        btn.addEventListener("click", () => {
            activeMonth = monthKey;
            renderTabs();
            renderCalendar(activeMonth);
        });

        monthSwitch.appendChild(btn);
    });
}

async function renderCalendar(monthKey) {
    // 다 만든 뒤 한 번에 갈아끼운다 — 먼저 비우면 응답을 기다리는 동안 달력이 빈 화면으로 깜빡인다.
    const slots = await fetchMonthSlots(monthKey);
    slotsByDate = groupByDate(slots);
    dayStatus = toDayStatus(slotsByDate);
    selectedDate = defaultSelectedDate(monthKey);

    const grid = document.createDocumentFragment();
    const [year, month] = monthKey.split("-").map(Number);
    const firstDay = new Date(year, month - 1, 1).getDay();
    const lastDate = new Date(year, month, 0).getDate();

    // 앞쪽: 이전 달 꼬리 — 날짜만 흐리게 채운다(주 단위 칸을 맞추기 위한 것)
    for (let i = firstDay; i > 0; i--) {
        const overflowDate = new Date(year, month - 1, 1 - i);
        grid.appendChild(createDayCell(overflowDate, true));
    }

    for (let day = 1; day <= lastDate; day++) {
        grid.appendChild(createDayCell(new Date(year, month - 1, day), false));
    }

    // 뒤쪽: 다음 달 머리
    const trailingCount = (7 - ((firstDay + lastDate) % 7)) % 7;
    for (let i = 1; i <= trailingCount; i++) {
        grid.appendChild(createDayCell(new Date(year, month, i), true));
    }

    calendarGrid.replaceChildren(grid);
    renderSummary(monthKey, lastDate);
    renderSlotSchedule();
}

async function fetchMonthSlots(monthKey) {
    const [year, month] = monthKey.split("-");

    try {
        const res = await fetch("/app/calendar", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            credentials: "same-origin", // 앱이 심어준 JSESSIONID 로 학생을 특정한다
            body: JSON.stringify({year: year, month: month})
        });
        const data = await res.json();
        return (data.success && data.response) ? data.response : [];
    } catch {
        return [];
    }
}

/** 회차 목록 → 날짜별 회차 목록. 서버가 날짜·회차 순으로 내려주므로 순서는 그대로 둔다. */
function groupByDate(slots) {
    const byDate = {};
    slots.forEach((slot) => {
        const date = slot.serviceDate;
        if (!date) return;
        (byDate[date] = byDate[date] || []).push(slot);
    });
    return byDate;
}

/** 이번 달이면 오늘, 아니면 그 달에 회차가 있는 첫 날을 처음 선택해 둔다. */
function defaultSelectedDate(monthKey) {
    if (todayKey.startsWith(monthKey)) return todayKey;
    return Object.keys(slotsByDate).sort()[0] || null;
}

/** 날짜별 회차 목록 → 날짜별 상태. 앱 BookstoreReservationData.dayStatuses 와 같은 규칙. */
function toDayStatus(byDate) {
    const status = {};
    Object.keys(byDate).forEach((date) => {
        const daySlots = byDate[date];
        if (daySlots.some((s) => s.reservedByMe)) {
            status[date] = "complete";
        } else if (daySlots.some(isOpen)) {
            status[date] = "available";
        } else {
            status[date] = "closed";
        }
    });
    return status;
}

/** 앱 SlotOption.isOpen 과 같은 판단 — 열려 있고 자리가 남았고 내 예약이 아닌 회차. */
function isOpen(slot) {
    return slot.status === "OPEN" && (slot.capacity - slot.reservedCount) > 0 && !slot.reservedByMe;
}

function createDayCell(date, isOverflowDate) {
    const dateKey = formatDateKey(date);
    const weekday = date.getDay();

    const cell = document.createElement("div");
    cell.className = "day-cell" + (isOverflowDate ? " other-month" : "");
    if (!isOverflowDate && dateKey === todayKey) cell.classList.add("today");

    const chip = document.createElement("div");
    chip.className = "day-chip";
    if (weekday === 0) chip.classList.add("sun");
    if (weekday === 6) chip.classList.add("sat");

    const status = isOverflowDate ? null : dayStatus[dateKey];
    if (status) chip.classList.add(status);

    chip.textContent = date.getDate();
    cell.appendChild(chip);

    if (!isOverflowDate) {
        cell.dataset.date = dateKey;
        if (dateKey === selectedDate) cell.classList.add("selected");
        cell.addEventListener("click", () => selectDate(dateKey));
    }
    return cell;
}

function renderSummary(monthKey, lastDate) {
    const [year, month] = monthKey.split("-").map(Number);

    let reserved = 0;
    let available = 0;

    for (let day = 1; day <= lastDate; day++) {
        const status = dayStatus[formatDateKey(new Date(year, month - 1, day))];
        if (status === "complete") reserved++;
        if (status === "available") available++;
    }

    scheduleBox.textContent = reserved === 0 && available === 0
        ? `${month}월은 예약할 수 있는 일정이 없습니다.`
        : `${month}월 예약 ${reserved}일 · 예약 가능 ${available}일`;
}

function selectDate(dateKey) {
    selectedDate = dateKey;
    calendarGrid.querySelectorAll(".day-cell.selected").forEach((c) => c.classList.remove("selected"));
    const cell = calendarGrid.querySelector(`.day-cell[data-date="${dateKey}"]`);
    if (cell) cell.classList.add("selected");
    renderSlotSchedule();
}

/** 선택한 날짜의 회차 스케줄 — 학생 센터에서 그날 열린 회차만 내려온다. */
function renderSlotSchedule() {
    const list = document.createDocumentFragment();

    const title = document.createElement("li");
    title.className = "slot-title";
    title.textContent = selectedDate ? formatDayTitle(selectedDate) : "날짜를 선택해 주세요.";
    list.appendChild(title);

    const daySlots = selectedDate ? (slotsByDate[selectedDate] || []) : [];
    if (selectedDate && daySlots.length === 0) {
        const empty = document.createElement("li");
        empty.className = "slot-empty";
        empty.textContent = "운영하는 회차가 없습니다.";
        list.appendChild(empty);
    }

    daySlots.forEach((slot) => {
        const row = document.createElement("li");

        const label = document.createElement("span");
        label.className = "slot-seq";
        label.textContent = `${slot.seq}회차`;

        const time = document.createElement("span");
        time.className = "slot-time";
        time.textContent = `${formatTime(slot.startsAt)} ~ ${formatTime(slot.endsAt)}`;

        const state = slotState(slot);
        const badge = document.createElement("span");
        badge.className = `slot-badge ${state.key}`;
        badge.textContent = state.label;

        row.append(label, time, badge);
        list.appendChild(row);
    });

    slotSchedule.replaceChildren(list);
}

/** 회차 하나의 상태 — 날짜 색과 같은 우선순위(내 예약 > 예약 가능 > 마감). */
function slotState(slot) {
    if (slot.reservedByMe) return {key: "complete", label: "예약 완료"};
    if (isOpen(slot)) return {key: "available", label: `잔여 ${slot.capacity - slot.reservedCount}석`};
    return {key: "closed", label: "마감"};
}

/** "2026-10-13" → "10월 13일 (화)" */
function formatDayTitle(dateKey) {
    const [y, m, d] = dateKey.split("-").map(Number);
    const weekday = "일월화수목금토"[new Date(y, m - 1, d).getDay()];
    return `${m}월 ${d}일 (${weekday})`;
}

/** "2026-10-13T14:00:00" → "14:00" */
function formatTime(dateTime) {
    return String(dateTime).slice(11, 16);
}

function getMonthKey(year, month) {
    const date = new Date(year, month - 1, 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

function formatMonth(monthKey) {
    return `${Number(monthKey.split("-")[1])}월`;
}

function formatDateKey(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
