// 정적 스캐폴딩 단계 — 날짜 표시/체크박스 토글 등 화면 인터랙션만 붙여두고,
// 실제 조회·저장 API 연동은 다음 작업에서 이어서 진행한다 (2026-07-29).

document.addEventListener("DOMContentLoaded", () => {
  initDatePicker();
  initAttitudeCheckboxes();
});

function initDatePicker() {
  const dateInput = document.getElementById("diaryDate");
  const dateDisplay = document.getElementById("diaryDateDisplay");
  if (!dateInput || !dateDisplay) return;

  dateInput.closest(".monitor-date-trigger").addEventListener("click", () => {
    if (typeof dateInput.showPicker === "function") dateInput.showPicker();
  });

  dateInput.addEventListener("change", () => {
    const [y, m, d] = dateInput.value.split("-");
    dateDisplay.textContent = `${y}. ${m}. ${d}`;
  });
}

function initAttitudeCheckboxes() {
  document.querySelectorAll(".diary-checkbox").forEach((label) => {
    const checkbox = label.querySelector("input[type='checkbox']");
    checkbox.addEventListener("change", () => {
      label.classList.toggle("checked", checkbox.checked);
    });
  });
}
