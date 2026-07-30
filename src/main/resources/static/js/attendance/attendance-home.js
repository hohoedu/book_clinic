/* 출석체크 홈 — 입실/퇴실 선택만 하는 단순한 화면(2026-07-30).
   입실: QR 스캔 → /attendance/enter?appId=X로 이동. 이 페이지가 입실 처리 + 오늘 추천 도서만
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

  document.getElementById('enterBtn').addEventListener('click', async () => {
    try {
      const appId = await window.openQrScanner('QR 스캔 — 입실');
      window.location.href = `/attendance/enter?appId=${encodeURIComponent(appId)}`;
    } catch (err) {
      if (err.message !== 'cancelled') showToast('error', err.message || 'QR 스캔에 실패했어요.');
    }
  });

  document.getElementById('exitBtn').addEventListener('click', async () => {
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

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch((err) => {
        console.error('Service worker 등록 실패:', err);
      });
    });
  }
})();
