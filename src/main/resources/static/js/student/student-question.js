(function () {
  const page = document.getElementById('questionPage');
  const contentId = page ? page.getAttribute('data-content-id') : null;
  const studentId = page ? page.getAttribute('data-student-id') : null;
  const qlevel = (page ? page.getAttribute('data-qlevel') : null) || '01';
  const isAdvanced = qlevel === '02';

  const emptyState = document.getElementById('emptyState');
  const loadingState = document.getElementById('loadingState');
  const gradingState = document.getElementById('gradingState');
  const quizLayout = document.getElementById('quizLayout');

  const sideBookImg = document.getElementById('sideBookImg');
  const sideBookTitle = document.getElementById('sideBookTitle');
  const qBadge = document.getElementById('qBadge');
  const qIndexEl = document.getElementById('qIndex');
  const qTotalEl = document.getElementById('qTotal');
  const progressDots = document.getElementById('progressDots');
  const qtypeIcon = document.getElementById('qtypeIcon');
  const qtypeName = document.getElementById('qtypeName');
  const qtypeDesc = document.getElementById('qtypeDesc');
  const qexBox = document.getElementById('qexBox');
  const qexText = document.getElementById('qexText');
  const qText = document.getElementById('qText');
  const choiceList = document.getElementById('choiceList');
  const quizCard = document.getElementById('quizCard');
  const quizActions = document.getElementById('quizActions');
  const prevBtn = document.getElementById('prevBtn');
  const nextBtn = document.getElementById('nextBtn');

  // 문제 유형(erp_bookstore_code gubun='T') 코드별 아이콘 이미지/설명 — 서버 데이터에 없는 화면용 고정 문구
  // img는 /images/icons/ 아래 PNG 파일명. 06 어휘는 심화(qlevel=02) 진입 시 advance_voca로 바꾼다.
  // color는 유형 이름(하단 h3)에 쓸 색 — 사용자 지정값. 배열이면 그라데이션(글자에 background-clip)
  const QTYPE_INFO = {
    '01': { name: '이해', img: 'comp.png', color: '#89EED3', desc: '이야기의 내용을 정확히 파악하고 기억하는 능력이에요. 등장인물과 사건을 잘 떠올려보세요!' },
    '02': { name: '표현', img: 'expr.png', color: '#FDBB59', desc: '자신의 생각을 글이나 말로 표현하는 능력이에요. 이야기 속 표현을 참고해보세요!' },
    '03': { name: '논리', img: 'logic.png', color: '#8FC7FD', desc: '이야기의 앞뒤 관계를 바탕으로 이유와 결과를 생각하는 능력이에요. 문제의 단서를 잘 살펴보세요!' },
    '04': { name: '사고', img: 'think.png', color: '#FEE660', desc: '이야기를 깊이 생각하고 스스로 판단하는 능력이에요. 다양한 가능성을 떠올려보세요!' },
    '05': { name: '감정', img: 'emo.png', color: '#FD9EC2', desc: '등장인물의 마음과 감정을 이해하는 능력이에요. 내가 그 상황이라면 어땠을지 생각해보세요!' },
    '06': { name: '어휘', img: 'voca.png', color: '#C2AEFC', desc: '낱말의 뜻을 정확히 알고 활용하는 능력이에요. 문맥 속에서 낱말의 의미를 찾아보세요!' },
    '07': { name: '지식', img: 'know.png', color: '#89E3FA', desc: '이야기와 관련된 배경지식을 아는 능력이에요. 알고 있는 내용을 잘 떠올려보세요!' },
    '08': { name: '문법', img: 'advance_gram.png', color: ['#FFABE5', '#B797F9', '#8BCCFD'], desc: '문장을 바르게 이해하고 사용하는 능력이에요. 문장의 짜임을 잘 살펴보세요!' },
  };
  const ADVANCED_VOCA_COLOR = ['#FDA9A1', '#FECE83', '#A4F3CD'];

  // 유형 이름 h3에 단색 또는 그라데이션 색을 입힌다
  function applyNameColor(el, color) {
    if (Array.isArray(color)) {
      el.style.color = 'transparent';
      el.style.backgroundImage = `linear-gradient(90deg, ${color.join(', ')})`;
      el.style.webkitBackgroundClip = 'text';
      el.style.backgroundClip = 'text';
    } else {
      el.style.backgroundImage = 'none';
      el.style.webkitBackgroundClip = 'border-box';
      el.style.backgroundClip = 'border-box';
      el.style.color = color;
    }
  }

  // 유형 아이콘 PNG(100~300KB)를 미리 받아둔다 — 안 하면 유형이 바뀌는 문제로 넘어갈 때마다
  // 이미지가 뒤늦게 뜨는 팝인이 생긴다
  function preloadQtypeIcons() {
    const files = Object.values(QTYPE_INFO).map((v) => v.img);
    files.push('advance_voca.png');
    files.forEach((f) => {
      const img = new Image();
      img.src = `/images/icons/${f}`;
    });
  }

  let questions = [];
  let current = 0;
  let answered = [];
  // "틀린 문제 다시 풀기"로 진입했는지 — 채점 제출 시 mode=WRONG_ONLY로 보내 점수/등급을 고정한다.
  // (일반 재도전은 mode=RETRY로 보내 최종 점수/등급이 갱신된다. 첫 시도 여부는 서버가 판단한다.)
  let wrongOnlyMode = false;
  // 결과 화면 문구("OO을(를) 완독하고...")에 쓸 책 제목 — loadBookInfo에서 채운다
  let currentBookTitle = null;

  function showState(name) {
    emptyState.hidden = name !== 'empty';
    loadingState.hidden = name !== 'loading';
    gradingState.hidden = name !== 'grading';
    quizLayout.hidden = name !== 'quiz';
    quizActions.hidden = name !== 'quiz';
  }

  // 사이드바 도서 표지/제목 — 이 화면의 contentId 책 정보만 순수 조회한다(추천/대여 확정 부작용 없음).
  // "틀린 문제 다시 풀기"로 이미 DONE 처리된 책을 다시 열 때, /clinic/recommend(추천 확정 API)를
  // 재사용하면 PENDING 추천이 없어 엉뚱하게 다음 책을 새로 추천/대여해버렸다(2026-08-25 발견).
  async function loadBookInfo() {
    if (!contentId) return;
    try {
      const res = await fetch(`/clinic/book-info?contentId=${encodeURIComponent(contentId)}`);
      const data = await res.json();
      if (!data.success) return;
      const book = data.response;
      currentBookTitle = book.originalTitle ?? null;
      sideBookTitle.textContent = book.originalTitle ?? '-';
      if (book.imageUrl) sideBookImg.src = book.imageUrl;
    } catch (err) {
      console.error(err);
    }
  }

  async function loadQuestions() {
    if (!contentId || contentId === 'null' || contentId === '') {
      showState('empty');
      return;
    }

    showState('loading');
    preloadQtypeIcons();
    loadBookInfo();

    try {
      const res = await fetch(`/question/search?contentId=${encodeURIComponent(contentId)}&state=S&qlevel=${encodeURIComponent(qlevel)}`);
      const data = await res.json();
      if (!data.success) throw new Error(data.error?.message ?? '문제를 불러오지 못했어요.');

      questions = (data.response ?? []).filter((q) => q.q);

      // 결과 화면의 "틀린 문제 풀기"에서 넘어온 경우 — 지난 시도에서 틀린 문항만 추려서 다시 낸다
      const retryQnumsRaw = sessionStorage.getItem('retryQnums');
      if (retryQnumsRaw) {
        sessionStorage.removeItem('retryQnums');
        const retryQnums = JSON.parse(retryQnumsRaw);
        const filtered = questions.filter((q) => retryQnums.includes(q.qnum));
        if (filtered.length > 0) {
          questions = filtered;
          wrongOnlyMode = true;
        }
      }

      if (questions.length === 0) {
        document.getElementById('emptyStateMsg').textContent = '이 책에 등록된 문제가 아직 없어요.';
        showState('empty');
        return;
      }

      answered = new Array(questions.length).fill(null);
      current = 0;
      showState('quiz');
      renderQuestion();
    } catch (err) {
      console.error(err);
      document.getElementById('emptyStateMsg').textContent = '문제를 불러오는 중 오류가 발생했어요.';
      showState('empty');
    }
  }

  function renderQuestion() {
    const q = questions[current];

    // "틀린 문제 다시 풀기"처럼 일부 문항만 걸러서 낼 때도 원래 문제 번호(qnum)를 그대로 보여준다 —
    // 배열 순번(current+1)을 쓰면 3/5/8번을 틀렸는데 화면엔 1/2/3번으로 보이는 문제가 있었다(2026-08-25)
    qBadge.textContent = `Q${q.qnum}`;
    qIndexEl.textContent = current + 1;
    qTotalEl.textContent = questions.length;
    renderProgressDots();
    renderQtype(q.qtype);

    if (q.qex) {
      qexText.innerHTML = q.qex;
      qexBox.hidden = false;
    } else {
      qexBox.hidden = true;
    }

    qText.innerHTML = q.q;

    // 보기는 섞지 않고 등록된 순서(1~4) 그대로 보여준다
    const choices = [
      { num: 1, text: q.e1 },
      { num: 2, text: q.e2 },
      { num: 3, text: q.e3 },
      { num: 4, text: q.e4 },
    ].filter((c) => c.text);

    choiceList.innerHTML = '';
    choices.forEach((c) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'choice-item';
      btn.dataset.num = c.num;
      btn.innerHTML = `<span class="choice-num">${c.num}</span><span>${c.text}</span>`;
      btn.addEventListener('click', () => selectChoice(c.num));
      choiceList.appendChild(btn);
    });

    prevBtn.hidden = current === 0;
    quizActions.classList.toggle('has-prev', !prevBtn.hidden);

    updateNextButton();
    updateSelectedVisual();
  }

  function renderProgressDots() {
    progressDots.innerHTML = '';
    for (let i = 0; i < questions.length; i += 1) {
      const dot = document.createElement('span');
      dot.className = `dot${i <= current ? ' active' : ''}`;
      progressDots.appendChild(dot);
    }
  }

  function renderQtype(qtype) {
    const info = QTYPE_INFO[qtype] ?? QTYPE_INFO['03'];
    // 심화(qlevel=02)에서 어휘 유형은 심화 어휘 아이콘으로 교체
    const advVoca = isAdvanced && qtype === '06';
    const imgFile = advVoca ? 'advance_voca.png' : info.img;
    qtypeIcon.innerHTML = `<img src="/images/icons/${imgFile}" alt="${info.name} 유형" />`;
    qtypeName.textContent = info.name;
    applyNameColor(qtypeName, advVoca ? ADVANCED_VOCA_COLOR : info.color);
    qtypeDesc.textContent = info.desc;
  }

  // 문항별 정답 확인 없이 선택만 기록하고 다음으로 넘어간다 — 정답 여부는 마지막 결과에서만 보여준다
  // (다시 눌러 바꾸는 것도 마지막 제출 전까지 자유)
  function selectChoice(num) {
    const q = questions[current];
    answered[current] = { qnum: q.qnum, selected: num };
    updateSelectedVisual();
    updateNextButton();
  }

  function updateSelectedVisual() {
    const picked = answered[current] ? answered[current].selected : null;
    choiceList.querySelectorAll('.choice-item').forEach((btn) => {
      btn.classList.toggle('selected', Number(btn.dataset.num) === picked);
    });
  }

  function updateNextButton() {
    nextBtn.disabled = !answered[current];
    nextBtn.textContent = current === questions.length - 1 ? '결과 보기' : '다음 문제';
  }

  function goNext() {
    if (current === questions.length - 1) {
      showResult();
      return;
    }
    current += 1;
    renderQuestion();
  }

  function goPrev() {
    if (current === 0) return;
    current -= 1;
    renderQuestion();
  }

  async function showResult() {
    // 정답(itempool.ans)은 더 이상 화면으로 내려오지 않는다(2026-08-20, 정답 노출 차단) —
    // 정답 수도 오답 문항 목록도 전부 서버 채점 결과(/clinic/quiz/submit)를 쓴다.
    const totalCount = questions.length;

    showState('grading');

    // 정답 수는 서버가 문항별 제출 답안(qnum+selected)을 itempool.ans와 직접 대조해서 계산한다
    // (클라이언트에서 계산한 correctCount/totalCount는 devtools로 조작 가능해서 신뢰하지 않는다)
    // 심화문제(qlevel=02)도 풀이 이력을 남기기 위해 동일하게 제출한다 — 서버는 이력 기록과 채점만
    // 하고 등급/EXP 처리는 하지 않는다
    const answersPayload = answered
      .filter((a) => a)
      .map((a) => ({ qnum: a.qnum, selected: a.selected }));

    let result;
    try {
      const res = await fetch('/clinic/quiz/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ studentId, contentId: Number(contentId), qlevel, mode: wrongOnlyMode ? 'WRONG_ONLY' : 'RETRY', answers: answersPayload }),
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error?.message ?? '채점에 실패했어요.');
      result = isAdvanced
        ? { advanced: true, correctCount: data.response.correctCount, totalCount: data.response.totalCount, newBadges: data.response.newBadges }
        : { advanced: false, bookTitle: currentBookTitle, ...data.response };  // wrongQnums도 응답에 들어있다
    } catch (err) {
      console.error(err);
      // 채점 서버 호출이 실패한 경우. 정답을 모르니 점수를 계산할 수 없어 0점으로 두고 재도전으로
      // 표시한다 — 틀린 문제 목록도 만들 수 없으므로 비운다(재도전 버튼은 전체 다시 풀기로 동작).
      result = isAdvanced
        ? { advanced: true, correctCount: 0, totalCount, newBadges: null }
        : {
            advanced: false,
            passed: false,
            grade: null,
            correctCount: 0,
            totalCount,
            passLine: Math.ceil(totalCount * (2 / 3)),
            expGained: null,
            wrongQnums: [],
          };
    }

    // 결과 화면(student-result.html)이 읽어갈 채점 결과를 세션 저장소에 담아두고 이동한다
    sessionStorage.setItem('quizResult', JSON.stringify(result));
    window.location.href = `/student/result?studentId=${encodeURIComponent(studentId)}&contentId=${encodeURIComponent(contentId)}&qlevel=${encodeURIComponent(qlevel)}`;
  }

  nextBtn.addEventListener('click', goNext);
  prevBtn.addEventListener('click', goPrev);

  // 문제 푸는 중에 다른 기기에서 재로그인하거나 직원이 퇴실 처리하면, 예전엔 이 화면을 벗어나
  // (다음 페이지 이동) 전까지는 계속 문제를 풀 수 있었다(2026-08-26 발견). 주기적으로 세션이
  // 아직 유효한지 확인해서, 무효화됐으면 바로 로그인 화면으로 돌려보낸다.
  const SESSION_CHECK_INTERVAL_MS = 15000;
  const sessionCheckTimer = setInterval(async () => {
    try {
      const res = await fetch(`/student/session-check?studentId=${encodeURIComponent(studentId)}`);
      const data = await res.json();
      if (data.success && data.response && data.response.valid === false) {
        clearInterval(sessionCheckTimer);
        window.location.replace('/student/login');
      }
    } catch (err) {
      console.error(err);
    }
  }, SESSION_CHECK_INTERVAL_MS);

  loadQuestions();
})();
