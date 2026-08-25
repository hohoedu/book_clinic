(function () {
  const DESIGN_WIDTH = 1280;
  const DESIGN_HEIGHT = 800;
  const viewport = document.querySelector('.app-viewport');

  // 모바일 브라우저는 주소창/하단 툴바가 접혔다 펴졌다 하면서 window.innerHeight가 실시간으로
  // 바뀌는데, 그 순간 window.innerHeight로 스케일을 잡으면 툴바가 다시 나타나 실제 보이는 높이가
  // 줄어든 뒤에도 갱신이 안 될 수 있다 — 캔버스 맨 아래(문제풀이 화면의 "다음 문제" 버튼 등)가
  // 화면 밖으로 밀려나는 원인. visualViewport는 툴바 표시/숨김에 따라 실제 보이는 영역 기준으로
  // resize를 더 안정적으로 쏴주므로, 지원하는 브라우저는 이쪽 값을 우선 사용한다(2026-07-30).
  const vv = window.visualViewport;

  function setAppScale() {
    if (!viewport) return;

    const viewWidth = vv ? vv.width : window.innerWidth;
    const viewHeight = vv ? vv.height : window.innerHeight;

    // .app-viewport의 실제 박스 크기를 CSS 100vw/100vh(모바일 브라우저에서 주소창 뒤 공간까지
    // 포함해 스케일 계산 기준(window.innerHeight/visualViewport)보다 더 크게 잡히는 경우가 있음)
    // 대신 이 값으로 직접 고정한다. .page는 .app-viewport의 top:50%/left:50%로 중앙 정렬되는데,
    // 두 기준이 어긋나면 .page 자체 크기(스케일)는 정상이어도 중앙 기준점이 실제 화면 중앙보다
    // 아래로 처져서 캔버스 전체가 밀려 보인다(2026-07-30) — 여기서 기준을 하나로 통일해 없앤다.
    viewport.style.width = viewWidth + 'px';
    viewport.style.height = viewHeight + 'px';

    // 화면 비율이 1280x800과 달라도 여백 없이 꽉 채우도록 가로/세로 비율을 각각 적용
    const scaleX = viewWidth / DESIGN_WIDTH;
    const scaleY = viewHeight / DESIGN_HEIGHT;

    viewport.style.setProperty('--app-scale-x', scaleX.toFixed(4));
    viewport.style.setProperty('--app-scale-y', scaleY.toFixed(4));
  }

  window.addEventListener('resize', setAppScale);
  window.addEventListener('orientationchange', setAppScale);
  if (vv) {
    vv.addEventListener('resize', setAppScale);
    vv.addEventListener('scroll', setAppScale);
  }
  // 폰트/이미지 로딩이 늦게 끝나거나 orientationchange 직후 툴바 애니메이션이 끝나기 전에 계산되는
  // 경우를 대비해, 로드 완료 후와 약간의 지연을 두고 한 번씩 더 재계산한다.
  window.addEventListener('load', setAppScale);
  window.addEventListener('orientationchange', () => setTimeout(setAppScale, 300));
  setAppScale();

  document.addEventListener('DOMContentLoaded', () => {
    setAppScale();
    initRecommend();

    const logoutButton = document.querySelector('.logout-btn');
    const logoutConfirmModal = document.getElementById('logoutConfirmModal');
    const logoutConfirmBtn = document.getElementById('logoutConfirmBtn');
    const logoutCancelBtn = document.getElementById('logoutCancelBtn');

    function doLogout() {
      localStorage.clear();
      sessionStorage.clear();
      // replace()로 이동해 뒤로가기로 메인 화면에 다시 들어올 수 없게 한다
      window.location.replace('/student');
    }

    // 실수로 로그아웃을 누르는 경우가 있어 확인 모달을 한 번 거친다(2026-08-25)
    if (logoutButton && logoutConfirmModal) {
      logoutButton.addEventListener('click', () => {
        logoutConfirmModal.hidden = false;
      });
      logoutCancelBtn.addEventListener('click', () => {
        logoutConfirmModal.hidden = true;
      });
      logoutConfirmBtn.addEventListener('click', doLogout);
    } else if (logoutButton) {
      logoutButton.addEventListener('click', doLogout);
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
    const passExhaustedEl = document.getElementById('passExhausted');
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
    const recommendNextBtn = document.getElementById('recommendNextBtn');
    const completionActions = document.getElementById('completionActions');
    const completionWrongRetryBtn = document.getElementById('completionWrongRetryBtn');
    const completionAdvancedBtn = document.getElementById('completionAdvancedBtn');
    const recommendErrorModal = document.getElementById('recommendErrorModal');
    const recommendErrorMsg = document.getElementById('recommendErrorMsg');
    const recommendErrorOkBtn = document.getElementById('recommendErrorOkBtn');

    // "책 추천받기" 실패 안내(하루 추천 한도 2권 초과 등) — 브라우저 기본 alert() 대신 앱 스타일
    // 모달로 보여준다(2026-08-25)
    function showRecommendError(message) {
      if (!recommendErrorModal) {
        alert(message);
        return;
      }
      recommendErrorMsg.textContent = message;
      recommendErrorModal.hidden = false;
    }
    if (recommendErrorOkBtn) {
      recommendErrorOkBtn.addEventListener('click', () => {
        recommendErrorModal.hidden = true;
      });
    }

    // 완독(KING/FRIEND/심화완료) 후 결과 화면 "홈으로" 또는 "틀린 문제 다시 풀기" 중 "나가기"로
    // 왔을 때 — 문제풀기 버튼 자리에 남은 액션만 보여주는 완료 화면 모드(2026-08-25)
    const params = new URLSearchParams(window.location.search);
    const isCompletionMode = params.get('mode') === 'retryDone';
    const completionContentId = params.get('contentId');

    function showState(name) {
      loadingEl.hidden = name !== 'loading';
      emptyEl.hidden = name !== 'empty';
      passExhaustedEl.hidden = name !== 'passExhausted';
      cardEl.hidden = name !== 'card';
    }

    function fillBookInfo(book) {
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
    }

    // 문제풀이 화면 홈은 이미 확정된 PENDING 추천 책만 보여준다 — 입실/추천은 출석체크 기기에서만 일어난다
    function renderBook(book) {
      fillBookInfo(book);
      actionBtn.hidden = false;
      completionActions.hidden = true;
      recommendNextBtn.hidden = true;

      actionLabel.textContent = '문제 풀기';
      actionBtn.onclick = () => {
        window.location.href = `/student/question?studentId=${encodeURIComponent(studentId)}&contentId=${book.contentId}`;
      };

      showState('card');
    }

    // 완독(KING/FRIEND/심화완료) 후 결과 화면 "홈으로" 또는 문제풀이 화면 "나가기"(다시풀기 중)로
    // 왔을 때 — 문제 풀기 버튼 대신 남은 액션(틀린 문제 다시 풀기/심화 문제 풀기)과 "책 추천받기"만
    // 보여준다. 둘 다 없을 수도 있다(이미 다 끝낸 경우 — 책 추천받기만 노출)
    function renderCompletion(state) {
      const book = state.book;
      fillBookInfo(book);
      actionBtn.hidden = true;
      completionActions.hidden = false;
      recommendNextBtn.hidden = false;

      const hasWrong = (state.wrongQnums ?? []).length > 0;
      completionWrongRetryBtn.hidden = !hasWrong;
      completionAdvancedBtn.hidden = !state.advancedAvailable;

      completionWrongRetryBtn.onclick = () => {
        sessionStorage.setItem('retryQnums', JSON.stringify(state.wrongQnums ?? []));
        window.location.href = `/student/question?studentId=${encodeURIComponent(studentId)}&contentId=${book.contentId}&qlevel=01`;
      };
      completionAdvancedBtn.onclick = () => {
        window.location.href = `/student/question?studentId=${encodeURIComponent(studentId)}&contentId=${book.contentId}&qlevel=02`;
      };
      recommendNextBtn.onclick = async () => {
        recommendNextBtn.disabled = true;
        try {
          const res = await fetch('/clinic/recommend', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ studentId }),
          });
          const data = await res.json();
          if (!data.success) throw new Error(data.error?.message ?? '추천에 실패했어요.');
          // 다음 책을 새로 추천받았으니 완료 화면(mode=retryDone)이 아니라 일반 홈으로 다시 진입
          window.location.href = `/student/main?studentId=${encodeURIComponent(studentId)}`;
        } catch (err) {
          console.error(err);
          showRecommendError(err.message || '추천에 실패했어요.');
          recommendNextBtn.disabled = false;
        }
      };

      showState('card');
    }

    // 이용권 소진(MonitorService.enterSession)은 시스템 오류가 아니라 결제/재계약이 필요한
    // 정상적인 업무 상황이라, 같은 "실패" 톤이 아니라 전용 카드(passExhausted)로 구분해서 보여준다.
    // 서버가 별도 에러 코드를 내려주지 않아 메시지 문구로 구분한다 — enterSession의 문구와 짝이 맞아야 한다.
    function showError(err) {
      console.error(err);
      if (err.message && err.message.includes('이용권이 모두 소진')) {
        showState('passExhausted');
        return;
      }
      // 하루 추천 한도(2권) 초과는 에러 카드로 붙잡아두지 않고 바로 홈으로 돌려보낸다(2026-08-20) —
      // 학생이 "추천받기"를 다시 눌러도 매번 같은 에러만 반복되는 상황을 만들지 않기 위해서다.
      if (err.message && err.message.includes('초과할 수 없습니다')) {
        loadHomeState();
        return;
      }
      emptyMsgEl.textContent = err.message || '추천할 수 있는 도서를 찾지 못했어요.';
      showState('empty');
    }

    // 홈 진입 시 1회 — 입실/추천 처리는 전혀 하지 않고, 이미 확정된 PENDING 추천 책만 확인한다.
    // 입실(모니터링 "입실" 전환)과 다음 책 추천은 출석체크 기기(/attendance/enter)에서만 일어난다
    // (2026-08-25) — 예전엔 이 화면 진입만으로도 입실 처리되고, 추천 이력이 아예 없는 학생은
    // 곧바로 새 책이 추천/대여까지 확정돼버렸다.
    async function loadHomeState() {
      showState('loading');
      try {
        const res = await fetch('/clinic/quiz-home-state', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ studentId }),
        });
        const data = await res.json();
        if (!data.success) throw new Error(data.error?.message ?? '조회에 실패했어요.');
        if (data.response.state === 'EXITED') {
          emptyMsgEl.textContent = '이미 퇴실했습니다.';
          showState('empty');
          return;
        }
        if (data.response.state === 'NOT_ENTERED') {
          emptyMsgEl.textContent = '입실을 먼저 해주세요.';
          showState('empty');
          return;
        }
        if (data.response.state === 'COMPLETED') {
          // 퇴실 전 로그아웃 후 재로그인 등으로 홈에 왔는데 PENDING 추천은 없고(=책을 이미 다 읽음),
          // 최근에 끝낸 책이 있는 경우 — 완료 화면(틀린 문제 다시 풀기/심화 문제 풀기/책 추천받기)을
          // 그대로 재사용한다(2026-08-25)
          loadCompletionState(data.response.book.contentId);
          return;
        }
        renderBook(data.response.book);
      } catch (err) {
        showError(err);
      }
    }

    // 완료 화면 상태 조회 — 남은 액션(틀린 문제 다시 풀기/심화 문제 풀기)을 매번 최신 DB 상태로
    // 다시 계산한다(2026-08-25)
    async function loadCompletionState(forContentId) {
      showState('loading');
      try {
        const res = await fetch(`/clinic/completion-state?studentId=${encodeURIComponent(studentId)}&contentId=${encodeURIComponent(forContentId)}`);
        const data = await res.json();
        if (!data.success) throw new Error(data.error?.message ?? '조회에 실패했어요.');
        renderCompletion(data.response);
      } catch (err) {
        showError(err);
      }
    }

    if (isCompletionMode && completionContentId) {
      loadCompletionState(completionContentId);
    } else {
      loadHomeState();
    }
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
