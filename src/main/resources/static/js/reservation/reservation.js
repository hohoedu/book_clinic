document.addEventListener("DOMContentLoaded", async () => {
  initDatePicker();
  initSlotPicker();
  initSearch();
  await loadReservationList();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
// 서버가 세션마다 다른 값을 XSRF-TOKEN 쿠키로 내려준다(CookieCsrfTokenRepository, 2026-07-31) —
// 예전처럼 고정 문자열을 하드코딩하지 않고 매 요청마다 쿠키에서 읽는다.
function getCsrfToken() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}

let selectedStudent = null;

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

function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

// 서버가 내려주는 "yyyy-MM-ddTHH:mm:ss" 문자열에서 "HH:mm"만 잘라 쓴다
function formatTime(isoDateTime) {
  return isoDateTime ? isoDateTime.slice(11, 16) : "";
}

function initDatePicker() {
  const input = document.getElementById("reservationDate");
  const display = document.getElementById("reservationDateDisplay");
  const trigger = document.querySelector(".monitor-date-trigger");

  input.value = todayStr();
  updateDateDisplay(input, display);

  trigger.addEventListener("click", () => input.showPicker());

  input.addEventListener("change", async () => {
    updateDateDisplay(input, display);
    await loadReservationList();
    // 날짜가 바뀌면 이전 날짜 기준으로 골라둔 회차는 더 이상 유효하지 않다
    if (selectedStudent) await loadSlotOptions();
  });
}

function updateDateDisplay(input, display) {
  const [y, m, d] = input.value.split("-");
  display.textContent = `${y}. ${m}. ${d}`;
}

function selectedDate() {
  return document.getElementById("reservationDate").value || todayStr();
}

/* ── 회차 선택 ──
   old(TIME_SLOTS 고정 4개) 대신, 신규 예약 스키마는 센터마다 회차 수·시간이 달라 화면 로드
   시점엔 무엇을 보여줄지 알 수 없다. 학생을 고르면 그 학생 센터의 그 날짜 회차를 서버에서
   받아와 채운다(2026-08-18). */
function initSlotPicker() {
  const select = document.getElementById("reservationSlot");
  select.innerHTML = `<option value="">학생을 먼저 선택해주세요</option>`;
  select.disabled = true;
}

async function loadSlotOptions() {
  const select = document.getElementById("reservationSlot");
  select.innerHTML = `<option value="">불러오는 중…</option>`;
  select.disabled = true;

  try {
    const date = selectedDate();
    const slots = await getJson(
      `/admin/monitor/reservation/slots?studentId=${encodeURIComponent(selectedStudent.studentId)}&fromDate=${date}&toDate=${date}`
    );

    select.innerHTML = "";
    if (slots.length === 0) {
      select.innerHTML = `<option value="">이 날짜에 열린 회차가 없습니다</option>`;
      return;
    }

    slots.forEach((slot) => {
      const option = document.createElement("option");
      option.value = slot.slotInstanceId;
      const full = slot.reservedCount >= slot.capacity;
      const already = slot.reservedByMe;
      option.textContent =
        `${slot.seq}회차 (${formatTime(slot.startsAt)}~${formatTime(slot.endsAt)})` +
        ` · ${slot.reservedCount}/${slot.capacity}` +
        (already ? " · 이미 예약됨" : full ? " · 마감" : "");
      option.disabled = full || already;
      select.appendChild(option);
    });
    select.disabled = false;
  } catch (e) {
    select.innerHTML = `<option value="">회차를 불러오지 못했습니다</option>`;
    console.error("회차 조회 실패", e);
  }
}

/* ── 학생 검색 ── */

function initSearch() {
  const keywordInput = document.getElementById("studentKeyword");
  document.getElementById("btnSearchStudent").addEventListener("click", searchStudents);
  keywordInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") searchStudents();
  });
  document.getElementById("btnRegisterReservation").addEventListener("click", registerReservation);
}

async function searchStudents() {
  const keyword = document.getElementById("studentKeyword").value.trim();
  if (!keyword) return;

  try {
    const students = await getJson(`/admin/monitor/reservation/students?keyword=${encodeURIComponent(keyword)}`);
    renderSearchResults(students);
  } catch (e) {
    alert(e.message);
  }
}

function renderSearchResults(students) {
  const list = document.getElementById("studentSearchResults");
  list.innerHTML = "";

  if (students.length === 0) {
    list.innerHTML = `<li class="empty">검색 결과가 없습니다.</li>`;
    return;
  }

  students.forEach((student) => {
    const li = document.createElement("li");
    li.innerHTML = `
      <span class="result-name">${student.studentName}</span>
      <span class="result-sub">${[student.school, student.gradeKey].filter(Boolean).join(" · ")}</span>
    `;
    li.addEventListener("click", () => selectStudent(student));
    list.appendChild(li);
  });
}

async function selectStudent(student) {
  selectedStudent = student;
  document.getElementById("selectedStudentName").textContent = `${student.studentName} (${student.appId ?? ""})`;
  document.getElementById("selectedStudentBox").hidden = false;
  await loadSlotOptions();
}

async function registerReservation() {
  if (!selectedStudent) return;

  const slotInstanceId = document.getElementById("reservationSlot").value;
  if (!slotInstanceId) {
    alert("예약할 회차를 선택해주세요.");
    return;
  }

  try {
    await postJson("/admin/monitor/reservation/register", {
      studentId: selectedStudent.studentId,
      slotInstanceId: Number(slotInstanceId),
    });
    selectedStudent = null;
    document.getElementById("selectedStudentBox").hidden = true;
    document.getElementById("studentKeyword").value = "";
    document.getElementById("studentSearchResults").innerHTML = "";
    initSlotPicker();
    await loadReservationList();
  } catch (e) {
    alert(e.message);
  }
}

/* ── 예약 목록 ── */

async function loadReservationList() {
  try {
    const list = await getJson(`/admin/monitor/reservation/list?date=${selectedDate()}`);
    renderReservationList(list);
  } catch (e) {
    console.error("예약 목록 조회 실패", e);
  }
}

function renderReservationList(list) {
  const body = document.getElementById("reservationListBody");
  body.innerHTML = "";

  if (list.length === 0) {
    body.innerHTML = `<tr><td colspan="5" class="empty">등록된 예약이 없습니다.</td></tr>`;
    return;
  }

  list.forEach((row) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${row.seq}회차 (${formatTime(row.startsAt)}~${formatTime(row.endsAt)})</td>
      <td>${row.studentName ?? ""}</td>
      <td>${row.school ?? "-"}</td>
      <td>${row.gradeKey ?? "-"}</td>
      <td><button type="button" class="btn outline small btn-delete">취소</button></td>
    `;
    tr.querySelector(".btn-delete").addEventListener("click", () => cancelReservation(row.reservationId, row.studentId));
    body.appendChild(tr);
  });
}

async function cancelReservation(reservationId, studentId) {
  if (!confirm("이 예약을 취소할까요?")) return;

  try {
    await postJson("/admin/monitor/reservation/cancel", { reservationId, studentId });
    await loadReservationList();
  } catch (e) {
    alert(e.message);
  }
}
