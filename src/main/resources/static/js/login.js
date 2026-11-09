// 관리자 로그인 — "아이디 저장" 체크 시 지점 코드와 ID를 localStorage에 기억한다
(function () {
  const STORAGE_KEY = "adminLoginRemember";

  const form = document.getElementById("loginForm");
  const centerCode = document.getElementById("centerCode");
  const username = document.getElementById("username");
  const password = document.getElementById("password");
  const rememberId = document.getElementById("rememberId");

  let saved = null;
  try {
    saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
  } catch (e) {
    saved = null;
  }

  // 저장값이 없으면(첫 방문) 체크된 상태로 시작 — 시안 기본값
  rememberId.checked = true;
  if (saved) {
    centerCode.value = saved.centerCode || "";
    username.value = saved.username || "";
    rememberId.checked = saved.remember !== false;
  }

  // 지점 코드는 항상 대문자 — 입력하는 즉시 변환(커서 위치 유지)
  centerCode.addEventListener("input", function () {
    const pos = centerCode.selectionStart;
    centerCode.value = centerCode.value.toUpperCase();
    centerCode.setSelectionRange(pos, pos);
  });

  if (!centerCode.value) centerCode.focus();
  else if (!username.value) username.focus();
  else password.focus();

  form.addEventListener("submit", function () {
    try {
      if (rememberId.checked) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({
          centerCode: centerCode.value.trim(),
          username: username.value.trim(),
          remember: true
        }));
      } else {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({ remember: false }));
      }
    } catch (e) {
      // 저장소 사용 불가(사생활 보호 모드 등) — 로그인 자체는 계속 진행
    }
  });
})();
