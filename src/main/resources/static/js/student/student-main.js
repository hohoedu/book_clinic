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
    initRecommend();

    const logoutButton = document.querySelector('.logout-btn');

    if (logoutButton) {
      logoutButton.addEventListener('click', () => {
        localStorage.clear();
        sessionStorage.clear();
        // replace()로 이동해 뒤로가기로 메인 화면에 다시 들어올 수 없게 한다
        window.location.replace('/student');
      });
    }
  });

  // student-main.html(#mainPage)에서만 동작 — 로그인하면 무조건 책 한 권을 보여준다
  // (이미 추천받은 책이 있으면 그 책 그대로, 없으면 새로 추천해서 대여까지 확정 — /clinic/recommend가 멱등 처리)
  function initRecommend() {
    const page = document.getElementById('mainPage');
    if (!page) return;

    const studentId = page.getAttribute('data-student-id');

    const loadingEl = document.getElementById('recommendLoading');
    const emptyEl = document.getElementById('recommendEmpty');
    const emptyMsgEl = document.getElementById('recommendEmptyMsg');
    const cardEl = document.getElementById('recommendCard');
    const titleEl = document.getElementById('bookTitle');
    const authorEl = document.getElementById('bookAuthor');
    const descEl = document.getElementById('bookDesc');
    const imgEl = document.getElementById('bookImg');
    const actionBtn = document.getElementById('mainActionBtn');
    const actionLabel = document.getElementById('mainActionLabel');
    const metaTypeEl = document.getElementById('bookMetaType');
    const metaAwardEl = document.getElementById('bookMetaAward');
    const metaCurriculumEl = document.getElementById('bookMetaCurriculum');
    const metaTagsEl = document.getElementById('bookMetaTags');

    function showState(name) {
      loadingEl.hidden = name !== 'loading';
      emptyEl.hidden = name !== 'empty';
      cardEl.hidden = name !== 'card';
    }

    function renderBook(book) {
      titleEl.textContent = book.originalTitle ?? '-';
      authorEl.textContent = [book.author, book.publisher].filter(Boolean).join(' | ') || '-';
      descEl.textContent = book.summary ?? '-';
      imgEl.src = book.imageUrl || '/images/book-sample.png';
      imgEl.alt = `${book.originalTitle ?? ''} 표지`;

      // 메타 정보는 값이 없어도 행을 숨기지 않고 "-"로 채운다 (정보 영역 높이 고정)
      metaTypeEl.textContent = [book.contentTypeName, book.genreName].filter(Boolean).join(', ') || '-';
      metaAwardEl.textContent = book.awardName || '-';
      metaCurriculumEl.textContent = book.curriculumName || '-';
      // 해시태그만 예외 — 없을 때 "-"를 찍지 않고 자리(높이)만 비워 둔다
      metaTagsEl.textContent = book.keywords
        ? book.keywords.split(',').map((kw) => `#${kw.trim()}`).join(' ')
        : '';

      actionLabel.textContent = '문제 풀기';
      actionBtn.onclick = () => {
        window.location.href = `/student/question?studentId=${encodeURIComponent(studentId)}&contentId=${book.contentId}`;
      };

      showState('card');
    }

    async function fetchRecommend() {
      showState('loading');
      try {
        const res = await fetch('/clinic/recommend', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ studentId }),
        });
        const data = await res.json();
        if (!data.success) throw new Error(data.error?.message ?? '추천에 실패했어요.');
        renderBook(data.response);
      } catch (err) {
        console.error(err);
        emptyMsgEl.textContent = err.message || '추천할 수 있는 도서를 찾지 못했어요.';
        showState('empty');
      }
    }

    fetchRecommend();
  }

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
