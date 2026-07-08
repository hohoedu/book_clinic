(function () {
  const DESIGN_WIDTH = 1280;
  const DESIGN_HEIGHT = 800;
  const viewport = document.querySelector('.app-viewport');

  function setAppScale() {
    if (!viewport) return;

    // 화면 비율이 1280x800과 달라도 여백 없이 꽉 채우도록 가로/세로 비율을 각각 적용
    const scaleX = window.innerWidth / DESIGN_WIDTH;
    const scaleY = window.innerHeight / DESIGN_HEIGHT;

    viewport.style.setProperty('--app-scale-x', scaleX.toFixed(4));
    viewport.style.setProperty('--app-scale-y', scaleY.toFixed(4));
  }

  window.addEventListener('resize', setAppScale);
  window.addEventListener('orientationchange', setAppScale);
  setAppScale();

  document.addEventListener('DOMContentLoaded', () => {
    setAppScale();

    const solveButton = document.querySelector('.solve-btn');

    if (solveButton) {
      solveButton.addEventListener('click', () => {
        solveButton.classList.add('is-pressed');
        window.setTimeout(() => solveButton.classList.remove('is-pressed'), 180);
      });
    }

    const logoutButton = document.querySelector('.logout-btn');

    if (logoutButton) {
      logoutButton.addEventListener('click', () => {
        localStorage.clear();
        sessionStorage.clear();
        // replace()로 이동해 뒤로가기로 메인 화면에 다시 들어올 수 없게 한다
        window.location.replace('/student/login');
      });
    }
  });

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch((err) => {
        console.error('Service worker 등록 실패:', err);
      });
    });

    // 새 서비스워커가 활성화되면(=새 버전 배포됨) 자동으로 한 번만 새로고침
    let refreshingAfterUpdate = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (refreshingAfterUpdate) return;
      refreshingAfterUpdate = true;
      window.location.reload();
    });
  }
})();
