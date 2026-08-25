/*
 * 예약 현황 — 서버 연동 (2026-08-19)
 *
 * [화면이 다루는 두 축] 왼쪽은 "특정 날짜"의 스냅샷(회차별 정원 카드 + 그날 예약자 목록)이고,
 * 오른쪽 등록/변경 패널은 "등록 날짜"라는 별도의 날짜를 다룬다. 둘을 같은 변수로 묶지 않는다 —
 * 예를 들어 8/14 목록을 보면서 8/21로 새로 등록하는 것이 정상 흐름이기 때문이다.
 *
 * [예약 변경 = 취소 후 재등록] 서버에 "변경" 전용 API가 없다. 새 회차를 먼저 register해서
 * 자리를 확보한 뒤에 기존 예약을 cancel한다 — 순서를 반대로 하면 새 회차가 마감이었을 때
 * 기존 예약까지 잃는다.
 */
(function () {
  "use strict";

  const API = "/admin/monitor/reservation";
  const CSRF_HEADER = "X-XSRF-TOKEN";

  const GRADE_LABEL = {
    "01": "초1", "02": "초2", "03": "초3", "04": "초4",
    "05": "초5", "06": "초6", "07": "중등",
  };

  const STATUS_META = {
    RESERVED: { label: "예약 완료", cls: "status-complete" },
    ATTENDED: { label: "이용 완료", cls: "status-used" },
    CANCELED: { label: "예약 취소", cls: "status-cancelled" },
    NOSHOW: { label: "결석", cls: "status-absent" },
  };

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

  function todayStr(d = new Date()) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  const DAY_LABEL_SHORT = ["일", "월", "화", "수", "목", "금", "토"];

  function dateWithDow(dateStr) {
    if (!dateStr) return "";
    const d = new Date(`${dateStr}T00:00:00`);
    return `${dateStr} (${DAY_LABEL_SHORT[d.getDay()]})`;
  }

  function timeOf(iso) {
    if (!iso) return "";
    return iso.slice(11, 16);
  }

  function timeRange(startsAt, endsAt) {
    return `${timeOf(startsAt)} ~ ${timeOf(endsAt)}`;
  }

  function gradeLabel(gradeKey) {
    return GRADE_LABEL[gradeKey] || gradeKey || "-";
  }

  // ── 전역 상태 ────────────────────────────────────────────────────────────
  const state = {
    date: todayStr(),
    reservations: [],
    slots: [],
    selectedSeq: null,
    panelMode: "create",
    selectedRow: null,
    changeDate: todayStr(),
    changeRoundPickSlots: [],
    changeDayReservedByRound: new Map(), // seq -> Set(studentId), 등록 대상 제외용
    checkedStudents: new Map(), // studentId -> studentObj
  };

  // ── DOM ──────────────────────────────────────────────────────────────────
  const el = {
    dateInput: document.getElementById("reservationDate"),
    dateDisplay: document.getElementById("reservationDateDisplay"),
    searchInput: document.getElementById("studentSearchKeyword"),
    btnSearch: document.getElementById("btnSearch"),
    roundSummaryDesc: document.getElementById("roundSummaryDesc"),
    roundCardGrid: document.getElementById("roundCardGrid"),
    listRoundLabel: document.getElementById("listRoundLabel"),
    listRoundSub: document.getElementById("listRoundSub"),
    tbody: document.getElementById("reservationListBody"),
    btnAddReservation: document.getElementById("btnAddReservation"),

    changePanelTitle: document.getElementById("changePanelTitle"),
    changePanelDesc: document.getElementById("changePanelDesc"),
    changeDateLabel: document.getElementById("changeDateLabel"),
    changeRoundLabel: document.getElementById("changeRoundLabel"),
    changeReasonLabel: document.getElementById("changeReasonLabel"),
    btnSaveChange: document.getElementById("btnSaveChange"),
    btnCancelReservation: document.getElementById("btnCancelReservation"),
    currentReservationBox: document.getElementById("currentReservationBox"),
    changeTargetName: document.getElementById("changeTargetName"),
    currentResDate: document.getElementById("currentResDate"),
    currentResRound: document.getElementById("currentResRound"),
    changeDateInput: document.getElementById("changeDate"),
    roundPickList: document.getElementById("roundPickList"),
    studentPickGroup: document.getElementById("studentPickGroup"),
    studentPickSearch: document.getElementById("studentPickSearch"),
    btnStudentPickSearch: document.getElementById("btnStudentPickSearch"),
    studentPickBody: document.getElementById("studentPickBody"),
    changeReasonGroup: document.getElementById("changeReasonGroup"),
    changeReason: document.getElementById("changeReason"),
  };

  document.addEventListener("DOMContentLoaded", () => {
    state.date = todayStr();
    state.changeDate = todayStr();
    el.dateInput.value = state.date;
    el.dateDisplay.textContent = state.date;
    el.changeDateInput.value = state.changeDate;

    initDatePicker();
    initDateNav();
    initSearch();
    initChangePanelMode();
    initStudentPickSearch();

    loadDay();
  });

  // ── 날짜 툴바 ────────────────────────────────────────────────────────────
  function initDatePicker() {
    const trigger = document.querySelector(".monitor-date-trigger");
    trigger.addEventListener("click", () => el.dateInput.showPicker());
    el.dateInput.addEventListener("change", () => {
      state.date = el.dateInput.value;
      el.dateDisplay.textContent = state.date;
      loadDay();
    });
  }

  function initDateNav() {
    const shiftDay = (days) => {
      const current = new Date(`${el.dateInput.value || todayStr()}T00:00:00`);
      current.setDate(current.getDate() + days);
      el.dateInput.value = todayStr(current);
      el.dateInput.dispatchEvent(new Event("change"));
    };
    document.getElementById("btnPrevDay").addEventListener("click", () => shiftDay(-1));
    document.getElementById("btnNextDay").addEventListener("click", () => shiftDay(1));
    document.getElementById("btnToday").addEventListener("click", () => {
      el.dateInput.value = todayStr();
      el.dateInput.dispatchEvent(new Event("change"));
    });
  }

  function initSearch() {
    const apply = () => renderReservationTable();
    el.btnSearch.addEventListener("click", apply);
    el.searchInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") apply();
    });
  }

  // ── 날짜 데이터 로드 ─────────────────────────────────────────────────────
  async function loadDay() {
    state.selectedSeq = null;
    el.roundSummaryDesc.textContent = "불러오는 중...";
    el.roundCardGrid.innerHTML = "";
    el.tbody.innerHTML = "";

    try {
      const [slots, reservations] = await Promise.all([
        getJson(`${API}/summary?date=${state.date}`),
        getJson(`${API}/list?date=${state.date}`),
      ]);
      state.slots = slots;
      state.reservations = reservations;
      renderRoundSummary();
      renderRoundCards();
      renderReservationTable();
    } catch (err) {
      el.roundSummaryDesc.textContent = err.message;
    }
  }

  function renderRoundSummary() {
    if (state.slots.length === 0) {
      el.roundSummaryDesc.textContent = "선택한 날짜는 운영 회차가 없습니다.";
      return;
    }
    const starts = state.slots.map((s) => s.startsAt).sort();
    const ends = state.slots.map((s) => s.endsAt).sort();
    el.roundSummaryDesc.textContent =
      `선택한 날짜는 총 ${state.slots.length}회차 운영이며 ${timeOf(starts[0])}부터 ${timeOf(ends[ends.length - 1])}까지 진행됩니다.`;
  }

  function slotBadge(slot) {
    const ended = new Date(slot.endsAt) <= new Date();
    if (ended || slot.status !== "OPEN" || slot.reservedCount >= slot.capacity) {
      return { state: "full", label: "마감" };
    }
    if (slot.reservedCount === 0) {
      if (state.date === todayStr()) return { state: "none", label: "없음" };
      return { state: "available", label: "여유" };
    }
    if (slot.reservedCount / slot.capacity >= 0.8) return { state: "soon", label: "임박" };
    return { state: "available", label: "여유" };
  }

  function renderRoundCards() {
    el.roundCardGrid.innerHTML = "";
    state.slots.forEach((slot) => {
      const badge = slotBadge(slot);
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "round-status-card" + (state.selectedSeq === slot.seq ? " active" : "");
      btn.dataset.state = badge.state;
      btn.innerHTML = `
        <div class="round-status-top">
          <span class="round-status-no">${slot.seq}회차</span>
          <span class="round-status-badge badge-${badge.state}">${badge.label}</span>
        </div>
        <span class="round-status-time">${timeRange(slot.startsAt, slot.endsAt)}</span>
        <span class="round-status-count">${slot.reservedCount} <em>/ ${slot.capacity}명</em></span>
      `;
      btn.addEventListener("click", () => {
        state.selectedSeq = state.selectedSeq === slot.seq ? null : slot.seq;
        renderRoundCards();
        renderReservationTable();
      });
      el.roundCardGrid.appendChild(btn);
    });
  }

  function renderReservationTable() {
    const selectedSlot = state.slots.find((s) => s.seq === state.selectedSeq);
    if (selectedSlot) {
      el.listRoundLabel.textContent = `${selectedSlot.seq}회차`;
      el.listRoundSub.textContent =
        `| ${timeRange(selectedSlot.startsAt, selectedSlot.endsAt)} | 예약 ${selectedSlot.reservedCount}/${selectedSlot.capacity}명`;
    } else {
      el.listRoundLabel.textContent = "전체";
      el.listRoundSub.textContent = `| 예약 ${state.reservations.length}건`;
    }

    const keyword = el.searchInput.value.trim();
    const rows = state.reservations.filter((r) => {
      if (state.selectedSeq !== null && r.seq !== state.selectedSeq) return false;
      if (keyword && !r.studentName.includes(keyword)) return false;
      return true;
    });

    el.tbody.innerHTML = "";
    if (rows.length === 0) {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td colspan="9" style="text-align:center; color:#9AA0A6; padding:64px 0;">예약된 학생이 없습니다.</td>`;
      el.tbody.appendChild(tr);
      return;
    }

    rows.forEach((r, idx) => {
      const meta = STATUS_META[r.status] || { label: r.status, cls: "status-complete" };
      const isCenter = r.channel === "ADMIN";
      const tr = document.createElement("tr");
      if (state.selectedRow && state.selectedRow.reservationId === r.reservationId) tr.classList.add("selected");
      tr.innerHTML = `
        <td>${idx + 1}</td>
        <td class="cell-name">${r.studentName}</td>
        <td>${gradeLabel(r.gradeKey)}</td>
        <td>${r.contact || "-"}</td>
        <td${isCenter ? ' class="cell-center"' : ""}>${isCenter ? "센터 예약" : "직접 예약"}</td>
        <td><span class="status-pill ${meta.cls}">${meta.label}</span></td>
        <td>${r.reason || ""}</td>
        <td>${r.monthlyAttendCount ?? 0}회</td>
        <td><button type="button" class="btn outline small btn-row-change">예약 변경</button></td>
      `;
      tr.querySelector(".btn-row-change").addEventListener("click", () => selectRowForChange(r));
      el.tbody.appendChild(tr);
    });
  }

  // ── 등록/변경 패널 ───────────────────────────────────────────────────────
  const CHANGE_PANEL_TEXT = {
    create: {
      title: "예약 등록", desc: "학생을 선택한 날짜/회차에 새로 등록합니다.",
      dateLabel: "등록 날짜", roundLabel: "등록 회차", reasonLabel: "등록 사유", saveLabel: "예약 등록",
    },
    change: {
      title: "예약 변경", desc: "선택한 학생의 예약을 다른 날짜/회차로 변경합니다.",
      dateLabel: "변경 날짜", roundLabel: "변경 회차", reasonLabel: "변경 사유", saveLabel: "예약 변경",
    },
  };

  function initChangePanelMode() {
    el.btnAddReservation.addEventListener("click", () => {
      state.selectedRow = null;
      setChangePanelMode("create");
    });
    el.changeDateInput.addEventListener("change", () => {
      state.changeDate = el.changeDateInput.value;
      loadChangeDateData();
    });
    el.btnSaveChange.addEventListener("click", onSaveChange);
    el.btnCancelReservation.addEventListener("click", onCancelReservation);

    setChangePanelMode("create");
    loadChangeDateData();
  }

  function setChangePanelMode(mode) {
    state.panelMode = mode;
    const text = CHANGE_PANEL_TEXT[mode];
    el.changePanelTitle.textContent = text.title;
    el.changePanelDesc.textContent = text.desc;
    el.changeDateLabel.textContent = text.dateLabel;
    el.changeRoundLabel.textContent = text.roundLabel;
    el.changeReasonLabel.textContent = text.reasonLabel;
    el.btnSaveChange.textContent = text.saveLabel;
    el.currentReservationBox.hidden = mode !== "change";
    el.btnCancelReservation.hidden = mode !== "change";
    el.changeReasonGroup.hidden = mode !== "change";
    el.studentPickGroup.hidden = mode !== "create";
    el.changeReason.value = "";
    state.checkedStudents.clear();
    renderRoundPickList();

    if (mode === "create") {
      el.studentPickSearch.value = "";
      searchStudentsForPick();
    }
  }

  function selectRowForChange(row) {
    state.selectedRow = row;
    el.changeTargetName.textContent = row.studentName;
    el.currentResDate.textContent = dateWithDow(state.date);
    el.currentResRound.textContent = `${row.seq}회차 ${timeRange(row.startsAt, row.endsAt)}`;
    setChangePanelMode("change");
    renderReservationTable();
  }

  async function loadChangeDateData() {
    el.roundPickList.innerHTML = `<li class="round-pick disabled"><label><span class="round-pick-left"><span class="round-pick-time">불러오는 중...</span></span></label></li>`;
    try {
      const studentIdForSlots = state.panelMode === "change" && state.selectedRow ? state.selectedRow.studentId : "";
      const [slots, dayReservations] = await Promise.all([
        getJson(`${API}/slots?studentId=${encodeURIComponent(studentIdForSlots)}&fromDate=${state.changeDate}&toDate=${state.changeDate}`),
        getJson(`${API}/list?date=${state.changeDate}`),
      ]);
      state.changeRoundPickSlots = slots;
      state.changeDayReservedByRound = new Map();
      dayReservations
        .filter((r) => r.status === "RESERVED")
        .forEach((r) => {
          if (!state.changeDayReservedByRound.has(r.seq)) state.changeDayReservedByRound.set(r.seq, new Set());
          state.changeDayReservedByRound.get(r.seq).add(r.studentId);
        });
      renderRoundPickList();
    } catch (err) {
      el.roundPickList.innerHTML = `<li class="round-pick disabled"><label><span class="round-pick-left"><span class="round-pick-time">${err.message}</span></span></label></li>`;
    }
  }

  function renderRoundPickList() {
    el.roundPickList.innerHTML = "";
    if (state.changeRoundPickSlots.length === 0) {
      el.roundPickList.innerHTML = `<li class="round-pick disabled"><label><span class="round-pick-left"><span class="round-pick-time">이 날짜엔 예약 가능한 회차가 없습니다.</span></span></label></li>`;
      return;
    }
    const firstOpenIdx = state.changeRoundPickSlots.findIndex(
      (s) => s.status === "OPEN" && s.reservedCount < s.capacity
    );
    state.changeRoundPickSlots.forEach((slot, idx) => {
      const full = slot.status !== "OPEN" || slot.reservedCount >= slot.capacity;
      const li = document.createElement("li");
      li.className = "round-pick" + (full ? " disabled" : "");
      li.innerHTML = `
        <label>
          <input type="radio" name="changeRound" value="${slot.slotInstanceId}" ${full ? "disabled" : ""} ${idx === firstOpenIdx ? "checked" : ""} />
          <span class="round-pick-left">
            <span class="round-pick-checkbox"></span>
            <span class="round-pick-time">${slot.seq}회차 ${timeRange(slot.startsAt, slot.endsAt)}</span>
          </span>
          <span class="round-pick-remain">${full ? "예약 마감" : `잔여 ${slot.capacity - slot.reservedCount}명`}</span>
        </label>
      `;
      if (!full) {
        li.addEventListener("click", () => {
          el.roundPickList.querySelectorAll(".round-pick").forEach((i) => i.classList.remove("checked"));
          li.querySelector("input").checked = true;
          li.classList.add("checked");
          renderStudentPickList();
        });
      }
      el.roundPickList.appendChild(li);
    });
    const firstOpen = el.roundPickList.querySelector(".round-pick:not(.disabled)");
    if (firstOpen) firstOpen.classList.add("checked");
    renderStudentPickList();
  }

  function selectedRoundSeq() {
    const checked = el.roundPickList.querySelector("input[name='changeRound']:checked");
    if (!checked) return null;
    const slot = state.changeRoundPickSlots.find((s) => String(s.slotInstanceId) === checked.value);
    return slot ? slot.seq : null;
  }

  // ── 학생 검색(등록 모드) ─────────────────────────────────────────────────
  // 새로고침해도 검색어가 남아있도록 sessionStorage에 저장한다 — 등록 실패 후 습관적으로
  // 새로고침해버리면 검색어까지 날아가 "누구를 찾고 있었는지"부터 다시 해야 했다(2026-08-21).
  const STUDENT_PICK_KEYWORD_KEY = "reservation.studentPickKeyword";

  function saveStudentPickKeyword(keyword) {
    try {
      if (keyword) sessionStorage.setItem(STUDENT_PICK_KEYWORD_KEY, keyword);
      else sessionStorage.removeItem(STUDENT_PICK_KEYWORD_KEY);
    } catch (ignored) {
      // 시크릿 모드 등 sessionStorage 접근이 막힌 환경 — 새로고침 유지만 안 될 뿐 기능엔 지장 없다
    }
  }

  function loadStudentPickKeyword() {
    try {
      return sessionStorage.getItem(STUDENT_PICK_KEYWORD_KEY) || "";
    } catch (ignored) {
      return "";
    }
  }

  function clearSavedStudentPickKeyword() {
    saveStudentPickKeyword("");
  }

  function initStudentPickSearch() {
    const apply = () => searchStudentsForPick();
    el.btnStudentPickSearch.addEventListener("click", apply);
    el.studentPickSearch.addEventListener("keydown", (e) => {
      if (e.key === "Enter") apply();
    });

    // setChangePanelMode("create")가 초기 진입 시 검색창을 비우고 한 번 검색하는데, 저장된
    // 검색어가 있으면 그걸로 덮어써서 다시 채운다.
    const savedKeyword = loadStudentPickKeyword();
    if (savedKeyword) {
      el.studentPickSearch.value = savedKeyword;
      searchStudentsForPick();
    }
  }

  let studentPickResults = [];

  async function searchStudentsForPick() {
    const keyword = el.studentPickSearch.value.trim();
    try {
      studentPickResults = await getJson(`${API}/students?keyword=${encodeURIComponent(keyword)}`);
      saveStudentPickKeyword(keyword);
      renderStudentPickList();
    } catch (err) {
      el.studentPickBody.innerHTML = `<tr><td colspan="4" style="text-align:center;">${err.message}</td></tr>`;
    }
  }

  function renderStudentPickList() {
    if (state.panelMode !== "create") return;
    const seq = selectedRoundSeq();
    const excluded = seq !== null ? state.changeDayReservedByRound.get(seq) : null;

    el.studentPickBody.innerHTML = "";
    const visible = studentPickResults.filter((s) => !excluded || !excluded.has(s.studentId));
    if (visible.length === 0) {
      el.studentPickBody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:#9AA0A6;">${studentPickResults.length === 0 ? "검색된 학생이 없습니다." : "선택한 회차에 등록 가능한 학생이 없습니다."}</td></tr>`;
      return;
    }
    visible.forEach((s) => {
      const tr = document.createElement("tr");
      tr.className = "student-pick-item";
      if (state.checkedStudents.has(s.studentId)) tr.classList.add("checked");
      tr.innerHTML = `
        <td><span class="student-pick-checkbox"></span><input type="checkbox" name="pickStudent" value="${s.studentId}" ${state.checkedStudents.has(s.studentId) ? "checked" : ""} /></td>
        <td class="student-pick-name">${s.studentName}</td>
        <td class="student-pick-grade">${gradeLabel(s.gradeKey)}</td>
        <td class="student-pick-contact">${s.contact || "-"}</td>
      `;
      const input = tr.querySelector("input");
      const toggle = () => {
        input.checked = !input.checked;
        input.dispatchEvent(new Event("change"));
      };
      tr.addEventListener("click", (e) => {
        if (e.target === input) return;
        toggle();
      });
      input.addEventListener("change", () => {
        tr.classList.toggle("checked", input.checked);
        if (input.checked) state.checkedStudents.set(s.studentId, s);
        else state.checkedStudents.delete(s.studentId);
      });
      el.studentPickBody.appendChild(tr);
    });
  }

  // ── 등록/변경/취소 액션 ──────────────────────────────────────────────────
  async function onSaveChange() {
    const checked = el.roundPickList.querySelector("input[name='changeRound']:checked");
    if (!checked) {
      alert("등록할 회차를 선택해 주세요.");
      return;
    }
    const slotInstanceId = Number(checked.value);

    if (state.panelMode === "create") {
      const studentIds = [...state.checkedStudents.keys()];
      if (studentIds.length === 0) {
        alert("등록할 학생을 선택해 주세요.");
        return;
      }
      const failures = [];
      for (const studentId of studentIds) {
        try {
          await sendJson(`${API}/register`, { studentId, slotInstanceId });
          state.checkedStudents.delete(studentId);
        } catch (err) {
          const student = state.checkedStudents.get(studentId);
          failures.push(`${student ? student.studentName : studentId}: ${err.message}`);
        }
      }
      if (failures.length > 0) {
        // 실패한 학생은 검색 결과·선택 상태를 그대로 남겨서 다시 눌러 재시도할 수 있게 한다.
        // 전부 지워버리면 "검색된 학생이 없습니다"만 남아 실패 사실도, 대상 학생도 알 수 없게 된다.
        alert(`일부 등록에 실패했습니다.\n${failures.join("\n")}`);
        renderStudentPickList();
      } else {
        studentPickResults = [];
        el.studentPickSearch.value = "";
        clearSavedStudentPickKeyword();
      }
      await loadDay();
      await loadChangeDateData();
    } else {
      const row = state.selectedRow;
      if (!row) return;
      if (!el.changeReason.value.trim()) {
        alert("변경 사유를 입력해 주세요.");
        return;
      }

      // "하루 1회차만" 정책 때문에 새 회차가 기존 예약과 같은 날짜면 기존 예약을 먼저 취소해야
      // register가 통과한다(취소 전엔 같은 날 두 회차를 동시에 들고 있을 수 없다고 서버가 막는다).
      // 반대로 날짜가 다르면 기존 register-먼저 순서를 유지해 새 회차가 마감이었을 때 기존 예약을
      // 잃지 않게 한다.
      const targetSlot = state.changeRoundPickSlots.find((s) => s.slotInstanceId === slotInstanceId);
      const sameDay = targetSlot && targetSlot.serviceDate === state.date;
      const reason = el.changeReason.value || null;

      try {
        if (sameDay) {
          await sendJson(`${API}/cancel`, { studentId: row.studentId, reservationId: row.reservationId, reason });
          try {
            await sendJson(`${API}/register`, { studentId: row.studentId, slotInstanceId });
          } catch (err) {
            alert(`기존 예약은 취소됐지만 새 회차 등록에 실패했습니다: ${err.message}\n다시 등록해 주세요.`);
            state.selectedRow = null;
            setChangePanelMode("create");
            await loadDay();
            await loadChangeDateData();
            return;
          }
        } else {
          await sendJson(`${API}/register`, { studentId: row.studentId, slotInstanceId });
          await sendJson(`${API}/cancel`, { studentId: row.studentId, reservationId: row.reservationId, reason });
        }
      } catch (err) {
        alert(err.message);
        return;
      }
      state.selectedRow = null;
      setChangePanelMode("create");
      await loadDay();
      await loadChangeDateData();
    }
  }

  async function onCancelReservation() {
    const row = state.selectedRow;
    if (!row) return;
    if (!el.changeReason.value.trim()) {
      alert("취소 사유를 입력해 주세요.");
      return;
    }
    if (!confirm(`${row.studentName} 학생의 예약을 취소할까요?`)) return;
    try {
      await sendJson(`${API}/cancel`, { studentId: row.studentId, reservationId: row.reservationId, reason: el.changeReason.value || null });
    } catch (err) {
      alert(err.message);
      return;
    }
    state.selectedRow = null;
    setChangePanelMode("create");
    await loadDay();
  }
})();
