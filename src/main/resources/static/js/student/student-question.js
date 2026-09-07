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
  // "틀린 문제 다시 풀기"(완료화면)에서 기본+심화 오답을 한 번에 푸는 모드 — 문항마다 q.__qlevel을
  // 달고, 제출은 레벨별로 나눠 각각 WRONG_ONLY로 보낸 뒤 결과를 합쳐서 결과 화면에 넘긴다(2026-09-02).
  let mergedMode = false;
  // 결과 화면 문구("OO을(를) 완독하고...")에 쓸 책 제목 — loadBookInfo에서 채운다
  let currentBookTitle = null;

  // ── 즉시 채점 (2026-09-03) ──────────────────────────────────────────────────
  // "틀린 문제 다시 풀기"를 두 번째로 할 때부터는 보기를 고르는 즉시 정답/오답을 보여주고 자동으로
  // 다음 문제로 넘어간다. 두 번째쯤 되면 답을 몰라서 못 맞히는 상태라, 계속 혼자 붙들고 있게
  // 하는 것보다 바로 알려주고 넘어가는 편이 낫다는 판단이다(키독 방식).
  // 판정과 정답은 서버가 내려준다(GET /clinic/wrong-retry-mode) — 첫 번째 다시 풀기에서는
  // 정답이 아예 오지 않으므로 화면이 답을 들고 있을 수 없다.
  let instantMode = false;
  let instantAnswers = {};      // qnum → 정답 보기 번호
  let instantLocked = false;    // 정답 공개 후 답을 바꿔 고르지 못하게 막는다

  // 문항 하나가 어느 난이도인지 — 병합 모드면 문항에 붙은 __qlevel, 아니면 페이지 qlevel
  function levelOf(q) {
    return q.__qlevel || qlevel;
  }

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

    // 완료화면 "틀린 문제 다시 풀기"(기본+심화 병합) — retryQnumsMerged가 있으면 이 경로로 처리한다
    const mergedRaw = sessionStorage.getItem('retryQnumsMerged');
    if (mergedRaw) {
      sessionStorage.removeItem('retryQnumsMerged');
      await loadMergedWrongQuestions(JSON.parse(mergedRaw));
      return;
    }

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
          await loadInstantMode();
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

  // 완료화면 "틀린 문제 다시 풀기" — map = { '01': [qnum...], '02': [qnum...] }.
  // 두 난이도 문제를 각각 불러와 틀린 문항만 추려 __qlevel을 달고 하나로 합친다(기본 먼저, 심화 다음).
  async function loadMergedWrongQuestions(map) {
    const levels = ['01', '02'].filter((lv) => (map[lv] ?? []).length > 0);
    try {
      const perLevel = await Promise.all(levels.map(async (lv) => {
        const res = await fetch(`/question/search?contentId=${encodeURIComponent(contentId)}&state=S&qlevel=${lv}`);
        const data = await res.json();
        if (!data.success) throw new Error(data.error?.message ?? '문제를 불러오지 못했어요.');
        const want = map[lv] ?? [];
        return (data.response ?? [])
          .filter((q) => q.q && want.includes(q.qnum))
          .map((q) => ({ ...q, __qlevel: lv }));
      }));
      questions = perLevel.flat();

      if (questions.length === 0) {
        document.getElementById('emptyStateMsg').textContent = '다시 풀 틀린 문제가 없어요.';
        showState('empty');
        return;
      }

      mergedMode = true;
      wrongOnlyMode = true;
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

  /* 두 번째 다시 풀기인지 서버에 물어본다. 즉시 채점이면 정답 맵도 함께 받는다.
     조회에 실패하면 조용히 기존 방식(스스로 풀고 제출)으로 둔다 — 답을 못 받은 채로 즉시 채점을
     켜면 모든 보기가 오답으로 보이므로, 실패 시엔 절대 켜지 않는다. */
  async function loadInstantMode() {
    try {
      const res = await fetch(`/clinic/wrong-retry-mode?studentId=${encodeURIComponent(studentId)}&contentId=${encodeURIComponent(contentId)}&qlevel=${encodeURIComponent(qlevel)}`);
      // 인증에 막히면 로그인 화면(HTML)이 돌아온다 — 그대로 json()을 부르면 예외가 나므로 먼저 거른다
      if (!res.ok) {
        console.warn('즉시 채점 여부 조회 실패 — 기존 방식으로 진행합니다:', res.status);
        return;
      }
      const data = await res.json();
      if (!data.success) return;
      const answers = data.response.answers ?? {};
      instantMode = data.response.instant === true && Object.keys(answers).length > 0;
      instantAnswers = answers;
      console.info(`틀린 문제 다시 풀기 ${(data.response.priorRounds ?? 0) + 1}회차 — 즉시 채점 ${instantMode ? 'ON' : 'OFF'}`);
    } catch (err) {
      console.error(err);
    }
  }

  function renderQuestion() {
    const q = questions[current];

    // "틀린 문제 다시 풀기"처럼 일부 문항만 걸러서 낼 때도 원래 문제 번호(qnum)를 그대로 보여준다 —
    // 배열 순번(current+1)을 쓰면 3/5/8번을 틀렸는데 화면엔 1/2/3번으로 보이는 문제가 있었다(2026-08-25)
    // 병합 모드에선 기본/심화 문제 번호가 겹칠 수 있어 심화 문항은 빨간 뱃지 + Q01·Q02 형식으로 구분한다.
    // 기본 문항은 기존 그대로(Q1 형식, 초록 뱃지).
    const advBadge = mergedMode && levelOf(q) === '02';
    qBadge.classList.toggle('is-advanced', advBadge);
    qBadge.textContent = advBadge ? `Q${String(q.qnum).padStart(2, '0')}` : `Q${q.qnum}`;
    qIndexEl.textContent = current + 1;
    qTotalEl.textContent = questions.length;
    renderProgressDots();
    renderQtype(q);

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

    // 즉시 채점에서는 O가 뜬 뒤 학생이 "다음 문제"를 눌러 직접 넘어간다 — 정답을 충분히 확인할
    // 시간을 학생이 정하게 두는 편이 낫다(2026-09-03, 자동 진행에서 변경).
    // 단 "이전 문제"는 감춘다 — 정답을 본 뒤 되돌아가 답을 고치면 채점이 무의미해진다.
    instantLocked = false;
    quizCard.classList.remove('reveal-correct', 'reveal-wrong');
    prevBtn.hidden = instantMode || current === 0;
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

  function renderQtype(q) {
    const qtype = q.qtype;
    const info = QTYPE_INFO[qtype] ?? QTYPE_INFO['03'];
    // 심화(qlevel=02)에서 어휘 유형은 심화 어휘 아이콘으로 교체 — 병합 모드는 문항별 난이도로 판단
    const advVoca = levelOf(q) === '02' && qtype === '06';
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
    if (instantMode && instantLocked) return;   // 이미 정답을 맞혔다 — 답을 바꿀 수 없다

    answered[current] = { qnum: q.qnum, selected: num, qlevel: levelOf(q) };
    updateSelectedVisual();

    if (instantMode) revealAnswer(q, num);
    updateNextButton();
  }

  /* 고른 보기가 맞았는지 그 자리에서 O/X로 보여준다 (2026-09-03 확정).
       정답 → O 를 띄우고 잠근다. "다음 문제"를 눌러 넘어간다.
       오답 → X 를 띄우고 그 보기만 막은 뒤, 정답을 맞힐 때까지 다시 고르게 한다.
     그래서 이 모드를 끝까지 풀면 모든 문항이 정답으로 남는다 — 남은 오답이 0이 되어 다음
     화면에서 "심화 문제 풀기"가 열린다(student-result.js / student-main.js의 버튼 규칙).
     서버에 올라가는 답도 마지막에 고른 정답이다.
     .correct / .wrong 은 student-question.css에 이미 있던 스타일인데(초록/빨강 테두리 +
     .choice-mark 색상) 여태 아무도 붙이지 않아 쓰이지 않던 클래스라 그대로 재사용한다. */
  function revealAnswer(q, picked) {
    const correctNum = Number(instantAnswers[q.qnum]);
    const isCorrect = picked === correctNum;
    const btn = choiceList.querySelector(`.choice-item[data-num="${picked}"]`);

    quizCard.classList.remove('reveal-correct', 'reveal-wrong');
    quizCard.classList.add(isCorrect ? 'reveal-correct' : 'reveal-wrong');
    if (!btn) return;

    if (isCorrect) {
      instantLocked = true;
      btn.classList.add('correct');
      markChoice(btn, 'O');
      return;
    }

    // 오답 — X를 남기고 그 보기는 다시 못 고르게 막는다. 정답을 맞히기 전엔 다음으로 못 넘어가도록
    // 선택을 지운다(updateNextButton이 answered를 보고 버튼을 잠근다).
    btn.classList.add('wrong');
    btn.disabled = true;
    markChoice(btn, 'X');
    answered[current] = null;
    updateSelectedVisual();
  }

  /** 보기 오른쪽에 O / X 표시를 붙인다 (이미 붙어 있으면 그대로 둔다) */
  function markChoice(btn, kind) {
    if (btn.querySelector('.choice-mark')) return;
    const mark = document.createElement('i');
    mark.className = kind === 'O'
      ? 'choice-mark fa-regular fa-circle'
      : 'choice-mark fa-solid fa-xmark';
    mark.setAttribute('aria-label', kind === 'O' ? '정답' : '오답');
    btn.appendChild(mark);
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

    if (mergedMode) {
      await showMergedResult();
      return;
    }

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
      // 심화도 기본과 똑같이 응답을 통째로 넘긴다(2026-09-03). 예전엔 필요한 필드만 골라 담느라
      // 서버가 채워 보낸 레벨(levelNo/progressPercent)·보유 뱃지(bookBadge)·카드(cardName)가 버려져서
      // 심화 결과 화면의 레벨 칸이 placeholder로 남고 카드 칸이 통째로 비어 보였다.
      result = { advanced: isAdvanced, bookTitle: currentBookTitle, ...data.response };  // wrongQnums도 응답에 들어있다
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

  // 완료화면 "틀린 문제 다시 풀기"(기본+심화 병합) 채점 — 다시 푼 난이도만 각각 WRONG_ONLY로 제출하고
  // (점수/등급/뱃지는 서버가 바꾸지 않음), 화면에는 기본+심화 성적을 합산해 한 번에 보여준다(2026-09-02).
  async function showMergedResult() {
    const byLevel = { '01': [], '02': [] };
    answered.filter((a) => a).forEach((a) => {
      (byLevel[a.qlevel] || byLevel['01']).push({ qnum: a.qnum, selected: a.selected });
    });

    const merged = {
      advanced: false,
      mergedWrong: true,
      bookTitle: currentBookTitle,
      correctCount: 0,
      totalCount: 0,
      wrongQnums: [],
      alreadyCompleted: true,
    };
    // 난이도별 "전체 기준" 정답/문항 수 — 합산해서 화면 헤드라인 점수로 쓴다
    const part = {};

    // 등급·레벨·뱃지 등 화면 표시값은 기본 문제 결과를 이어받는다(WRONG_ONLY라 변동 없음)
    const carryBasic = (src) => {
      merged.grade = src.grade;
      merged.passed = src.passed;
      merged.firstCorrectCount = src.firstCorrectCount;
      merged.finalCorrectCount = src.finalCorrectCount;
      merged.bookBadge = src.bookBadge;
      merged.attemptNo = src.attemptNo;
      merged.levelNo = src.levelNo;
      merged.levelTitle = src.levelTitle;
      merged.progressPercent = src.progressPercent;
      merged.booksToNextLevel = src.booksToNextLevel;
      merged.stepNow = src.stepNow;
      merged.stepTotal = src.stepTotal;
    };

    try {
      for (const lv of ['01', '02']) {
        if (byLevel[lv].length === 0) continue;
        const res = await fetch('/clinic/quiz/submit', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ studentId, contentId: Number(contentId), qlevel: lv, mode: 'WRONG_ONLY', answers: byLevel[lv] }),
        });
        const data = await res.json();
        if (!data.success) throw new Error(data.error?.message ?? '채점에 실패했어요.');
        const r = data.response;
        part[lv] = { correct: r.correctCount ?? 0, total: r.totalCount ?? 0 };
        (r.wrongQnums ?? []).forEach((qn) => merged.wrongQnums.push(qn));
        if (lv === '01') carryBasic(r);
      }

      // 기본을 다시 풀지 않았으면(심화만 재도전) 기본 성적/등급은 직전 결과에서 채운다
      if (!part['01']) {
        const res = await fetch(`/clinic/last-result?studentId=${encodeURIComponent(studentId)}&contentId=${encodeURIComponent(contentId)}&qlevel=01`);
        const data = await res.json();
        if (data.success && data.response) {
          part['01'] = { correct: data.response.correctCount ?? 0, total: data.response.totalCount ?? 0 };
          carryBasic(data.response);
        }
      }

      merged.correctCount = (part['01'] ? part['01'].correct : 0) + (part['02'] ? part['02'].correct : 0);
      merged.totalCount = (part['01'] ? part['01'].total : 0) + (part['02'] ? part['02'].total : 0);
    } catch (err) {
      console.error(err);
      merged.wrongQnums = [];
    }

    sessionStorage.setItem('quizResult', JSON.stringify(merged));
    window.location.href = `/student/result?studentId=${encodeURIComponent(studentId)}&contentId=${encodeURIComponent(contentId)}&qlevel=01`;
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
