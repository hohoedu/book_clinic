/* 학생증 QR 스캔 오버레이 — 로그인(학생 식별)과 자가 퇴실(student-main) 양쪽에서 재사용한다.
   jsQR(순수 JS 디코더, /js/vendor/jsqr/jsQR.js)로 매 프레임 캔버스를 읽어 QR을 찾는다 —
   네트워크 없이 오프라인에서도 동작해야 하는 PWA라 CDN 대신 로컬에 벤더링해뒀다(Firebase SDK와 동일 관례).
   QR에 담긴 내용은 학생의 appId 원문 그대로라고 가정한다 — 실물 학생증 발급 포맷이 다르면 이 파일의
   디코드 결과 파싱 부분만 손보면 된다. */
(function () {
  function openQrScanner(title) {
    return new Promise((resolve, reject) => {
      if (typeof window.jsQR !== 'function') {
        reject(new Error('QR 스캔 모듈을 불러오지 못했어요.'));
        return;
      }

      const overlay = document.createElement('div');
      overlay.className = 'qr-scan-overlay';
      overlay.innerHTML = [
        '<div class="qr-scan-box">',
        '  <div class="qr-scan-header">',
        `    <span>${title || 'QR 스캔'}</span>`,
        '    <button type="button" class="qr-scan-close" aria-label="닫기"><i class="fa-solid fa-xmark"></i></button>',
        '  </div>',
        '  <video class="qr-scan-video" autoplay playsinline muted></video>',
        '  <p class="qr-scan-hint">학생증 QR을 카메라에 비춰주세요</p>',
        '  <p class="qr-scan-error" hidden></p>',
        '</div>',
      ].join('');
      document.body.appendChild(overlay);

      const video = overlay.querySelector('.qr-scan-video');
      const errorEl = overlay.querySelector('.qr-scan-error');
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d', { willReadFrequently: true });

      let stream = null;
      let rafId = null;
      let stopped = false;

      function cleanup() {
        stopped = true;
        if (rafId) cancelAnimationFrame(rafId);
        if (stream) stream.getTracks().forEach((track) => track.stop());
        overlay.remove();
      }

      overlay.querySelector('.qr-scan-close').addEventListener('click', () => {
        cleanup();
        reject(new Error('cancelled'));
      });

      function tick() {
        if (stopped) return;
        if (video.readyState === video.HAVE_ENOUGH_DATA) {
          canvas.width = video.videoWidth;
          canvas.height = video.videoHeight;
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
          const code = window.jsQR(imageData.data, imageData.width, imageData.height);
          if (code && code.data) {
            cleanup();
            resolve(code.data);
            return;
          }
        }
        rafId = requestAnimationFrame(tick);
      }

      navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } })
        .then((mediaStream) => {
          if (stopped) {
            mediaStream.getTracks().forEach((track) => track.stop());
            return;
          }
          stream = mediaStream;
          video.srcObject = mediaStream;
          rafId = requestAnimationFrame(tick);
        })
        .catch((err) => {
          console.error('[qr-scan] 카메라 열기 실패', err);
          errorEl.hidden = false;
          errorEl.textContent = '카메라를 열 수 없어요. 권한을 확인해주세요.';
        });
    });
  }

  window.openQrScanner = openQrScanner;
})();
