/* 출석체크 홈 — 입실/퇴실 선택만 하는 단순한 화면(2026-07-30).
   입실: QR 스캔 → /attendance/enter로 POST(폼 전송). 이 페이지가 입실 처리 + 오늘 추천 도서만
         보여주고 "닫기"를 누르면 이 출석체크 홈으로 돌아온다 — 문제풀이로는 못 넘어간다(공용 기기라
         여기서 실제 학습까지 이어지면 안 됨. 학습은 학생 개인 폰의 /student/login에서 따로 함).
   퇴실: QR 스캔 → /student/exit 호출(로그인 컨텍스트가 없어 studentId 없이 appId만 보낸다 —
         StudentViewController.exitByQr가 studentId 없으면 본인 확인 없이 스캔된 QR을 그대로 신뢰한다)
         → 완료 메시지를 보여준 뒤 이 화면 그대로 유지(재사용 대비). */
(function () {
  function showToast(kind, message, duration) {
    const toast = document.createElement('div');
    toast.className = 'attendance-toast';
    const icon = kind === 'success' ? 'fa-circle-check' : 'fa-circle-exclamation';
    toast.innerHTML = `
      <div class="attendance-toast-box ${kind}">
        <i class="fa-solid ${icon}"></i>
        <p>${message}</p>
      </div>
    `;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), duration || 1800);
  }

  // 입실은 이용권을 소진시키는 "쓰기" 요청이라 GET으로 두면 주소창 한 줄로 남의 이용권이 깎인다
  // (2026-08-20). 폼을 만들어 POST로 보내면 CSRF 토큰이 함께 실려 우리 화면에서 온 요청만 통과한다.
  // fetch가 아니라 폼 전송인 이유는 서버가 JSON이 아니라 확인 화면(HTML)을 그대로 내려주기 때문이다.
  function submitEnter(appId) {
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/attendance/enter';
    form.appendChild(hiddenInput('appId', appId));
    const csrf = readCsrfToken();
    if (csrf) form.appendChild(hiddenInput('_csrf', csrf));
    document.body.appendChild(form);
    form.submit();
  }

  function hiddenInput(name, value) {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    return input;
  }

  // 서버가 세션마다 다른 값을 XSRF-TOKEN 쿠키로 내려준다(CookieCsrfTokenRepository, 2026-07-31)
  function readCsrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
  }

  const enterBtn = document.getElementById('enterBtn');
  const exitBtn = document.getElementById('exitBtn');

  if (enterBtn) enterBtn.addEventListener('click', async () => {
    try {
      const appId = await window.openQrScanner('QR 스캔 — 입실');
      submitEnter(appId);
    } catch (err) {
      if (err.message !== 'cancelled') showToast('error', err.message || 'QR 스캔에 실패했어요.');
    }
  });

  if (exitBtn) exitBtn.addEventListener('click', async () => {
    try {
      const appId = await window.openQrScanner('QR 스캔 — 퇴실');
      const res = await fetch('/student/exit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ appId }),
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error?.message ?? '퇴실 처리에 실패했어요.');
      showToast('success', '퇴실 처리되었습니다.');
    } catch (err) {
      if (err.message !== 'cancelled') showToast('error', err.message || '퇴실 처리에 실패했어요.');
    }
  });

  // 이 기기 용도(문제풀이/출석체크)를 다시 고르고 싶을 때 — launcher.js가 쓰는 키와 같아야 한다
  const changeModeBtn = document.getElementById('changeModeBtn');
  if (changeModeBtn) changeModeBtn.addEventListener('click', () => {
    try { localStorage.removeItem('hohobook.appMode'); } catch (err) { /* 저장소 접근 불가 시에도 이동은 진행 */ }
    window.location.href = '/launch';
  });

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch((err) => {
        console.error('Service worker 등록 실패:', err);
      });
    });
  }
})();
