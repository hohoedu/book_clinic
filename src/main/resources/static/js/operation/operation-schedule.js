/* 운영 스케줄 설정 — 정적 스캐폴딩 단계, 저장/조회 API 연동은 다음 작업에서 이어감 */
(function () {
  "use strict";

  const DAY_LABEL = {
    mon: "월요일", tue: "화요일", wed: "수요일", thu: "목요일",
    fri: "금요일", sat: "토요일", sun: "일요일",
  };

  const dayTabs = document.getElementById("dayTabs");
  const dayScheduleTitle = document.getElementById("dayScheduleTitle");
  const dayScheduleBadge = document.getElementById("dayScheduleBadge");
  const dayScheduleStateToggle = document.getElementById("dayScheduleStateToggle");
  const roundListBody = document.getElementById("roundListBody");
  const roundDefaultMinutes = document.getElementById("roundDefaultMinutes");
  const btnAutoGenerate = document.getElementById("btnAutoGenerate");
  const dayHoursCard = document.getElementById("dayHoursCard");
  const roundManageCard = document.getElementById("roundManageCard");
  const maxCapacityInput = document.getElementById("maxCapacity");
  const effectiveFromDate = document.getElementById("effectiveFromDate");
  const applyNowCheck = document.getElementById("applyNowCheck");

  let roundSeq = 0;

  function buildRoundRow({ startTime, endTime, minutes, capacity }) {
    roundSeq += 1;
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><i class="fa-solid fa-grip-vertical round-drag"></i></td>
      <td class="round-name">${roundSeq}회차</td>
      <td><input type="time" value="${startTime}" /></td>
      <td><input type="time" value="${endTime}" /></td>
      <td>
        <select>
          <option value="30" ${minutes === 30 ? "selected" : ""}>30분</option>
          <option value="40" ${minutes === 40 ? "selected" : ""}>40분</option>
          <option value="50" ${minutes === 50 ? "selected" : ""}>50분</option>
          <option value="60" ${minutes === 60 ? "selected" : ""}>60분</option>
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

  function renumberRounds() {
    roundListBody.querySelectorAll("tr").forEach((tr, idx) => {
      tr.querySelector(".round-name").textContent = `${idx + 1}회차`;
    });
    roundSeq = roundListBody.querySelectorAll("tr").length;
  }

  function addMinutes(hhmm, minutes) {
    const [h, m] = hhmm.split(":").map(Number);
    const total = h * 60 + m + minutes;
    const nh = Math.floor((total % (24 * 60)) / 60);
    const nm = total % 60;
    return `${String(nh).padStart(2, "0")}:${String(nm).padStart(2, "0")}`;
  }

  function autoGenerateRounds() {
    const openTime = document.getElementById("dayOpenTime").value || "13:00";
    const closeTime = document.getElementById("dayCloseTime").value || "19:00";
    const minutes = Number(roundDefaultMinutes.value) || 50;
    const capacity = document.getElementById("maxCapacity").value.replace(/\D/g, "") || "10";

    roundListBody.innerHTML = "";
    roundSeq = 0;

    let cursor = openTime;
    while (true) {
      const next = addMinutes(cursor, minutes);
      if (toMinutes(next) > toMinutes(closeTime)) break;
      roundListBody.appendChild(buildRoundRow({ startTime: cursor, endTime: next, minutes, capacity }));
      cursor = next;
    }

    if (!roundListBody.children.length) {
      roundListBody.appendChild(buildRoundRow({ startTime: openTime, endTime: addMinutes(openTime, minutes), minutes, capacity }));
    }
  }

  function toMinutes(hhmm) {
    const [h, m] = hhmm.split(":").map(Number);
    return h * 60 + m;
  }

  function loadDefaultRounds() {
    roundListBody.innerHTML = "";
    roundSeq = 0;
    const defaults = [
      { startTime: "13:00", endTime: "13:50", minutes: 50, capacity: "10" },
      { startTime: "13:00", endTime: "13:50", minutes: 50, capacity: "10" },
      { startTime: "13:00", endTime: "13:50", minutes: 50, capacity: "10" },
      { startTime: "13:00", endTime: "13:50", minutes: 50, capacity: "10" },
    ];
    defaults.forEach((r) => roundListBody.appendChild(buildRoundRow(r)));
  }

  function applyDayState(isOpen) {
    dayScheduleBadge.textContent = isOpen ? "운영중" : "휴무";
    dayScheduleBadge.classList.toggle("badge-open", isOpen);
    dayScheduleBadge.classList.toggle("badge-closed", !isOpen);
    dayScheduleStateToggle.checked = isOpen;
  }

  // 요일 탭 전환
  dayTabs.addEventListener("click", (e) => {
    const tab = e.target.closest(".day-tab");
    if (!tab) return;

    dayTabs.querySelectorAll(".day-tab").forEach((t) => t.classList.remove("active"));
    tab.classList.add("active");

    dayScheduleTitle.textContent = `${DAY_LABEL[tab.dataset.day]} 스케줄`;
    applyDayState(tab.dataset.state === "open");

    // 운영시간/운영회차 변경 중이었더라도 요일을 다시 고르면 평소 편집 상태로 되돌린다
    const closedRadio = exceptionTypeGroup.querySelector('input[value="closed"]');
    closedRadio.checked = true;
    applyExceptionType("closed");
  });

  // 운영중/휴무 스위치 — 현재 선택된 요일 탭 상태에 함께 반영
  dayScheduleStateToggle.addEventListener("change", () => {
    const isOpen = dayScheduleStateToggle.checked;
    applyDayState(isOpen);

    const activeTab = dayTabs.querySelector(".day-tab.active");
    if (!activeTab) return;
    activeTab.dataset.state = isOpen ? "open" : "closed";
    activeTab.querySelector(".day-tab-state").textContent = isOpen ? "운영" : "휴무";
    activeTab.querySelector(".day-tab-state").classList.toggle("closed", !isOpen);
  });

  function getMaxCapacity() {
    return parseInt(maxCapacityInput.value, 10) || 0;
  }

  function flashLimit(input) {
    input.classList.add("stepper-limit");
    setTimeout(() => input.classList.remove("stepper-limit"), 400);
  }

  // 화요일 스케줄의 최대 예약 인원보다 회차별 예약 가능 인원이 많아지지 않도록 제한
  function clampRoundCapacity(input) {
    const max = getMaxCapacity();
    const suffix = input.value.replace(/^\d+/, "");
    let value = parseInt(input.value, 10) || 0;
    if (value > max) {
      value = max;
      input.value = `${value}${suffix}`;
      flashLimit(input);
    }
  }

  function clampAllRoundCapacities() {
    roundListBody.querySelectorAll(".stepper-value").forEach(clampRoundCapacity);
  }

  // 스테퍼 공용 처리 (± 버튼)
  document.addEventListener("click", (e) => {
    const minusBtn = e.target.closest(".stepper-minus");
    const plusBtn = e.target.closest(".stepper-plus");
    const btn = minusBtn || plusBtn;
    if (!btn) return;

    const input = btn.closest(".stepper").querySelector(".stepper-value");
    const suffix = input.value.replace(/^\d+/, "");
    const current = parseInt(input.value, 10) || 0;
    const isRoundCapacity = input !== maxCapacityInput && roundListBody.contains(input);

    if (plusBtn && isRoundCapacity && current >= getMaxCapacity()) {
      flashLimit(input);
      return;
    }

    const next = minusBtn ? Math.max(0, current - 1) : current + 1;
    input.value = `${next}${suffix}`;

    // 최대 예약 인원을 ±버튼으로 낮췄을 때도 회차 인원을 즉시 재조정
    if (input === maxCapacityInput) {
      clampAllRoundCapacities();
    }
  });

  // 회차별 예약 가능 인원을 직접 입력했을 때도 최대 예약 인원을 넘지 않도록 보정
  roundListBody.addEventListener("change", (e) => {
    if (e.target.classList.contains("stepper-value")) {
      clampRoundCapacity(e.target);
    }
  });

  // 최대 예약 인원을 낮추면 이미 설정된 회차 인원도 함께 낮춘다
  maxCapacityInput.addEventListener("change", clampAllRoundCapacities);

  // 회차 삭제
  roundListBody.addEventListener("click", (e) => {
    const delBtn = e.target.closest(".round-delete");
    if (!delBtn) return;
    delBtn.closest("tr").remove();
    renumberRounds();
  });

  btnAutoGenerate.addEventListener("click", autoGenerateRounds);

  // 특정일 예외 설정 — 라디오(단일 선택) pill 스타일
  const exceptionTypeGroup = document.getElementById("exceptionTypeGroup");
  const exceptionEditHint = document.getElementById("exceptionEditHint");

  function setCardLocked(card, locked) {
    card.classList.toggle("locked", locked);
    card.querySelectorAll("input, select, button").forEach((el) => {
      el.disabled = locked;
    });
  }

  function applyExceptionType(type) {
    exceptionTypeGroup.querySelectorAll(".check-pill").forEach((p) => {
      p.classList.toggle("checked", p.dataset.exceptionType === type);
    });

    // 평소(휴무 상태 포함)엔 왼쪽 카드는 평상시 요일 스케줄 입력용으로 그대로 사용
    // 운영시간 변경/운영회차 변경을 누른 순간에만 해당 카드는 열고 나머지는 잠근다
    if (type === "time") {
      setCardLocked(dayHoursCard, false);
      setCardLocked(roundManageCard, true);
    } else if (type === "round") {
      setCardLocked(dayHoursCard, true);
      setCardLocked(roundManageCard, false);
    } else {
      setCardLocked(dayHoursCard, false);
      setCardLocked(roundManageCard, false);
    }

    exceptionEditHint.hidden = type !== "time" && type !== "round";
  }

  exceptionTypeGroup.addEventListener("click", (e) => {
    const pill = e.target.closest(".check-pill");
    if (!pill) return;
    if (e.target.tagName !== "INPUT") {
      pill.querySelector("input").checked = true;
    }
    applyExceptionType(pill.dataset.exceptionType);
  });

  // 등록된 예외 일정 삭제
  document.getElementById("exceptionList").addEventListener("click", (e) => {
    const delBtn = e.target.closest(".exception-delete");
    if (!delBtn) return;
    delBtn.closest(".exception-item").remove();
  });

  loadDefaultRounds();

  // 초기 상태(휴무 기본 선택)를 좌측 카드 잠금 상태에 반영 — 회차 행이 채워진 뒤 적용
  applyExceptionType(exceptionTypeGroup.querySelector("input:checked").value);

  // 즉시 적용 체크 시 적용 시작일은 오늘로 고정, 해제하면 미래 날짜를 직접 고를 수 있다
  applyNowCheck.addEventListener("change", () => {
    const applyNow = applyNowCheck.checked;
    effectiveFromDate.disabled = applyNow;
    if (applyNow) {
      effectiveFromDate.value = new Date().toISOString().slice(0, 10);
    }
  });
  effectiveFromDate.disabled = applyNowCheck.checked;
})();
