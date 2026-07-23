document.addEventListener("DOMContentLoaded", async () => {
  initDatePicker();
  initSlotPicker();
  initSearch();
  await loadReservationList();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TOKEN = "hohoedu-master-csrf-token";

// 교시 마스터 데이터가 아직 없어 고정 목록으로 둔다 — monitor-live.js의 TIME_SLOTS와 값('1'~'4')을 맞춘다
const TIME_SLOTS = [
  { key: "1", label: "1교시(14:00~15:00)" },
  { key: "2", label: "2교시(15:00~16:00)" },
  { key: "3", label: "3교시(16:00~17:00)" },
  { key: "4", label: "4교시(17:00~18:00)" },
];

let selectedStudent = null;

/* 공통 요청 헬퍼 */
async function getJson(url) {
  const response = await fetch(url);
  const data = await response.json();
  if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");
  return data.response;
}

async function postJson(url, body, method = "POST") {
  const response = await fetch(url, {
    method,
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
  const input = document.getElementById("reservationDate");
  const display = document.getElementById("reservationDateDisplay");
  const trigger = document.querySelector(".monitor-date-trigger");

  input.value = todayStr();
  updateDateDisplay(input, display);

  trigger.addEventListener("click", () => input.showPicker());

  input.addEventListener("change", async () => {
    updateDateDisplay(input, display);
    await loadReservationList();
  });
}

function updateDateDisplay(input, display) {
  const [y, m, d] = input.value.split("-");
  display.textContent = `${y}. ${m}. ${d}`;
}

function selectedDate() {
  return document.getElementById("reservationDate").value || todayStr();
}

function initSlotPicker() {
  const select = document.getElementById("reservationSlot");
  select.innerHTML = "";
  TIME_SLOTS.forEach(({ key, label }) => {
    const option = document.createElement("option");
    option.value = key;
    option.textContent = label;
    select.appendChild(option);
  });
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

function selectStudent(student) {
  selectedStudent = student;
  document.getElementById("selectedStudentName").textContent = `${student.studentName} (${student.appId ?? ""})`;
  document.getElementById("selectedStudentBox").hidden = false;
}

async function registerReservation() {
  if (!selectedStudent) return;

  const timeSlot = document.getElementById("reservationSlot").value;
  try {
    await postJson("/admin/monitor/reservation/register", {
      studentId: selectedStudent.studentId,
      reservationDate: selectedDate(),
      timeSlot,
    });
    selectedStudent = null;
    document.getElementById("selectedStudentBox").hidden = true;
    document.getElementById("studentKeyword").value = "";
    document.getElementById("studentSearchResults").innerHTML = "";
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

function slotLabel(key) {
  return TIME_SLOTS.find((s) => s.key === key)?.label ?? key;
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
      <td>${slotLabel(row.timeSlot)}</td>
      <td>${row.studentName ?? ""}</td>
      <td>${row.school ?? "-"}</td>
      <td>${row.gradeKey ?? "-"}</td>
      <td><button type="button" class="btn outline small btn-delete">삭제</button></td>
    `;
    tr.querySelector(".btn-delete").addEventListener("click", () => deleteReservation(row.reservationId));
    body.appendChild(tr);
  });
}

async function deleteReservation(reservationId) {
  if (!confirm("이 예약을 삭제할까요?")) return;

  try {
    await postJson("/admin/monitor/reservation/delete", { reservationId }, "DELETE");
    await loadReservationList();
  } catch (e) {
    alert(e.message);
  }
}
