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

  // 기기 등록 화면 — 아직 라이선스 키가 없을 때만 마크업에 존재한다
  const registerForm = document.getElementById('registerForm');
  if (registerForm) {
    const errorEl = document.getElementById('registerError');
    registerForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      errorEl.hidden = true;
      const licenseKey = document.getElementById('licenseKeyInput').value.trim();
      if (!licenseKey) return;

      try {
        const res = await fetch('/kiosk/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': readCsrfToken() ?? '' },
          body: JSON.stringify({ licenseKey }),
        });
        const data = await res.json();
        if (!data.success) throw new Error(data.error?.message ?? '등록에 실패했어요.');
        // 서버가 심어준 쿠키를 반영하려면 화면을 새로 받아야 한다
        window.location.reload();
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      }
    });
  }

  // 등록 전 화면에는 입실/퇴실 버튼이 아예 없다 — 아래 등록은 그때를 위한 것이라 존재 확인이 필요하다
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

  // 기기 반납이나 센터 이전 때만 쓴다 — 해제하면 이 기기에서는 학생 로그인이 바로 막힌다
  const unregisterBtn = document.getElementById('unregisterBtn');
  if (unregisterBtn) unregisterBtn.addEventListener('click', async () => {
    if (!confirm('이 기기의 등록을 해제할까요?\n해제하면 학생 로그인이 막히고, 다시 쓰려면 라이선스 키를 새로 입력해야 합니다.')) return;
    try {
      const res = await fetch('/kiosk/register', {
        method: 'DELETE',
        headers: { 'X-XSRF-TOKEN': readCsrfToken() ?? '' },
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error?.message ?? '해제에 실패했어요.');
      window.location.reload();
    } catch (err) {
      showToast('error', err.message);
    }
  });

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch((err) => {
        console.error('Service worker 등록 실패:', err);
      });
    });
  }
})();
