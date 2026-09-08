/* 학생 로그인 화면 — QR 인라인 스캐너 + 앱 아이디 입력 모드 토글, 안내 모달, 기기 용도 변경.
   jsQR(/js/vendor/jsqr/jsQR.js)이 먼저 로드돼 있어야 한다. 서버가 넘기는 값(noReservation 등)은
   Thymeleaf가 해당 모달 DOM을 렌더/생략하는 것으로만 전달되므로 이 파일은 DOM 존재 여부만 본다. */
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((err) => {
      console.error('Service worker 등록 실패:', err);
    });
  });
}

const noReservationModal = document.getElementById('noReservationModal');
if (noReservationModal) {
  document.getElementById('noReservationOk').addEventListener('click', () => noReservationModal.remove());
}

const alreadyExitedModal = document.getElementById('alreadyExitedModal');
if (alreadyExitedModal) {
  document.getElementById('alreadyExitedOk').addEventListener('click', () => alreadyExitedModal.remove());
}

const notEnteredModal = document.getElementById('notEnteredModal');
if (notEnteredModal) {
  document.getElementById('notEnteredOk').addEventListener('click', () => notEnteredModal.remove());
}

// 이 기기 용도(문제풀이/출석체크)를 다시 고르고 싶을 때 — launcher.js가 쓰는 키와 같아야 한다
document.getElementById('changeModeBtn').addEventListener('click', () => {
  try { localStorage.removeItem('hohobook.appMode'); } catch (err) { /* 저장소 접근 불가 시에도 이동은 진행 */ }
  window.location.href = '/launch';
});

// 카드 안 인라인 QR 스캐너 — jsQR로 매 프레임 디코드, 찾으면 appId 채우고 바로 제출한다.
// (오버레이 방식 openQrScanner는 student-main 자가 퇴실용으로 그대로 두고, 여기선 인라인으로 둔다)
// "다른 방법으로 로그인" 버튼으로 QR 모드 ↔ 앱 아이디 모드를 오간다. 앱 아이디 모드로 가면
// 카메라를 끄고, 다시 QR 모드로 오면 카메라를 켠다.
const inlineScanner = (function createInlineQrScanner() {
  const video = document.getElementById('qrInlineVideo');
  const errorEl = document.getElementById('qrInlineError');
  const form = document.getElementById('loginForm');

  let stream = null;
  let rafId = null;
  let running = false;
  let canvas = null;
  let ctx = null;

  function showError(msg) {
    errorEl.hidden = false;
    errorEl.textContent = msg;
  }

  function clearError() {
    errorEl.hidden = true;
    errorEl.textContent = '';
  }

  function stop() {
    running = false;
    if (rafId) { cancelAnimationFrame(rafId); rafId = null; }
    if (stream) { stream.getTracks().forEach((track) => track.stop()); stream = null; }
    if (video) video.srcObject = null;
  }

  function tick() {
    if (!running) return;
    if (video.readyState === video.HAVE_ENOUGH_DATA) {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const code = window.jsQR(img.data, img.width, img.height);
      if (code && code.data) {
        stop();
        form.appId.value = code.data.trim();
        form.submit();
        return;
      }
    }
    rafId = requestAnimationFrame(tick);
  }

  function start() {
    if (running) return;
    if (!video || typeof window.jsQR !== 'function') {
      showError('QR 스캐너를 불러오지 못했어요. "다른 방법으로 로그인"을 눌러주세요.');
      return;
    }
    if (!canvas) {
      canvas = document.createElement('canvas');
      ctx = canvas.getContext('2d', { willReadFrequently: true });
    }
    clearError();
    running = true;
    navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } })
      .then((mediaStream) => {
        if (!running) {
          mediaStream.getTracks().forEach((track) => track.stop());
          return;
        }
        stream = mediaStream;
        video.srcObject = mediaStream;
        rafId = requestAnimationFrame(tick);
      })
      .catch((err) => {
        running = false;
        console.error('[login-qr] 카메라 열기 실패', err);
        showError('카메라를 열 수 없어요. 권한을 확인하거나 "다른 방법으로 로그인"을 눌러주세요.');
      });
  }

  window.addEventListener('pagehide', stop);
  return { start, stop };
})();

// QR 모드 ↔ 앱 아이디 모드 토글
function setManualMode(on) {
  const qrMode = document.getElementById('qrMode');
  const manualMode = document.getElementById('manualMode');
  if (on) {
    inlineScanner.stop();
    qrMode.hidden = true;
    manualMode.hidden = false;
    const input = manualMode.querySelector('input');
    if (input) input.focus();
  } else {
    manualMode.hidden = true;
    qrMode.hidden = false;
    inlineScanner.start();
  }
}

document.getElementById('toggleModeBtn').addEventListener('click', () => {
  setManualMode(document.getElementById('manualMode').hidden);
});

// 서버가 로그인 에러를 돌려줬으면(대개 앱 아이디 오류) 앱 아이디 모드로 연다. 아니면 QR 모드.
if (document.querySelector('.login-error')) {
  setManualMode(true);
} else {
  inlineScanner.start();
}
