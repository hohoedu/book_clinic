(function () {
  const page = document.getElementById('questionPage');
  const contentId = page ? page.getAttribute('data-content-id') : null;
  const studentId = page ? page.getAttribute('data-student-id') : null;
  const qlevel = (page ? page.getAttribute('data-qlevel') : null) || '01';
  const isAdvanced = qlevel === '02';

  const emptyState = document.getElementById('emptyState');
  const loadingState = document.getElementById('loadingState');
  const gradingState = document.getElementById('gradingState');
  const quizCard = document.getElementById('quizCard');
  const resultCard = document.getElementById('resultCard');
  const resultMascot = document.getElementById('resultMascot');
  const resultGradePill = document.getElementById('resultGradePill');
  const resultTitle = document.getElementById('resultTitle');
  const passLineText = document.getElementById('passLineText');
  const resultExp = document.getElementById('resultExp');

  const qIndexEl = document.getElementById('qIndex');
  const qTotalEl = document.getElementById('qTotal');
  const progressBar = document.getElementById('progressBar');
  const qexBox = document.getElementById('qexBox');
  const qexText = document.getElementById('qexText');
  const qText = document.getElementById('qText');
  const choiceList = document.getElementById('choiceList');
  const prevBtn = document.getElementById('prevBtn');
  const checkBtn = document.getElementById('checkBtn');
  const nextBtn = document.getElementById('nextBtn');
  const retryBtn = document.getElementById('retryBtn');
  const advancedBtn = document.getElementById('advancedBtn');
  const goMainBtn = document.getElementById('goMainBtn');
  const scoreCorrectEl = document.getElementById('scoreCorrect');
  const scoreTotalEl = document.getElementById('scoreTotal');

  let questions = [];
  let current = 0;
  let selected = null;
  let answered = [];
  let choiceOrders = [];

  function shuffle(arr) {
    const result = arr.slice();
    for (let i = result.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      [result[i], result[j]] = [result[j], result[i]];
    }
    return result;
  }

  function showState(name) {
    emptyState.hidden = name !== 'empty';
    loadingState.hidden = name !== 'loading';
    gradingState.hidden = name !== 'grading';
    quizCard.hidden = name !== 'quiz';
    resultCard.hidden = name !== 'result';
  }

  async function loadQuestions() {
    if (!contentId || contentId === 'null' || contentId === '') {
      showState('empty');
      return;
    }

    showState('loading');

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
      choiceOrders = new Array(questions.length).fill(null);
      current = 0;
      renderQuestion();
      showState('quiz');
    } catch (err) {
      console.error(err);
      document.getElementById('emptyStateMsg').textContent = '문제를 불러오는 중 오류가 발생했어요.';
      showState('empty');
    }
  }

  function renderQuestion() {
    const q = questions[current];
    selected = answered[current] ? answered[current].selected : null;

    qIndexEl.textContent = current + 1;
    qTotalEl.textContent = questions.length;
    progressBar.style.width = `${((current + 1) / questions.length) * 100}%`;

    if (q.qex) {
      qexText.textContent = q.qex;
      qexBox.hidden = false;
    } else {
      qexBox.hidden = true;
    }

    qText.textContent = q.q;

    if (!choiceOrders[current]) {
      const baseChoices = [
        { num: 1, text: q.e1 },
        { num: 2, text: q.e2 },
        { num: 3, text: q.e3 },
        { num: 4, text: q.e4 },
      ].filter((c) => c.text);
      choiceOrders[current] = shuffle(baseChoices);
    }
    const choices = choiceOrders[current];

    choiceList.innerHTML = '';
    choices.forEach((c, idx) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'choice-item';
      btn.dataset.num = c.num;
      btn.innerHTML = `<span class="choice-num">${idx + 1}</span><span>${escapeHtml(c.text)}</span>`;
      btn.addEventListener('click', () => selectChoice(c.num));
      choiceList.appendChild(btn);
    });

    prevBtn.hidden = current === 0;

    if (answered[current]) {
      showAnswerState();
    } else {
      checkBtn.disabled = true;
      checkBtn.hidden = false;
      nextBtn.hidden = true;
      updateSelectedVisual();
    }
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  function selectChoice(num) {
    if (answered[current]) return;
    selected = num;
    checkBtn.disabled = false;
    updateSelectedVisual();
  }

  function updateSelectedVisual() {
    choiceList.querySelectorAll('.choice-item').forEach((btn) => {
      btn.classList.toggle('selected', Number(btn.dataset.num) === selected);
    });
  }

  function showAnswerState() {
    const q = questions[current];
    const correctNum = Number(q.ans);
    const picked = answered[current].selected;

    choiceList.querySelectorAll('.choice-item').forEach((btn) => {
      const num = Number(btn.dataset.num);
      btn.disabled = true;
      if (num === correctNum) btn.classList.add('correct');
      else if (num === picked) btn.classList.add('wrong');
    });

    checkBtn.hidden = true;
    nextBtn.hidden = false;
    nextBtn.textContent = current === questions.length - 1 ? '결과 보기' : '다음 문제';
    if (current !== questions.length - 1) {
      nextBtn.innerHTML = '다음 문제<i class="fa-solid fa-arrow-right" aria-hidden="true"></i>';
    }
  }

  function checkAnswer() {
    if (selected == null) return;
    const q = questions[current];
    answered[current] = { qnum: q.qnum, selected, correct: Number(q.ans) === selected };
    showAnswerState();
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
    const correctCount = answered.filter((a) => a && a.correct).length;
    const totalCount = questions.length;

    // 심화문제(qlevel=02)는 등급/EXP 없이 결과만 보여준다 — 기본 문제풀이(qlevel=01)만 채점 API를 호출
    if (isAdvanced) {
      renderAdvancedResult(correctCount, totalCount);
      return;
    }

    showState('grading');

    // 정답 수는 서버가 문항별 제출 답안(qnum+selected)을 itempool.ans와 직접 대조해서 계산한다
    // (클라이언트에서 계산한 correctCount/totalCount는 devtools로 조작 가능해서 신뢰하지 않는다)
    const answersPayload = answered
      .filter((a) => a)
      .map((a) => ({ qnum: a.qnum, selected: a.selected }));

    try {
      const res = await fetch('/clinic/quiz/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ studentId, contentId: Number(contentId), answers: answersPayload }),
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error?.message ?? '채점에 실패했어요.');
      renderResult(data.response);
    } catch (err) {
      console.error(err);
      // 채점 서버 호출이 실패해도 학생이 결과를 볼 수 있도록 클라이언트 집계값으로 대체 표시(재도전 취급)
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

  function renderAdvancedResult(correctCount, totalCount) {
    scoreCorrectEl.textContent = correctCount;
    scoreTotalEl.textContent = totalCount;
    passLineText.textContent = '';
    resultExp.hidden = true;

    resultGradePill.classList.remove('king', 'retry');
    resultGradePill.textContent = '심화문제';
    resultMascot.innerHTML = '<i class="fa-solid fa-star" aria-hidden="true"></i>';
    resultTitle.textContent = '심화문제까지 다 풀었어요!';

    retryBtn.hidden = true;
    advancedBtn.hidden = true;
    goMainBtn.hidden = false;

    showState('result');
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

    showState('result');
  }

  function retry() {
    answered = new Array(questions.length).fill(null);
    choiceOrders = new Array(questions.length).fill(null);
    current = 0;
    renderQuestion();
    showState('quiz');
  }

  checkBtn.addEventListener('click', checkAnswer);
  nextBtn.addEventListener('click', goNext);
  prevBtn.addEventListener('click', goPrev);
  retryBtn.addEventListener('click', retry);

  loadQuestions();
})();
