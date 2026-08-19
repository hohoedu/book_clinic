document.addEventListener("DOMContentLoaded", () => {
  initDatePicker();
  initDateNav();
  initRoundCardSelect();
  initRoundPickSelect();
  initStudentPickSelect();
  initStudentPickSearch();
  initRowSelect();
  initChangePanelMode();
});

function initStudentPickSelect() {
  const items = document.querySelectorAll(".student-pick-item");
  items.forEach((item) => {
    const input = item.querySelector("input");
    item.addEventListener("click", (e) => {
      // 체크박스 자체를 클릭한 경우는 브라우저가 이미 토글해서 change 이벤트가 따로 온다
      if (e.target === input) return;
      input.checked = !input.checked;
      input.dispatchEvent(new Event("change"));
    });
    input.addEventListener("change", () => {
      item.classList.toggle("checked", input.checked);
    });
  });
}

function initStudentPickSearch() {
  const input = document.getElementById("studentPickSearch");
  const items = document.querySelectorAll(".student-pick-item");

  const applyFilter = () => {
    const keyword = input.value.trim();
    items.forEach((item) => {
      const name = item.querySelector(".student-pick-name").textContent;
      item.hidden = keyword !== "" && !name.includes(keyword);
    });
  };

  input.addEventListener("input", applyFilter);
  document.getElementById("btnStudentPickSearch").addEventListener("click", applyFilter);
}

const CHANGE_PANEL_TEXT = {
  create: {
    title: "예약 등록",
    desc: "학생을 선택한 날짜/회차에 새로 등록합니다.",
    dateLabel: "등록 날짜",
    roundLabel: "등록 회차",
    reasonLabel: "등록 사유",
    saveLabel: "예약 등록",
  },
  change: {
    title: "예약 변경",
    desc: "선택한 학생의 예약을 다른 날짜/회차로 변경합니다.",
    dateLabel: "변경 날짜",
    roundLabel: "변경 회차",
    reasonLabel: "변경 사유",
    saveLabel: "예약 변경",
  },
};

function initChangePanelMode() {
  document.getElementById("btnAddReservation").addEventListener("click", () => setChangePanelMode("create"));
  setChangePanelMode("create");
}

function setChangePanelMode(mode) {
  const text = CHANGE_PANEL_TEXT[mode];
  document.getElementById("changePanelTitle").textContent = text.title;
  document.getElementById("changePanelDesc").textContent = text.desc;
  document.getElementById("changeDateLabel").textContent = text.dateLabel;
  document.getElementById("changeRoundLabel").textContent = text.roundLabel;
  document.getElementById("changeReasonLabel").textContent = text.reasonLabel;
  document.getElementById("btnSaveChange").textContent = text.saveLabel;
  document.getElementById("currentReservationBox").hidden = mode !== "change";
  document.getElementById("btnCancelReservation").hidden = mode !== "change";
  document.getElementById("changeReasonGroup").hidden = mode !== "change";
  document.getElementById("studentPickGroup").hidden = mode !== "create";
}

function todayStr(d = new Date()) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function updateDateDisplay(input, display) {
  const [y, m, d] = input.value.split("-");
  display.textContent = `${y}-${m}-${d}`;
}

function initDatePicker() {
  const input = document.getElementById("reservationDate");
  const display = document.getElementById("reservationDateDisplay");
  const trigger = document.querySelector(".monitor-date-trigger");

  trigger.addEventListener("click", () => input.showPicker());
  input.addEventListener("change", () => updateDateDisplay(input, display));
}

function initDateNav() {
  const input = document.getElementById("reservationDate");
  const display = document.getElementById("reservationDateDisplay");

  const shiftDay = (days) => {
    const current = new Date(input.value || todayStr());
    current.setDate(current.getDate() + days);
    input.value = todayStr(current);
    updateDateDisplay(input, display);
  };

  document.getElementById("btnPrevDay").addEventListener("click", () => shiftDay(-1));
  document.getElementById("btnNextDay").addEventListener("click", () => shiftDay(1));
  document.getElementById("btnToday").addEventListener("click", () => {
    input.value = todayStr();
    updateDateDisplay(input, display);
  });
}

function initRoundCardSelect() {
  const cards = document.querySelectorAll(".round-status-card");
  cards.forEach((card) => {
    card.addEventListener("click", () => {
      cards.forEach((c) => c.classList.remove("active"));
      card.classList.add("active");
    });
  });
}

function initRoundPickSelect() {
  const items = document.querySelectorAll(".round-pick:not(.disabled)");
  items.forEach((item) => {
    const input = item.querySelector("input");
    item.addEventListener("click", () => {
      document.querySelectorAll(".round-pick").forEach((i) => i.classList.remove("checked"));
      input.checked = true;
      item.classList.add("checked");
    });
  });
}

function initRowSelect() {
  const rows = document.querySelectorAll("#reservationListBody tr");
  rows.forEach((row) => {
    row.querySelector(".btn-row-change").addEventListener("click", () => {
      rows.forEach((r) => r.classList.remove("selected"));
      row.classList.add("selected");
      const name = row.querySelector(".cell-name").textContent;
      document.getElementById("changeTargetName").textContent = name;
      setChangePanelMode("change");
    });
  });
}
