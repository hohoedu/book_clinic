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

  // student-main.html(#mainPage)에서만 동작 — 홈 진입 시 입실 처리 + "지금 보여줄 책"만 확인한다.
  // 다음 책 추천은 여기서 자동으로 일어나지 않는다(2026-07-29) — 예전엔 홈 화면에 들어오기만 해도
  // 바로 다음 책이 대여 확정돼서, 문제도 안 풀고 퇴실해버리면 그 책만 아무도 안 읽은 채 붕 떴다.
  // "새 책 추천받을까요?" 질문은 결과 화면(student-result.js)에서 이미 물어보므로 여기서는 다시
  // 묻지 않는다 — 읽던 책이 있으면 그 책+"문제 풀기", 방금 끝낸 책만 있으면(결과 화면에서 "아니요"를
  // 골랐거나 새로고침 등으로 그냥 홈에 온 경우) 그 책+"책 추천받기" 버튼만 조용히 보여준다.
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

    // state: "READING"(읽던 중, 버튼="문제 풀기") / "AWAITING_NEXT"(방금 끝냄, 버튼="책 추천받기")
    function renderBook(book, state) {
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

      if (state === 'AWAITING_NEXT') {
        actionLabel.textContent = '책 추천받기';
        actionBtn.onclick = fetchNextBook;
      } else {
        actionLabel.textContent = '문제 풀기';
        actionBtn.onclick = () => {
          window.location.href = `/student/question?studentId=${encodeURIComponent(studentId)}&contentId=${book.contentId}`;
        };
      }

      showState('card');
    }

    function showError(err) {
      console.error(err);
      emptyMsgEl.textContent = err.message || '추천할 수 있는 도서를 찾지 못했어요.';
      showState('empty');
    }

    // "책 추천받기" 버튼 클릭 시에만 호출 — 여기서만 새 책이 실제로 대여 확정된다
    async function fetchNextBook() {
      showState('loading');
      try {
        const res = await fetch('/clinic/recommend', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ studentId }),
        });
        const data = await res.json();
        if (!data.success) throw new Error(data.error?.message ?? '추천에 실패했어요.');
        renderBook(data.response, 'READING');
      } catch (err) {
        showError(err);
      }
    }

    // 홈 진입 시 1회 — 입실 처리 + "지금 보여줄 책" 상태만 확인한다(다음 책 추천은 안 함)
    async function loadHomeState() {
      showState('loading');
      try {
        const res = await fetch('/clinic/home-state', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ studentId }),
        });
        const data = await res.json();
        if (!data.success) throw new Error(data.error?.message ?? '조회에 실패했어요.');
        renderBook(data.response.book, data.response.state);
      } catch (err) {
        showError(err);
      }
    }

    loadHomeState();
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
