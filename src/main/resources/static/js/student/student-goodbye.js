// 로그아웃 안내 화면(2026-09-30) — 로그아웃은 이미 끝난 상태로 들어온다. 학생이 안내를 읽을 시간을
// 준 뒤 로그인(QR) 화면으로 자동 이동하고, 확인 버튼을 누르면 바로 이동한다.
(function () {
  const REDIRECT_SECONDS = 8;
  const okBtn = document.getElementById('goodbyeOkBtn');
  const countEl = document.getElementById('goodbyeCount');
  let remaining = REDIRECT_SECONDS;
  let timer = null;

  function goLogin() {
    if (timer) clearInterval(timer);
    // replace()로 이동해 뒤로가기로 이 화면(또는 그 전 화면)에 돌아오지 않게 한다
    window.location.replace('/student');
  }

  function renderCount() {
    if (countEl) countEl.textContent = `(${remaining})`;
  }

  renderCount();
  timer = setInterval(() => {
    remaining -= 1;
    if (remaining <= 0) {
      goLogin();
      return;
    }
    renderCount();
  }, 1000);

  if (okBtn) okBtn.addEventListener('click', goLogin);
})();
