/*
 * 커스텀 컨펌 모달 (여러 화면에서 공용으로 사용)
 * 사용법: const ok = await customConfirm("메시지"); if (!ok) return;
 */
function customConfirm(message, options = {}) {
  return new Promise((resolve) => {
    const overlay = document.createElement("div");
    overlay.className = "custom-confirm-overlay";
    overlay.innerHTML = `
      <div class="custom-confirm">
        <p class="custom-confirm-message"></p>
        <div class="custom-confirm-actions">
          <button type="button" class="btn outline custom-confirm-cancel"></button>
          <button type="button" class="btn primary custom-confirm-ok"></button>
        </div>
      </div>
    `;

    overlay.querySelector(".custom-confirm-message").textContent = message;
    const cancelBtn = overlay.querySelector(".custom-confirm-cancel");
    const okBtn = overlay.querySelector(".custom-confirm-ok");
    cancelBtn.textContent = options.cancelText ?? "취소";
    okBtn.textContent = options.confirmText ?? "확인";

    const close = (result) => {
      overlay.remove();
      document.removeEventListener("keydown", onKeydown);
      resolve(result);
    };

    const onKeydown = (event) => {
      if (event.key === "Escape") close(false);
    };

    cancelBtn.addEventListener("click", () => close(false));
    okBtn.addEventListener("click", () => close(true));
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) close(false);
    });
    document.addEventListener("keydown", onKeydown);

    document.body.appendChild(overlay);
    okBtn.focus();
  });
}

/*
 * 커스텀 선택 모달 (2개 이상의 선택지 + 취소) — 여러 화면에서 공용으로 사용
 * 사용법: const value = await customChoice("메시지", [{ label: "이번년도 적용", value: "now", primary: true }, { label: "내년 등록", value: "next" }]);
 * 취소하면 null을 반환한다.
 */
function customChoice(message, choices, options = {}) {
  return new Promise((resolve) => {
    const overlay = document.createElement("div");
    overlay.className = "custom-confirm-overlay";
    overlay.innerHTML = `
      <div class="custom-confirm">
        <p class="custom-confirm-message"></p>
        <div class="custom-confirm-actions custom-confirm-actions-column">
          <div class="custom-choice-buttons"></div>
          <button type="button" class="btn outline custom-confirm-cancel"></button>
        </div>
      </div>
    `;

    overlay.querySelector(".custom-confirm-message").textContent = message;
    const choiceContainer = overlay.querySelector(".custom-choice-buttons");
    const cancelBtn = overlay.querySelector(".custom-confirm-cancel");
    cancelBtn.textContent = options.cancelText ?? "취소";

    const close = (result) => {
      overlay.remove();
      document.removeEventListener("keydown", onKeydown);
      resolve(result);
    };

    const onKeydown = (event) => {
      if (event.key === "Escape") close(null);
    };

    choices.forEach((choice) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `btn ${choice.primary ? "primary" : "outline"} custom-choice-btn`;
      button.textContent = choice.label;
      button.addEventListener("click", () => close(choice.value));
      choiceContainer.appendChild(button);
    });

    cancelBtn.addEventListener("click", () => close(null));
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) close(null);
    });
    document.addEventListener("keydown", onKeydown);

    document.body.appendChild(overlay);
    choiceContainer.querySelector("button")?.focus();
  });
}
