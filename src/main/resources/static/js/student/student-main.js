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
    const mainPageEl = document.getElementById('mainPage');
    const logoutStudentId = mainPageEl ? mainPageEl.getAttribute('data-student-id') : null;

    // 예전엔 로컬 저장소만 지우고 이동해서 서버 세션(HttpSession)이 그대로 살아있었다 — 모니터링에
    // "문제 푸는 중"이 계속 남는 등 서버가 로그아웃 사실을 전혀 몰랐다(2026-08-26). /student/logout을
    // 먼저 호출해 세션을 무효화하고 "문제 푸는 중"/"결과 확인중" 표시를 해제한 뒤 이동한다.
    async function doLogout() {
      try {
        await fetch('/student/logout', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ studentId: logoutStudentId }),
        });
      } catch (err) {
        console.error(err);
      }
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

    initBookcaseModal();
  });

  // "더보기"(카드 컬렉션 / 이번 달에 읽은 책) → student-bookcase.html 을 iframe 으로 띄운다.
  // 같은 화면에 ?type=card / ?type=book 만 바꿔 데이터를 갈아끼운다. 닫기는 iframe 안에서
  // postMessage({type:'bookcase:close'}) 를 보내면 여기서 받아 닫는다(2026-09-02).
  function initBookcaseModal() {
    const modal = document.getElementById('bookcaseModal');
    const frame = document.getElementById('bookcaseFrame');
    if (!modal || !frame) return;

    const page = document.getElementById('mainPage');
    const studentId = page ? page.getAttribute('data-student-id') : null;

    function openBookcase(type) {
      const params = new URLSearchParams({ type: type === 'card' ? 'card' : 'book' });
      if (studentId) params.set('studentId', studentId);
      frame.src = `/student/bookcase?${params.toString()}`;
      modal.hidden = false;
    }

    function closeBookcase() {
      modal.hidden = true;
      frame.src = 'about:blank';
    }

    document.querySelectorAll('.more-link[data-bookcase]').forEach((link) => {
      link.addEventListener('click', (e) => {
        e.preventDefault();
        openBookcase(link.dataset.bookcase);
      });
    });

    window.addEventListener('message', (e) => {
      if (e.source === frame.contentWindow && e.data && e.data.type === 'bookcase:close') {
        closeBookcase();
      }
    });
  }

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
    const holdNoteEl = document.getElementById('bookHoldNote');
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
    const completionRetryBtn = document.getElementById('completionRetryBtn');
    const completionWrongRetryBtn = document.getElementById('completionWrongRetryBtn');
    const completionAdvancedBtn = document.getElementById('completionAdvancedBtn');
    // "여권 쓰러 가기"(2026-09-03) — 결과 화면과 같은 버튼이고 동작은 로그아웃이다
    const completionPassportBtn = document.getElementById('completionPassportBtn');
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

      // 이어 읽는 책이면 어디부터 읽으면 되는지 알려준다(2026-09-03) — 선생님이 자물쇠로 쪽수를
      // 적어둔 경우에만 나온다. 쪽수를 안 적고 홀딩된 책은 그냥 평소처럼 보인다.
      if (holdNoteEl) {
        const hasHoldPage = book.holdPage != null;
        holdNoteEl.hidden = !hasHoldPage;
        if (hasHoldPage) holdNoteEl.textContent = `지난번에 ${book.holdPage}쪽까지 읽었어요. 이어서 읽어볼까요?`;
      }

      // 메타 정보는 값이 없어도 행을 숨기지 않고 "-"로 채운다 (정보 영역 높이 고정)
      metaTypeEl.textContent = [book.contentTypeName, book.genreName].filter(Boolean).join(', ') || '-';
      metaAwardEl.textContent = book.awardName || '-';
      metaCurriculumEl.textContent = book.curriculumName || '-';
      // 해시태그만 예외 — 없을 때 "-"를 찍지 않고 자리(높이)만 비워 둔다
      metaTagsEl.textContent = book.keywords
        ? book.keywords.split(',').map((kw) => `#${kw.trim()}`).join(' ')
        : '';
    }

    // ── 추천 책 바뀜 감지 (2026-09-02) ──────────────────────────────────────────
    // 추천된 책이 서가에 없거나 훼손된 경우 선생님이 모니터링 화면에서 다른 책으로 교체한다.
    // 이 화면은 진입 시 1회만 상태를 읽어서, 그대로 두면 학생 폰엔 없는 책이 계속 떠 있고 그 상태로
    // 문제풀이에 들어가면 이미 지워진 추천 이력을 찾다가 에러가 난다. 그래서 책을 보여주는 동안에만
    // 조용히 상태를 다시 물어보고, 실제로 바뀌었을 때만 화면을 다시 그린다(버튼이나 안내 UI는 없다).
    const BOOK_POLL_MS = 15000;
    let currentBookContentId = null;
    let bookPollTimer = null;

    function stopBookPoll() {
      if (bookPollTimer) clearInterval(bookPollTimer);
      bookPollTimer = null;
    }

    function startBookPoll() {
      stopBookPoll();
      bookPollTimer = setInterval(pollBookChange, BOOK_POLL_MS);
    }

    async function pollBookChange() {
      // 화면이 가려져 있으면(폰을 덮어둠 등) 굳이 서버를 두드리지 않는다 — 돌아올 때 한 번 확인한다
      if (document.hidden) return;
      try {
        const res = await fetch('/clinic/quiz-home-state', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ studentId }),
        });
        const data = await res.json();
        if (!data.success) return;
        const changed = data.response.state !== 'READING'
          || (data.response.book?.contentId ?? null) !== currentBookContentId;
        if (!changed) return;
        stopBookPoll();
        loadHomeState();
      } catch (err) {
        // 폴링 실패는 조용히 넘긴다 — 다음 주기에 다시 확인한다. 여기서 화면을 건드리면
        // 잠깐 끊긴 네트워크 때문에 멀쩡히 보던 책이 에러 화면으로 바뀐다.
        console.debug('책 상태 확인 실패(다음 주기에 재시도)', err);
      }
    }

    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && bookPollTimer) pollBookChange();
    });

    // 문제풀이 화면 홈은 이미 확정된 PENDING 추천 책만 보여준다 — 입실/추천은 출석체크 기기에서만 일어난다
    function renderBook(book) {
      fillBookInfo(book);
      currentBookContentId = book.contentId;
      startBookPoll();
      actionBtn.hidden = false;
      completionActions.hidden = true;
      recommendNextBtn.hidden = true;

      actionLabel.textContent = '문제 풀기';
      actionBtn.onclick = () => {
        window.location.href = `/student/question?studentId=${encodeURIComponent(studentId)}&contentId=${book.contentId}`;
      };

      showState('card');
    }

    /* ── 완료 화면 버튼 규칙 (2026-09-03 전면 재정리) ──────────────────────────
       결과 화면(student-result.js)과 **똑같은** 규칙을 쓴다. 두 화면이 다르면 "홈으로"를
       눌렀을 때 갑자기 다른 버튼이 나타나 학생이 흐름을 잃는다.

         1. 재도전(기본 합격선 미달)     → 문제 풀러 가기 (책을 다시 읽고 온 상태)
         2. 독서완료(합격선~만점 미만)   → 재도전 + 틀린 문제 다시 풀기
            2-2. 틀린 문제를 다 맞히면   → 재도전 + 심화 문제 풀기
         3. 독서왕(기본 만점)            → 심화 문제 풀기
         4. 심화완료(심화 만점 아님)     → 재도전 + 틀린 문제 다시 풀기
            4-2. 틀린 문제를 다 맞히면   → 재도전 + 여권 쓰러 가기
         5. 심화왕(심화 만점)            → 여권 쓰러 가기            ※ 로그아웃

       단계는 서로 배타적이다 — 심화를 한 번이라도 풀었으면 4·5단계이고, 그 전이면 2·3단계다.
       그래서 재도전/틀린 문제 버튼이 기본과 심화 중 어느 쪽을 여는지도 단계가 결정한다.
       예전엔 "기본 재도전이냐 심화 재도전이냐"를 모달로 물어봤는데(retryTypeModal), 이제 물어볼
       일이 없어 모달째로 없앴다. 기본·심화 오답을 한 번에 푸는 병합 모드도 같은 이유로 사라졌다. */
    function renderCompletion(state) {
      // 완료 화면은 이미 다 읽은 책을 보여주는 자리라 교체 대상이 아니다 — 폴링을 멈춘다
      stopBookPoll();
      const book = state.book;
      fillBookInfo(book);
      currentBookContentId = book.contentId;

      const goQuestion = (level) => {
        window.location.href = `/student/question?studentId=${encodeURIComponent(studentId)}&contentId=${book.contentId}&qlevel=${level}`;
      };

      // "책 추천받기" — 어느 단계에서 보일지는 아래에서 정하고, 동작은 단계와 무관하게 같다
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

      // "여권 쓰러 가기"는 결과 화면과 마찬가지로 로그아웃이다 — 종이 여권을 쓰러 가는 행동이라
      // 앱에서 이동할 화면이 따로 없다. 상단 로그아웃 버튼의 doLogout은 다른 스코프(DOMContentLoaded
      // 콜백)에 있어 여기서 부를 수 없으므로 같은 처리를 직접 한다.
      completionPassportBtn.onclick = async () => {
        try {
          await fetch('/student/logout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ studentId }),
          });
        } catch (err) {
          console.error(err);
        }
        localStorage.clear();
        sessionStorage.clear();
        window.location.replace('/student');
      };

      // 버튼을 전부 끈 뒤 이 단계에 필요한 것만 켠다
      actionBtn.hidden = true;
      completionActions.hidden = false;
      completionRetryBtn.hidden = true;
      completionWrongRetryBtn.hidden = true;
      completionAdvancedBtn.hidden = true;
      completionPassportBtn.hidden = true;

      const failed = !state.grade || state.grade === 'RETRY';   // 1단계 — 기본 합격선 미달
      const advTried = state.advancedAttempted === true;        // 심화를 한 번이라도 풀었나
      const advKing = state.advancedKing === true;              // 심화 만점

      // 1단계 — 책을 다시 읽고 와서 QR로 들어온 상태다. 남은 액션이 아니라 "문제 풀러 가기"
      // 하나만 있으면 된다(결과 화면의 "다시 읽으러 가기"가 이 화면으로 돌아오는 길이다).
      if (failed) {
        completionActions.hidden = true;
        recommendNextBtn.hidden = true;
        actionBtn.hidden = false;
        actionLabel.textContent = '문제 풀러 가기';
        actionBtn.onclick = () => goQuestion('01');
        showState('card');
        return;
      }

      // 5단계 — 심화왕. 여권으로 끝낸다(기본 오답이 남아 있어도 더 붙잡지 않는다).
      if (advKing) {
        completionPassportBtn.hidden = false;
        recommendNextBtn.hidden = true;   // 여권 쓰러 가면 끝 — 다음 책은 출석 기기에서 받는다
        showState('card');
        return;
      }

      // 4단계 — 심화를 풀었고 아직 만점이 아니다. 재도전/틀린 문제는 모두 심화(02) 대상.
      if (advTried) {
        renderRetryStage('02', state.advancedWrongQnums ?? [], completionPassportBtn, goQuestion);
        // 심화가 아직 안 끝났으므로 다음 책은 내주지 않는다(심화 게이트와 같은 취지)
        recommendNextBtn.hidden = true;
        showState('card');
        return;
      }

      // 3단계 — 독서왕(기본 만점). 심화만 남는다.
      if (state.grade === 'KING') {
        completionAdvancedBtn.hidden = state.advancedAvailable !== true;
        // 풀 심화 문항이 아예 없는 책이면 여기서 끝이라 다음 책을 받을 수 있다
        recommendNextBtn.hidden = state.canRecommendNext === false || state.advancedAvailable === true;
        completionAdvancedBtn.onclick = () => goQuestion('02');
        showState('card');
        return;
      }

      // 2단계 — 독서완료. 재도전/틀린 문제는 기본(01) 대상이고, 오답을 다 맞히면 심화가 열린다.
      renderRetryStage('01', state.wrongQnums ?? [], completionAdvancedBtn, goQuestion);
      recommendNextBtn.hidden = state.canRecommendNext === false || state.advancedAvailable === true;
      showState('card');
    }

    /**
     * 2·4단계 공통 — 재도전은 항상 열고, 나머지 한 자리는 남은 오답 유무로 갈린다.
     * @param level   이 단계에서 다시 풀 문제의 난이도 ('01' 기본 / '02' 심화)
     * @param wrong   아직 틀린 채로 남은 문항 번호
     * @param nextBtn 오답을 다 맞혔을 때 그 자리에 들어올 버튼(심화 문제 풀기 / 여권 쓰러 가기)
     */
    function renderRetryStage(level, wrong, nextBtn, goQuestion) {
      completionRetryBtn.hidden = false;
      completionRetryBtn.onclick = () => goQuestion(level);

      if (wrong.length > 0) {
        completionWrongRetryBtn.hidden = false;
        completionWrongRetryBtn.onclick = () => {
          // 다시 풀 문항 번호만 넘겨주면 student-question.js가 그 번호만 걸러서 낸다
          sessionStorage.setItem('retryQnums', JSON.stringify(wrong));
          goQuestion(level);
        };
        return;
      }
      nextBtn.hidden = false;
      // 심화 버튼이 이 자리에 오는 경우(2-2)만 이동이 필요하다 — 여권은 로그아웃이라 아래에서 따로 건다
      if (nextBtn === completionAdvancedBtn) nextBtn.onclick = () => goQuestion('02');
    }

    // 이용권 소진(MonitorService.enterSession)은 시스템 오류가 아니라 결제/재계약이 필요한
    // 정상적인 업무 상황이라, 같은 "실패" 톤이 아니라 전용 카드(passExhausted)로 구분해서 보여준다.
    // 서버가 별도 에러 코드를 내려주지 않아 메시지 문구로 구분한다 — enterSession의 문구와 짝이 맞아야 한다.
    function showError(err) {
      stopBookPoll();
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
