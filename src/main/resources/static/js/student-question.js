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
  const resultCard = document.getElementById('resultCard');
  const resultMascot = document.getElementById('resultMascot');
  const resultGradePill = document.getElementById('resultGradePill');
  const resultTitle = document.getElementById('resultTitle');
  const passLineText = document.getElementById('passLineText');
  const resultExp = document.getElementById('resultExp');

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
  const retryBtn = document.getElementById('retryBtn');
  const advancedBtn = document.getElementById('advancedBtn');
  const goMainBtn = document.getElementById('goMainBtn');
  const scoreCorrectEl = document.getElementById('scoreCorrect');
  const scoreTotalEl = document.getElementById('scoreTotal');

  // 문제 유형(erp_bookstore_code gubun='T') 코드별 아이콘/설명 — 서버 데이터에 없는 화면용 고정 문구
  const QTYPE_INFO = {
    '01': { name: '이해', icon: 'fa-book-open', desc: '이야기의 내용을 정확히 파악하고 기억하는 능력이에요. 등장인물과 사건을 잘 떠올려보세요!' },
    '02': { name: '표현', icon: 'fa-comment-dots', desc: '자신의 생각을 글이나 말로 표현하는 능력이에요. 이야기 속 표현을 참고해보세요!' },
    '03': { name: '논리', icon: 'fa-link', desc: '이야기를 앞뒤 관계를 바탕으로 이유와 관계를 생각하는 능력이에요. 문제의 단서를 잘 살펴보세요!' },
    '04': { name: '사고', icon: 'fa-lightbulb', desc: '이야기를 깊이 생각하고 스스로 판단하는 능력이에요. 다양한 가능성을 떠올려보세요!' },
    '05': { name: '감정', icon: 'fa-heart', desc: '등장인물의 마음과 감정을 이해하는 능력이에요. 내가 그 상황이라면 어땠을지 생각해보세요!' },
    '06': { name: '어휘', icon: 'fa-spell-check', desc: '낱말의 뜻을 정확히 알고 활용하는 능력이에요. 문맥 속에서 낱말의 의미를 찾아보세요!' },
    '07': { name: '지식', icon: 'fa-graduation-cap', desc: '이야기와 관련된 배경지식을 아는 능력이에요. 알고 있는 내용을 잘 떠올려보세요!' },
    '08': { name: '문법', icon: 'fa-pen', desc: '문장을 바르게 이해하고 사용하는 능력이에요. 문장의 짜임을 잘 살펴보세요!' },
  };

  let questions = [];
  let current = 0;
  let answered = [];

  function showState(name) {
    emptyState.hidden = name !== 'empty';
    loadingState.hidden = name !== 'loading';
    gradingState.hidden = name !== 'grading';
    quizLayout.hidden = name !== 'quiz';
    quizActions.hidden = name !== 'quiz';
    resultCard.hidden = name !== 'result';
  }

  // 사이드바 도서 표지/제목 — 이미 추천/대여 확정된 책이므로 멱등 API를 그대로 재사용해 정보만 조회
  async function loadBookInfo() {
    if (!studentId) return;
    try {
      const res = await fetch('/clinic/recommend', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ studentId }),
      });
      const data = await res.json();
      if (!data.success) return;
      const book = data.response;
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
    loadBookInfo();

    try {
      const res = await fetch(`/question/search?contentId=${encodeURIComponent(contentId)}&state=S&qlevel=${encodeURIComponent(qlevel)}`);
      const data = await res.json();
      if (!data.success) throw new Error(data.error?.message ?? '문제를 불러오지 못했어요.');

      questions = (data.response ?? []).filter((q) => q.q);
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

    qBadge.textContent = `Q${current + 1}`;
    qIndexEl.textContent = current + 1;
    qTotalEl.textContent = questions.length;
    renderProgressDots();
    renderQtype(q.qtype);

    if (q.qex) {
      qexText.textContent = q.qex;
      qexBox.hidden = false;
    } else {
      qexBox.hidden = true;
    }

    qText.textContent = q.q;

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
      btn.innerHTML = `<span class="choice-num">${c.num}</span><span>${escapeHtml(c.text)}</span>`;
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
    qtypeIcon.innerHTML = `<i class="fa-solid ${info.icon}" aria-hidden="true"></i>`;
    qtypeName.textContent = info.name;
    qtypeDesc.textContent = info.desc;
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
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
    // 풀이 중에는 정답 여부를 계산하지 않으므로, 결과 시점에 문항별 선택과 itempool.ans를 대조해 집계한다
    // (qlevel=01은 어차피 서버가 다시 채점하고, 이 값은 심화문제 결과와 서버 오류 폴백 표시에만 쓰인다)
    const correctCount = questions.filter((q, i) => answered[i] && Number(q.ans) === answered[i].selected).length;
    const totalCount = questions.length;

    showState('grading');

    // 정답 수는 서버가 문항별 제출 답안(qnum+selected)을 itempool.ans와 직접 대조해서 계산한다
    // (클라이언트에서 계산한 correctCount/totalCount는 devtools로 조작 가능해서 신뢰하지 않는다)
    // 심화문제(qlevel=02)도 풀이 이력을 남기기 위해 동일하게 제출한다 — 서버는 이력 기록과 채점만
    // 하고 등급/EXP 처리는 하지 않는다
    const answersPayload = answered
      .filter((a) => a)
      .map((a) => ({ qnum: a.qnum, selected: a.selected }));

    try {
      const res = await fetch('/clinic/quiz/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ studentId, contentId: Number(contentId), qlevel, answers: answersPayload }),
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error?.message ?? '채점에 실패했어요.');
      if (isAdvanced) {
        renderAdvancedResult(data.response.correctCount, data.response.totalCount, data.response.newBadges);
      } else {
        renderResult(data.response);
      }
    } catch (err) {
      console.error(err);
      // 채점 서버 호출이 실패해도 학생이 결과를 볼 수 있도록 클라이언트 집계값으로 대체 표시(재도전 취급)
      if (isAdvanced) {
        renderAdvancedResult(correctCount, totalCount);
        return;
      }
      renderResult({
        passed: false,
        grade: null,
        correctCount,
        totalCount,
        passLine: Math.ceil(totalCount * (2 / 3)),
        expGained: null,
      });
    }
  }

  function renderAdvancedResult(correctCount, totalCount, newBadges) {
    scoreCorrectEl.textContent = correctCount;
    scoreTotalEl.textContent = totalCount;
    passLineText.textContent = '';
    resultExp.hidden = true;

    resultGradePill.classList.remove('king', 'retry');
    resultGradePill.textContent = '심화문제';
    resultMascot.innerHTML = '<i class="fa-solid fa-star" aria-hidden="true"></i>';
    resultTitle.textContent = '심화문제까지 다 풀었어요!';

    renderNewBadges(newBadges);

    retryBtn.hidden = true;
    advancedBtn.hidden = true;
    goMainBtn.hidden = false;

    showState('result');
  }

  // 이번 제출로 새로 획득한 뱃지 목록 (서버가 로그 재계산으로 판정해서 내려준다)
  function renderNewBadges(newBadges) {
    const box = document.getElementById('resultBadges');
    const list = document.getElementById('resultBadgeList');
    list.innerHTML = '';
    if (!newBadges || newBadges.length === 0) {
      box.hidden = true;
      return;
    }
    newBadges.forEach((b) => {
      const li = document.createElement('li');
      li.className = 'result-badge-item';
      const name = document.createElement('strong');
      name.textContent = b.badgeName;
      const desc = document.createElement('span');
      desc.textContent = b.badgeDesc;
      li.append(name, desc);
      list.appendChild(li);
    });
    box.hidden = false;
  }

  function renderResult(result) {
    scoreCorrectEl.textContent = result.correctCount;
    scoreTotalEl.textContent = result.totalCount;
    passLineText.textContent = `(합격선 ${result.passLine}개)`;

    resultGradePill.classList.remove('king', 'retry');
    if (result.grade === 'KING') {
      resultGradePill.textContent = '독서왕';
      resultGradePill.classList.add('king');
      resultMascot.innerHTML = '<i class="fa-solid fa-crown" aria-hidden="true"></i>';
      resultTitle.textContent = '독서왕이 됐어요!';
      retryBtn.hidden = true;
      advancedBtn.hidden = false;
      goMainBtn.hidden = false;
    } else if (result.grade === 'FRIEND') {
      resultGradePill.textContent = '독서친구';
      resultMascot.innerHTML = '<i class="fa-solid fa-trophy" aria-hidden="true"></i>';
      resultTitle.textContent = '독서친구가 됐어요!';
      retryBtn.hidden = false;
      advancedBtn.hidden = false;
      goMainBtn.hidden = false;
    } else {
      resultGradePill.textContent = '재도전 필요';
      resultGradePill.classList.add('retry');
      resultMascot.innerHTML = '<i class="fa-solid fa-rotate-right" aria-hidden="true"></i>';
      resultTitle.textContent = '합격선에 조금 못 미쳤어요. 다시 풀어볼까요?';
      retryBtn.hidden = false;
      advancedBtn.hidden = true;
      goMainBtn.hidden = false;
    }

    if (result.alreadyCompleted) {
      resultTitle.textContent = '이미 완독한 책이에요!';
      resultExp.textContent = '다시 풀어도 추가 경험치는 없어요.';
      resultExp.hidden = false;
    } else if (result.expGained != null && result.expGained > 0) {
      resultExp.textContent = result.leveledUp
        ? `EXP +${result.expGained} 획득! 레벨 ${result.levelNo}(으)로 레벨업했어요 🎉`
        : `EXP +${result.expGained} 획득!`;
      resultExp.hidden = false;
    } else {
      resultExp.hidden = true;
    }

    renderNewBadges(result.newBadges);

    showState('result');
  }

  function retry() {
    answered = new Array(questions.length).fill(null);
    current = 0;
    showState('quiz');
    renderQuestion();
  }

  nextBtn.addEventListener('click', goNext);
  prevBtn.addEventListener('click', goPrev);
  retryBtn.addEventListener('click', retry);

  loadQuestions();
})();
