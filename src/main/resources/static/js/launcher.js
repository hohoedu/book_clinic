/* 앱 진입점(/launch) — "이 기기 용도"를 한 번만 물어보고 localStorage에 저장한 뒤, 이후로는
   이 화면을 건너뛰고 바로 그 화면으로 보낸다(2026-07-30). 서버 로그인 여부와 무관한, 순수하게
   "이 브라우저(기기)가 문제풀이용인지 출석체크용인지"만 기억하는 값이라 studentId 등과는 별개다. */
(function () {
  const MODE_KEY = 'hohobook.appMode';

  const ROUTES = {
    study: '/student/login',
    attendance: '/attendance',
  };

  // dev 프로파일에서는 매번 선택 화면을 다시 보여준다(서버가 body의 data-force-choice로 알려줌)
  // — 두 화면을 계속 오가며 테스트해야 해서 저장된 값이 있어도 건너뛰지 않는다.
  const forceChoice = document.body.dataset.forceChoice === 'true';

  const savedMode = !forceChoice && localStorage.getItem(MODE_KEY);
  if (savedMode && ROUTES[savedMode]) {
    window.location.replace(ROUTES[savedMode]);
  } else {
    document.getElementById('launcherView').hidden = false;

    document.getElementById('studyBtn').addEventListener('click', () => {
      localStorage.setItem(MODE_KEY, 'study');
      window.location.replace(ROUTES.study);
    });

    document.getElementById('attendanceBtn').addEventListener('click', () => {
      localStorage.setItem(MODE_KEY, 'attendance');
      window.location.replace(ROUTES.attendance);
    });
  }

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch((err) => {
        console.error('Service worker 등록 실패:', err);
      });
    });
  }
})();
