(function () {
  const page = document.getElementById('resultPage');
  const studentId = page ? page.getAttribute('data-student-id') : null;

  const heroCharacter = document.getElementById('heroCharacter');
  const heroCharacterAnim = document.getElementById('heroCharacterAnim');
  const heroFireworksAnim = document.getElementById('heroFireworksAnim');

  // 등급별 캐릭터 로티 종류 — 파일명은 /lottie/{basic|adv}_{category}_{캐릭터}.json
  const HERO_LOTTIE_PREFIX = {
    RETRY: 'basic',
    FRIEND: 'basic',
    KING: 'basic',
    ADVANCED: 'adv',
    ADVANCED_PERFECT: 'adv',
  };
  const HERO_LOTTIE_CATEGORY = {
    RETRY: 'fail',
    FRIEND: 'pass',
    KING: 'perfect',
    ADVANCED: 'pass',
    ADVANCED_PERFECT: 'perfect',
  };
  // 학년(schoolyear) 코드 → 캐릭터. 초1=frog, 초2=mouse, 초3~중3(03~07)은 parrot을 그대로 쓴다(2026-09-14).
  const HERO_LOTTIE_CHARACTER_BY_SCHOOLYEAR = { '01': 'frog', '02': 'mouse' };
  const HERO_LOTTIE_DEFAULT_CHARACTER = 'parrot';
  let heroAnim = null;
  let heroSchoolyear = null;

  function playHeroCharacter(grade) {
    if (!heroCharacterAnim || typeof lottie === 'undefined') return;
    const prefix = HERO_LOTTIE_PREFIX[grade] ?? 'basic';
    const category = HERO_LOTTIE_CATEGORY[grade] ?? 'pass';
    const character = HERO_LOTTIE_CHARACTER_BY_SCHOOLYEAR[heroSchoolyear] ?? HERO_LOTTIE_DEFAULT_CHARACTER;

    if (heroAnim) {
      heroAnim.destroy();
      heroAnim = null;
    }
    heroAnim = lottie.loadAnimation({
      container: heroCharacterAnim,
      renderer: 'svg',
      loop: true,
      autoplay: true,
      path: `/lottie/${prefix}_${category}_${character}.json`,
    });
    playHeroFireworks();
  }

  // 캐릭터 뒤 폭죽 로티(2026-09-28) — 모든 결과(완독 포함)에 띄운다
  let fireworksAnim = null;
  function playHeroFireworks() {
    if (fireworksAnim) {
      fireworksAnim.destroy();
      fireworksAnim = null;
    }
    if (!heroFireworksAnim) return;
    fireworksAnim = lottie.loadAnimation({
      container: heroFireworksAnim,
      renderer: 'svg',
      loop: true,
      autoplay: true,
      path: '/lottie/firework.json',
    });
  }

  // 데브툴 콘솔에서 등급/학년 조합을 바로 재생해보기 위한 헬퍼 — 예: previewHero('KING', '01')
  window.previewHero = function (grade, schoolyear) {
    heroSchoolyear = schoolyear ?? heroSchoolyear;
    playHeroCharacter(grade);
  };

  const scoreCorrectEl = document.getElementById('scoreCorrect');
  const scoreTotalEl = document.getElementById('scoreTotal');
  const scoreLabel = document.getElementById('scoreLabel');
  const scoreDots = document.getElementById('scoreDots');
  const resultTitle = document.getElementById('resultTitle');
  const resultNext = document.getElementById('resultNext');
  const resultNextLabel = document.getElementById('resultNextLabel');
  const rewardMedal = document.getElementById('rewardMedal');
  const rewardExpDesc = document.getElementById('rewardExpDesc');
  const newCard = document.getElementById('newCard');
  const newCardImg = document.getElementById('newCardImg');
  const cardReward = document.getElementById('cardReward');
  const rewardProgressBar = document.getElementById('rewardProgressBar');
  const badgeReward = document.getElementById('badgeReward');
  const rewardBadgeImg = document.getElementById('rewardBadgeImg');
  const rewardBadgeName = document.getElementById('rewardBadgeName');

  /* 보상 패널 "레벨 성장!" 메달을 학년 메달(medal_1~6.png)로 바꾼다 — student-main 레벨 카드와 같은 그림이다.
     메달에 학년 숫자가 박혀 있어 초1~초6(01~06)만 있고, 중등('07')/학년 미지정은 엉뚱한 숫자를 보여주느니
     템플릿에 박아둔 기존 금색 메달을 그대로 둔다. (2026-09-15) */
  function applyRewardMedal(schoolyear) {
    if (!rewardMedal) return;
    const n = Number(schoolyear);
    if (!Number.isInteger(n) || n < 1 || n > 6) return;
    rewardMedal.src = `/images/medal_sm/medal_${n}.png`;
  }
  const wrongRetryBtn = document.getElementById('wrongRetryBtn');
  const advancedBtn = document.getElementById('advancedBtn');
  // "다시 읽으러 가기"(불합격) / "여권 쓰러 가기"(심화 마무리) — 이름만 다르고 동작은 둘 다
  // 로그아웃이다(2026-09-03 확정). 학생이 앱에서 나가 실제로 책을 읽거나 종이 여권을 쓰러 가는
  // 행동이므로, 새 화면을 만들지 않고 기존 로그아웃(logout())을 그대로 재사용한다.
  const readAgainBtn = document.getElementById('readAgainBtn');
  const passportBtn = document.getElementById('passportBtn');

  const homeBtn = document.querySelector('.btn-home');
  const logoutBtn = document.querySelector('.logout-btn');
  const contentId = page ? page.getAttribute('data-content-id') : null;
  const qlevel = (page ? page.getAttribute('data-qlevel') : null) || '01';

  // 결과 화면의 "로그아웃" 버튼 — 예전엔 아예 연결돼있지 않아 눌러도 아무 반응이 없었다(2026-08-26).
  // student-main.js와 같은 방식으로 서버 세션을 먼저 무효화하고("문제 푸는 중"/"결과 확인중" 표시도
  // 함께 해제) 로그인 화면으로 이동한다.
  async function logout() {
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
  }

  if (logoutBtn) logoutBtn.addEventListener('click', logout);
  // 라벨만 다른 로그아웃 버튼들 — 어느 등급에서 보일지는 render*Result가 정한다
  if (readAgainBtn) readAgainBtn.addEventListener('click', logout);
  if (passportBtn) passportBtn.addEventListener('click', logout);

  // 이 책이 끝난 상태인지(=책은 이미 반납됨) — 독서왕/독서친구/심화완료면 true, 재도전(불합격)이면
  // false. true일 때 "홈으로"를 누르면 일반 홈이 아니라 완료 화면(mode=retryDone)으로 보낸다 —
  // 문제 풀기 버튼 자리에 남은 액션(틀린 문제 다시 풀기/심화 문제 풀기)과 "책 추천받기" 버튼을
  // 보여주기 위함이다(2026-08-25, 예전의 "다음 책 받을까요?" 팝업을 대체)
  let bookFinished = false;

  // "틀린 문제 다시 풀기"로 넘길 문항 번호 — 보통 이번 제출의 오답이지만, 심화왕일 때는
  // 기본(01) 쪽 오답을 넘긴다. 각 render*Result가 채운다(2026-09-02).
  let wrongRetryTargets = [];

  if (homeBtn) {
    homeBtn.addEventListener('click', (e) => {
      if (!bookFinished || !contentId) return; // 재도전(불합격) 등은 그냥 일반 홈으로 이동
      e.preventDefault();
      window.location.href = `/student/main?studentId=${encodeURIComponent(studentId)}&contentId=${encodeURIComponent(contentId)}&qlevel=${encodeURIComponent(qlevel)}&mode=retryDone`;
    });
  }

  // student-question.js가 채점 직후 세션 저장소에 담아둔 결과를 우선 쓴다. 없으면(재도전 중 제출 없이
  // "나가기"로 온 경우 등) 새로 채점하지 않고 서버에 남은 직전 결과를 가져온다(2026-08-25). 그마저
  // 없으면(새로고침/직접 접근 등 정말 보여줄 게 없는 경우) 메인으로 돌려보낸다.
  async function loadResult() {
    const raw = sessionStorage.getItem('quizResult');
    if (raw) {
      sessionStorage.removeItem('quizResult');
      return JSON.parse(raw);
    }
    const contentId = page ? page.getAttribute('data-content-id') : null;
    const qlevel = (page ? page.getAttribute('data-qlevel') : null) || '01';
    if (!contentId) return null;
    try {
      const res = await fetch(`/clinic/last-result?studentId=${encodeURIComponent(studentId)}&contentId=${encodeURIComponent(contentId)}&qlevel=${encodeURIComponent(qlevel)}`);
      const data = await res.json();
      return data.success ? { advanced: false, ...data.response } : null;
    } catch (err) {
      console.error(err);
      return null;
    }
  }

  (async function init() {
    const result = await loadResult();
    if (!result) {
      window.location.replace(`/student/main?studentId=${encodeURIComponent(studentId)}`);
      return;
    }

    heroSchoolyear = result.schoolyear ?? null;
    applyRewardMedal(heroSchoolyear);
    renderScore(result);

    if (result.advanced) {
      renderAdvancedResult(result);
    } else if (result.grade === 'KING') {
      renderKingResult(result);
    } else if (result.grade === 'FRIEND') {
      renderFriendResult(result);
    } else {
      renderRetryResult(result);
    }
    renderExp(result);
    renderNewCard(result);
    renderCard(result);
    renderBadge(result);
    // 획득 연출 순서: 캐릭터(가운데서 크게 → 탭 → 제자리) → 카드(탭 → 제자리) → 경험치(차오른 뒤 제자리)
    // → 뱃지(가운데서 제자리). 해당 없는 단계는 건너뛴다. 연출이 예정된 보상 칸은 처음엔 비워 뒀다가
    // 각자 제자리에 붙는 순간 채운다 — 중간에 연출이 빠지거나 오류가 나도 마지막에 전부 채운다.
    markPendingSlots(result);
    playHeroIntro(result)
      .then(() => playCardReveals(result))
      .then(() => playExpReveal(result))
      .then(() => revealSlot(expCell))
      .then(() => flyInBadge(result))
      .then(() => revealSlot(badgeReward))
      .catch((err) => console.error(err))
      .finally(revealAllSlots);

    // 기본+심화 오답을 한 번에 푸는 병합 모드로 온 결과 — 2026-09-03 버튼 규칙 재정리로 단계가
    // 기본/심화 중 하나로 배타적이 되면서 이 모드를 만드는 화면이 사라졌다(완료 화면이 이제
    // 단계에 맞는 한쪽 오답만 넘긴다). 도달할 일이 없지만, 예전 세션 저장소가 남아 있는 경우를
    // 대비해 방어적으로 남겨둔다 — 확정된 점수라 추가 액션 없이 "홈으로"만 보여준다.
    if (result.mergedWrong) {
      resetActionButtons();
      setGuide('틀린 문제를 다시 풀었어요!', null);
    }

    // "틀린 문제 풀기" — 다시 풀 문항 번호만 세션 저장소에 담아두면 student-question.js가
    // 문제 목록을 불러온 뒤 그 번호만 걸러서 다시 낸다
    wrongRetryBtn.addEventListener('click', () => {
      sessionStorage.setItem('retryQnums', JSON.stringify(wrongRetryTargets));
    });
  })();

  function renderScore(result) {
    const correct = result.correctCount ?? 0;
    const total = result.totalCount ?? 0;
    scoreCorrectEl.textContent = correct;
    scoreTotalEl.textContent = total;
    scoreLabel.textContent = result.advanced ? '문해력문제 풀이 결과' : '정독문제 풀이 결과';

    // 문항 번호 동그라미(2026-09-28 시안) — 1~total을 그리고 wrongQnums("01" 같은 qnum)에 든 번호만 빨강.
    // 병합 모드(기본+심화)는 번호가 겹쳐 위치를 특정할 수 없지만 도달하지 않는 경로라 그대로 둔다.
    const wrongSet = new Set((result.wrongQnums ?? []).map(Number));
    scoreDots.replaceChildren();
    for (let i = 1; i <= total; i++) {
      const dot = document.createElement('li');
      dot.textContent = i;
      if (wrongSet.has(i)) dot.classList.add('is-wrong');
      scoreDots.appendChild(dot);
    }
  }

  /**
   * 점수 카드 아래 안내 문구 — 첫 줄은 결과 설명, 둘째 줄은 하단에 뜬 버튼 이름을 빨갛게 강조해
   * "“버튼”를 진행해 주세요."로 안내한다(2026-09-28 시안). 버튼이 없으면 둘째 줄을 숨긴다.
   */
  function setGuide(message, nextBtn) {
    resultTitle.textContent = message;
    const label = nextBtn ? nextBtn.textContent.trim() : '';
    resultNext.hidden = !label;
    resultNextLabel.textContent = label ? `“${label}”` : '';
  }

  /* ── 결과 화면 버튼 규칙 (2026-09-21 재정리) ─────────────────────────────────
     학생이 한 책을 두고 지나가는 단계는 아래 다섯뿐이고, 각 단계의 버튼은 딱 하나다.
     홈의 완료 화면(student-main.js renderCompletion)도 똑같은 규칙을 쓴다 — 두 화면이 다르게
     보이면 "홈으로"를 눌렀을 때 갑자기 다른 버튼이 나타나 흐름이 끊긴다.

       1. 완독(기본 합격선 미달)       → 다시 읽으러 가기            ※ 로그아웃 = 재도전 경로
       2. 정독 완료(합격선~만점 미만)  → 틀린 문제 다시 풀기
          2-2. 틀린 문제를 다 맞히면   → 심화 문제 풀기
       3. 정독 왕(기본 만점)           → 심화 문제 풀기
       4. 심화완료(심화 만점 아님)     → 틀린 문제 다시 풀기          ※ 0/6이어도 동일
          4-2. 틀린 문제를 다 맞히면   → 여권 쓰러 가기
       5. 심화왕(심화 만점)            → 여권 쓰러 가기              ※ 로그아웃

     재도전(전체 다시 풀기)은 1번 단계에만 있다 — 책을 다시 읽고 QR로 들어오면 홈에 "정독 문제
     풀기"가 뜨는 그 경로다. 2·4번에는 재도전이 없고, 심화에는 재도전 개념 자체가 없다.
     "틀린 문제 다시 풀기" 1회차로 만점을 치면 등급이 정독왕·심화왕으로 올라가므로 다음 화면부터는
     자연히 3번·5번 규칙으로 그려진다 — 처음부터 만점이었던 것과 같은 상태가 된다.
     심화왕은 기본 오답이 남아 있어도 더 붙잡지 않는다(여권으로 끝낸다). */

  /** 버튼을 전부 끈다 — 각 단계는 이 위에 필요한 것만 켠다 */
  function resetActionButtons() {
    wrongRetryBtn.hidden = true;
    advancedBtn.hidden = true;
    readAgainBtn.hidden = true;
    passportBtn.hidden = true;
  }

  /** 재도전·틀린 문제 버튼이 열 문제풀이 주소 — 단계에 따라 기본(01)/심화(02)가 갈린다 */
  function questionHref(level) {
    return `/student/question?studentId=${encodeURIComponent(studentId)}&contentId=${encodeURIComponent(contentId)}&qlevel=${level}`;
  }

  /**
   * 2·4단계 공통 — 남은 오답이 있으면 "틀린 문제 다시 풀기", 다 맞혔으면 다음 단계 버튼.
   * 재도전(전체 다시 풀기)은 이 단계에 없다(2026-09-21).
   * @param level   이 단계에서 다시 풀 문제의 난이도 ('01' 기본 / '02' 심화)
   * @param wrong   아직 틀린 채로 남은 문항 번호
   * @param nextBtn 오답을 다 맞혔을 때 그 자리에 들어올 버튼(심화 문제 풀기 / 여권 쓰러 가기)
   */
  function renderWrongRetryStage(level, wrong, nextBtn) {
    if (wrong.length > 0) {
      wrongRetryBtn.hidden = false;
      wrongRetryBtn.setAttribute('href', questionHref(level));
      wrongRetryTargets = wrong;
      setGuide(`틀린 문제가 ${wrong.length}개 있어요!`, wrongRetryBtn);
      return;
    }
    nextBtn.hidden = false;
    setGuide('모든 문제를 정확하게 풀었어요!', nextBtn);
  }

  // 4·5단계 — 심화(qlevel=02) 결과
  function renderAdvancedResult(result) {
    const total = result.totalCount ?? 0;
    const correct = result.correctCount ?? 0;
    const perfect = total > 0 && correct >= total;   // 만점 = 심화왕

    setHero(perfect ? 'ADVANCED_PERFECT' : 'ADVANCED');
    bookFinished = true;

    resetActionButtons();
    if (perfect) {
      passportBtn.hidden = false;                                  // 5단계
      setGuide('한 권을 완벽하게 끝냈어요!', passportBtn);
      return;
    }
    renderWrongRetryStage('02', result.wrongQnums ?? [], passportBtn);   // 4단계
  }

  // 3단계 — 독서왕(기본 만점). 더 맞힐 기본 문제가 없으니 심화만 남는다.
  function renderKingResult(result) {
    setHero('KING');
    // "홈으로"를 누르면 완료 화면(같은 버튼 규칙)으로 간다 — 다시풀기(alreadyCompleted)
    // 재제출이어도 마찬가지다(2026-08-25, 예전엔 이때만 예외로 그냥 홈으로 보냈다)
    bookFinished = true;

    resetActionButtons();
    advancedBtn.hidden = false;
    setGuide('모든 문제를 정확하게 풀었어요!', advancedBtn);
  }

  // 2단계 — 독서완료(합격선 이상 만점 미만)
  function renderFriendResult(result) {
    setHero('FRIEND');
    bookFinished = true;

    resetActionButtons();
    // 오답이 남았으면 "틀린 문제 다시 풀기", 다 맞혔으면 그 자리에 "심화 문제 풀기"
    renderWrongRetryStage('01', result.wrongQnums ?? [], advancedBtn);
  }

  // 1단계 — 완독(합격선 미달). 문제를 다시 푸는 게 아니라 책을 다시 읽으러 간다(= 재도전 경로).
  // "다시 읽으러 가기"는 로그아웃이다 — 읽고 와서 QR로 다시 들어오면 홈에 "문제 풀러 가기"가 뜬다.
  function renderRetryResult(result) {
    setHero('RETRY');
    bookFinished = false;

    resetActionButtons();
    readAgainBtn.hidden = false;
    setGuide('책을 다시 읽고 한 번 더 도전해 보세요.', readAgainBtn);
  }

  // 등급별 캐릭터 로티를 재생한다 — 달성 문구는 로티 그림 안에 포함돼 있어 따로 표시하지 않는다(2026-09-17)
  function setHero(grade) {
    heroCharacter.dataset.grade = grade;
    playHeroCharacter(grade);
  }

  // 보상 패널의 "경험치 획득!" 칸 — EXP 폐지, 완독 권수로 레벨업. 독서탐험 칸은 2026-09-28 시안에서 빠졌다.
  function renderExp(result) {
    if (result.levelNo != null) {
      rewardMedal.alt = `Lv. ${result.levelNo}`;
    }
    if (result.progressPercent != null) {
      rewardProgressBar.style.width = `${result.progressPercent}%`;
    }

    // 레벨 문구는 등급(독서왕/독서친구/불합격)으로 가르지 않는다(2026-09-02) — 첫 제출은 결과와
    // 무관하게 완독 1권으로 카운트돼 레벨이 오르므로, "완독하지 못했다"처럼 안 오른 듯한 문구를 쓰지 않는다.
    if (result.advanced) {
      rewardExpDesc.textContent = '심화문제는 레벨과 무관해요.';
    } else if (result.alreadyCompleted) {
      rewardExpDesc.textContent = '이미 완독한 책이에요.';
    } else if (result.leveledUp) {
      rewardExpDesc.textContent = `레벨업했어요! 🎉 Lv. ${result.levelNo} 달성`;
    } else if (result.booksToNextLevel != null) {
      rewardExpDesc.textContent = `다음 레벨까지 ${result.booksToNextLevel}권 남았어요!`;
    } else {
      rewardExpDesc.textContent = '이 책을 다 읽었어요! 🎉';
    }
  }

  // 독서여권 도장 칸 — 이번에 받은 뱃지를 이미지로 보여준다(2026-09-01). 독서완료(합격·불합격 공통)/
  // 독서왕 둘 다 뱃지가 있으므로 불합격이어도 칸이 뜬다. 재도전·틀린문제 재제출처럼 "새로 받은" 뱃지가
  // 없을 때는 서버가 내려준 bookBadge(그 책에서 보유 중인 뱃지)로 대신 채운다.
  // 뱃지 이미지는 badgeId로 찾는다(/images/icons/badge_<id>.png). 아직 이미지가 없는 뱃지는
  // 여권 아이콘으로 대체해 깨진 이미지가 뜨지 않게 한다.
  function renderBadge(result) {
    const badge = (result.newBadges ?? [])[0] ?? result.bookBadge;
    if (!badge) {
      badgeReward.hidden = true;
      return;
    }
    badgeReward.hidden = false;
    rewardBadgeImg.onerror = () => {
      rewardBadgeImg.onerror = null;
      rewardBadgeImg.src = '/images/student_result/passport.png';
    };
    rewardBadgeImg.src = `/images/icons/badge_${badge.badgeId}.png`;
    rewardBadgeImg.alt = badge.badgeName ?? '획득한 뱃지';
    rewardBadgeName.textContent = badge.badgeName ?? '뱃지';
  }

  /**
   * "신규 카드를 획득했어요!" 칸 (2026-09-02) — 이번 제출로 카드를 새로 받았을 때만 보여준다.
   * 카드는 그 책 첫 제출에서만 지급되므로(ClinicService.submitQuiz), 재도전·틀린문제 재제출에는
   * cardName이 비어 있고 이 칸도 뜨지 않는다. 예전엔 마크업이 항상 떠 있어 카드를 받지 않은
   * 재제출에도 "신규 카드 획득" 문구가 보였다.
   *
   * 이미지는 서버가 내려준 cardImageUrl(erp_bookstore_card_path.card_url)이다. 카드 그림이 아직
   * 등록되지 않은 책은 쿼리에서 이미 기본 카드로 폴백돼 오지만, 호스팅 주소가 깨진 경우까지
   * 대비해 onerror로 한 번 더 기본 카드로 되돌린다.
   */
  function renderNewCard(result) {
    if (!result.cardName) {
      newCard.hidden = true;
      return;
    }
    newCard.hidden = false;
    if (result.cardImageUrl) {
      newCardImg.onerror = () => {
        newCardImg.onerror = null;
        newCardImg.src = '/images/student_result/card.png';
      };
      newCardImg.src = result.cardImageUrl;
    }
    newCardImg.alt = result.cardName;
  }

  // 스페셜 카드 그림 — 시안의 "비밀의 책갈피" 이미지. 아직 파일이 없으면 기존 treasure.png로 되돌린다.
  const SPECIAL_CARD_SRC = '/images/student_result/special_card.png';
  const SPECIAL_CARD_FALLBACK = '/images/student_result/treasure.png';
  const specialCardImg = document.getElementById('specialCardImg');

  // 스페셜 카드 = 이번 첫 제출로 도서 카드가 10장의 배수를 채운 순간(서버 cardRewardReached, CARD_SET_SIZE=10).
  // 재도전·틀린문제 재제출에는 카드를 새로 주지 않으므로 항상 false로 온다.
  function hasSpecialCard(result) {
    return Boolean(result.cardRewardReached);
  }

  function setImgWithFallback(img, src, fallback) {
    img.onerror = () => {
      img.onerror = null;
      img.src = fallback;
    };
    img.src = src;
  }

  // "숨겨진 보물 발견!" 칸(2x2 마지막) — 완독 카드 10장을 채운 순간(스페셜 카드 지급)에만 노출한다(2026-09-01).
  // 평범한 완독(1~9장째)은 띄우지 않는다 — 10장 달성이 곧 실물 교환 시점이다.
  function renderCard(result) {
    const show = hasSpecialCard(result);
    cardReward.hidden = !show;
    if (show) setImgWithFallback(specialCardImg, SPECIAL_CARD_SRC, SPECIAL_CARD_FALLBACK);
    renderTreasureProgress(result, show);
  }

  // 스페셜 카드를 못 받았을 때 그 자리에 "보물 발견까지 N칸!" — 도서 카드 10장(서버 CARD_SET_SIZE)
  // 세트까지 남은 장수. 직전 결과(/clinic/last-result)처럼 totalCards가 안 오면 셀 수 없어 숨긴다.
  const CARD_SET_SIZE = 10;
  const treasureProgress = document.getElementById('treasureProgress');
  const mysteryCardImg = document.getElementById('mysteryCardImg');

  function renderTreasureProgress(result, specialShown) {
    if (specialShown || result.totalCards == null) {
      treasureProgress.hidden = true;
      return;
    }
    const left = CARD_SET_SIZE - (result.totalCards % CARD_SET_SIZE);
    treasureProgress.querySelectorAll('.js-treasure-left').forEach((el) => {
      el.textContent = left;
    });
    // 디자인 원본(png)이 들어오면 그걸 쓰고, 없으면 임시 SVG로 되돌린다
    setImgWithFallback(mysteryCardImg, '/images/student_result/mystery_card.png', '/images/student_result/mystery_card.svg');
    treasureProgress.hidden = false;
  }

  /* ── 카드 획득 연출(2026-09-28) ─────────────────────────────────────────
     도서 카드(이번에 새로 받은 경우) → 스페셜 카드(10장 달성) 순서로 한 장씩 띄운다.
     한 장은 학생이 화면을 누를 때까지 떠 있고(자동으로 닫히지 않음), 다음 장은 CSS 애니메이션을
     처음부터 다시 튼다. */
  const cardReveal = document.getElementById('cardReveal');
  const cardRevealTitle = document.getElementById('cardRevealTitle');
  const cardRevealDesc = document.getElementById('cardRevealDesc');
  const cardRevealFront = document.getElementById('cardRevealFront');
  const cardRevealBack = document.getElementById('cardRevealBack');
  const REVEAL_LEAVE_MS = 400;   // .card-reveal.is-leaving 페이드아웃 길이와 맞춘다

  const wait = (ms) => new Promise((r) => setTimeout(r, ms));

  // 연출은 화면을 눌러야 닫힌다(다음 장이 있으면 이어서 나온다)
  let closeCurrentReveal = null;
  cardReveal.addEventListener('click', () => {
    if (closeCurrentReveal) closeCurrentReveal();
  });

  const cardRevealStage = cardReveal.querySelector('.card-reveal-stage');
  const cardRevealCard = document.getElementById('cardRevealCard');

  /** object-fit: contain 으로 (boxW x boxH) 칸에 (natW x natH) 그림을 담았을 때 실제로 그려지는 높이 */
  function containedHeight(boxW, boxH, natW, natH) {
    if (!natW || !natH) return boxH;
    return Math.min(boxH, (boxW * natH) / natW);
  }

  /**
   * 탭으로 닫을 때 카드가 막 위 자리(가운데 큰 카드)에서 보상 칸의 카드 자리로 날아가 붙는다.
   * 막 위 카드와 칸의 카드는 같은 그림이라, 두 칸에서 실제로 그려지는 높이의 비로 배율을 잡는다.
   */
  async function flyCardToSlot(target) {
    if (!target || !cardRevealStage || reducedMotion()) return;
    const fly = createFlyClone(target);
    if (!fly) return;
    const stageRect = cardRevealStage.getBoundingClientRect();
    const natW = target.naturalWidth;
    const natH = target.naturalHeight;
    const bigH = containedHeight(stageRect.width, stageRect.height, natW, natH);
    const slotH = containedHeight(fly.rect.width, fly.rect.height, natW, natH);
    setFlyStart(
      fly,
      stageRect.left + stageRect.width / 2 - (fly.rect.left + fly.rect.width / 2),
      stageRect.top + stageRect.height / 2 - (fly.rect.top + fly.rect.height / 2),
      bigH / slotH,
    );
    await runFly([fly], 'fly-return', 1500);
  }

  async function showCardReveal({ title, desc, src, fallback, target, cell }) {
    cardRevealTitle.textContent = title;
    cardRevealDesc.textContent = desc;
    setImgWithFallback(cardRevealFront, src, fallback);
    setImgWithFallback(cardRevealBack, src, fallback);
    cardRevealFront.alt = title;

    // hidden을 풀고 reflow를 한 번 일으켜야 두 번째 장에서도 애니메이션이 처음부터 다시 돈다
    cardReveal.classList.remove('is-leaving');
    cardReveal.hidden = true;
    void cardReveal.offsetWidth;
    cardReveal.hidden = false;

    await new Promise((resolve) => {
      closeCurrentReveal = resolve;
    });
    closeCurrentReveal = null;

    // 탭 → 막은 걷히고, 막 위 카드는 바로 감춘 뒤 같은 그림의 복제본이 그 자리에서 칸으로 날아간다.
    // 날아가는 동안의 탭은 투명 막이 받아 아래 버튼으로 새지 않게 한다.
    const catcher = openTapCatcher();
    cardRevealCard.style.visibility = 'hidden';
    cardReveal.classList.add('is-leaving');
    setTimeout(() => {
      cardReveal.hidden = true;
      cardRevealCard.style.visibility = '';
    }, REVEAL_LEAVE_MS);
    await flyCardToSlot(target);
    revealSlot(cell);
    catcher.remove();
    await wait(Math.max(0, REVEAL_LEAVE_MS - 300));   // 막이 다 걷힌 뒤 다음 장
  }

  // 연출이 도는 중에 보상 칸을 또 누르면 겹쳐 재생되지 않게 막는다
  let revealing = false;

  async function playQueue(queue) {
    if (revealing || queue.length === 0) return;
    revealing = true;
    try {
      for (const item of queue) {
        await showCardReveal(item);
      }
    } finally {
      revealing = false;
    }
  }

  async function playCardReveals(result) {
    const bookCardItem = result.cardName
      ? {
          title: '도서 카드 획득!',
          desc: '컬렉션에 추가됐어요!',
          src: result.cardImageUrl || '/images/student_result/card.png',
          fallback: '/images/student_result/card.png',
          target: newCardImg,
          cell: newCard,
        }
      : null;
    const specialItem = hasSpecialCard(result)
      ? {
          title: '숨겨진 보물 발견!',
          desc: '선생님께 카드를 받아요!',
          src: SPECIAL_CARD_SRC,
          fallback: SPECIAL_CARD_FALLBACK,
          target: specialCardImg,
          cell: cardReward,
        }
      : null;

    // 보상 2x2의 카드 칸을 누르면 그 카드 연출만 다시 보여준다 — 재제출이라 이미 가진 카드도 다시 볼 수 있다
    bindReplay(newCard, bookCardItem);
    bindReplay(cardReward, specialItem);

    // 자동 연출은 이번에 새로 받은 카드만 — 재도전·틀린문제 재제출도 서버가 보유 카드(cardName)를
    // 내려주지만 cardNew가 false라 연출 없이 칸에만 보인다
    await playQueue([bookCardIsNew(result) ? bookCardItem : null, specialItem].filter(Boolean));
  }

  function bookCardIsNew(result) {
    return Boolean(result.cardName) && result.cardNew;
  }

  /* ── 빈 칸 → 착지 시 채우기(2026-09-28) ─────────────────────────────────
     연출이 예정된 보상 칸은 흰 칸만 남기고 내용을 비워 둔다(fx-slot-pending). 각 연출이 제자리에
     붙는 순간 revealSlot으로 채우며 칸이 살짝 튕긴다. 자리(레이아웃)는 그대로라 복제본 좌표 계산에
     영향이 없다. */
  const expCell = rewardMedal ? rewardMedal.closest('.reward-item') : null;

  function markPendingSlots(result) {
    if (reducedMotion()) return;
    const pending = [];
    if (bookCardIsNew(result)) pending.push(newCard);
    if (hasSpecialCard(result)) pending.push(cardReward);
    if (shouldPlayExp(result)) pending.push(expCell);
    if (badgeIsNew(result) && !badgeReward.hidden) pending.push(badgeReward);
    pending.filter(Boolean).forEach((cell) => cell.classList.add('fx-slot-pending'));
  }

  function revealSlot(cell) {
    if (!cell || !cell.classList.contains('fx-slot-pending')) return;
    cell.classList.remove('fx-slot-pending');
    cell.classList.add('fx-slot-landed');
    cell.addEventListener('animationend', () => cell.classList.remove('fx-slot-landed'), { once: true });
  }

  function revealAllSlots() {
    document.querySelectorAll('.fx-slot-pending').forEach(revealSlot);
  }

  function bindReplay(cell, item) {
    if (!cell || !item) return;
    cell.classList.add('is-replayable');
    cell.setAttribute('role', 'button');
    cell.setAttribute('tabindex', '0');
    cell.addEventListener('click', () => playQueue([item]));
    cell.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        playQueue([item]);
      }
    });
  }

  /* ── 제자리 안착 공용(2026-09-28) ──────────────────────────────────────
     보상 칸의 원본 요소는 건드리지 않고 복제본(clone)을 원본 자리·크기에 겹쳐 body에 붙인 뒤,
     translate/scale을 CSS 변수로 넘겨 "가운데(크게) → 제자리(원래 크기)"로 풀리게 한다.
     - body에 붙이는 이유: .page는 transform(scale)이 걸려 있어 그 안의 position:fixed는 화면이
       아니라 .page 기준이 된다.
     - 좌표는 원본의 getBoundingClientRect()로 매번 잰다 — 화면 크기/배율이 달라도 그대로 맞는다.
     - 원본은 visibility:hidden으로 자리만 남겨 두고, 끝나면 복제본을 지우고 되살린다. */
  function createFlyClone(target) {
    const rect = target.getBoundingClientRect();
    if (!rect.width || !rect.height) return null;
    const clone = target.cloneNode(true);
    clone.removeAttribute('id');
    clone.querySelectorAll('[id]').forEach((el) => el.removeAttribute('id'));
    clone.setAttribute('aria-hidden', 'true');
    if (clone.tagName === 'IMG') {
      clone.alt = '';
      clone.src = target.currentSrc || target.src;
    }
    clone.classList.add('fly-clone');
    clone.style.left = `${rect.left}px`;
    clone.style.top = `${rect.top}px`;
    clone.style.width = `${rect.width}px`;
    clone.style.height = `${rect.height}px`;
    return { clone, target, rect };
  }

  /** 복제본을 (dx,dy)만큼 떨어진 곳에서 scale 배로 시작하게 변수만 심는다 — 움직임은 CSS 키프레임이 한다 */
  function setFlyStart(item, dx, dy, scale) {
    item.clone.style.setProperty('--fly-dx', `${dx}px`);
    item.clone.style.setProperty('--fly-dy', `${dy}px`);
    item.clone.style.setProperty('--fly-scale', scale);
  }

  /**
   * 복제본들을 붙이고 animationClass 키프레임을 한 번 돌린다. 끝나면(또는 안전 타이머) 정리하고 resolve.
   * 원본은 이 동안 숨긴다.
   */
  function runFly(items, animationClass, maxMs) {
    return new Promise((resolve) => {
      // 끝 처리는 한 번만 — animationend와 안전 타이머 중 먼저 온 쪽이 정리한다
      const run = { done: false, timer: null, finish: null };
      run.finish = () => {
        if (run.done) return;
        run.done = true;
        clearTimeout(run.timer);
        items.forEach(({ clone, target }) => {
          clone.remove();
          target.classList.remove('fly-origin-hidden');
        });
        resolve();
      };
      items.forEach(({ clone, target }) => {
        target.classList.add('fly-origin-hidden');
        clone.classList.remove('fly-appear', 'fly-hold', 'fly-return');
        clone.classList.add(animationClass);
        if (!clone.isConnected) document.body.appendChild(clone);
      });
      // 복제본 여러 개가 같은 길이로 움직이므로 첫 번째의 animationend 하나로 끝을 잡는다
      items[0].clone.addEventListener('animationend', run.finish, { once: true });
      // animationend가 안 오는 경우(탭 전환 등)에도 복제본이 남지 않게 한 번 더 정리한다
      run.timer = setTimeout(run.finish, maxMs);
    });
  }

  function reducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  /**
   * 투명 탭 막 — 캐릭터/경험치/뱃지 연출 중 화면 어디를 눌러도 탭으로 받는다. 막이 없으면 탭이 아래
   * "문해력 문제 풀기" 같은 버튼으로 새어 나간다. 연출이 제자리에 다 붙을 때까지 깔아 둔다.
   * @returns {{ tapped: Promise<void>, remove: () => void }}
   */
  function openTapCatcher() {
    const el = document.createElement('div');
    el.className = 'fx-tap-catcher';
    el.setAttribute('aria-hidden', 'true');
    document.body.appendChild(el);
    let resolveTap;
    const tapped = new Promise((resolve) => { resolveTap = resolve; });
    el.addEventListener('click', () => resolveTap(), { once: true });
    return { tapped, remove: () => el.remove() };
  }

  /** ms만큼 기다리되 그 전에 탭이 오면 바로 끝낸다 — 탭으로 끝났으면 true */
  function waitOrTap(ms, tapped) {
    return Promise.race([wait(ms).then(() => false), tapped.then(() => true)]);
  }

  /** 복제본들을 가운데로 크게 띄운다(fly-appear, 끝난 뒤에도 그 상태로 머문다). 탭이 오면 바로 끝낸다 */
  function flyAppear(items, tapped) {
    const appeared = new Promise((resolve) => {
      items[0].clone.addEventListener('animationend', resolve, { once: true });
      setTimeout(resolve, 800);
    });
    items.forEach(({ clone, target }) => {
      target.classList.add('fly-origin-hidden');
      clone.classList.add('fly-appear');
      document.body.appendChild(clone);
    });
    return Promise.race([appeared.then(() => false), tapped.then(() => true)]);
  }

  /* ── 캐릭터 인트로(2026-09-28) ───────────────────────────────────────────
     결과 화면에 들어오자마자 캐릭터 칸(폭죽+캐릭터 로티)이 화면 가운데에 크게 나와 머물고, 탭하면
     제자리로 붙는다. 로티는 복제하면 멈춘 그림이 되므로 복제본 없이 실제 칸에 transform을 건다 —
     칸은 .page 안에 있어 translate가 .page의 확대 배율을 한 번 더 타므로, 화면(px) 거리를 배율로 나눠서 넘긴다. */
  async function playHeroIntro(result) {
    if (!heroCharacter || !page || reducedMotion()) return;
    // 이 책·난이도의 첫 제출에만(서버 firstAttempt) — 재도전·틀린문제 재제출·새로고침에는 안 나온다
    if (result.firstAttempt !== true) return;

    const pageRect = page.getBoundingClientRect();
    const heroRect = heroCharacter.getBoundingClientRect();
    if (!heroRect.width || !page.offsetWidth) return;
    const scaleX = pageRect.width / page.offsetWidth;
    const scaleY = pageRect.height / page.offsetHeight;

    const dx = (pageRect.left + pageRect.width / 2 - (heroRect.left + heroRect.width / 2)) / scaleX;
    const dy = (pageRect.top + pageRect.height / 2 - (heroRect.top + heroRect.height / 2)) / scaleY;
    // 가운데에서 .page 높이의 85%까지 키우되 너무 커지지 않게 1.8배로 막는다
    const big = Math.min(1.8, (page.offsetHeight * 0.85) / (heroRect.height / scaleY));

    heroCharacter.style.setProperty('--fly-dx', `${dx}px`);
    heroCharacter.style.setProperty('--fly-dy', `${dy}px`);
    heroCharacter.style.setProperty('--fly-scale', big);

    // 1) 가운데에 크게 등장 — 애니메이션이 끝나도(both) 그 자리에 머물고, 탭을 기다린다
    const catcher = openTapCatcher();
    heroCharacter.classList.add('hero-intro-appear');
    await catcher.tapped;

    // 2) 탭하면 제자리로
    await new Promise((resolve) => {
      let done = false;
      const finish = () => {
        if (done) return;
        done = true;
        heroCharacter.classList.remove('hero-intro-return');
        resolve();
      };
      heroCharacter.addEventListener('animationend', finish, { once: true });
      setTimeout(finish, 1500);   // animationend가 안 와도 다음 연출로 넘어간다
      heroCharacter.classList.replace('hero-intro-appear', 'hero-intro-return');
    });
    catcher.remove();
  }

  /* ── 경험치 연출(2026-09-28) ─────────────────────────────────────────────
     뱃지와 같은 방식 — 보상 칸의 메달·프로그레스바 복제본이 화면 가운데로 크게 떠오르고, 바가
     이 책을 읽기 전 진행률에서 지금 진행률까지 차오른 뒤(레벨업이면 끝까지 찼다가 새 레벨에서 다시),
     원래 칸으로 날아가 "착" 붙는다. 도중에 탭하면 남은 단계를 건너뛰고 바로 제자리로 간다.

     이 책을 읽기 전 진행률은 서버가 따로 주지 않아 화면에서 역산한다 —
     레벨당 권수 B = 남은 권수 / (1 - 진행률), 한 권 = 100/B %. (서버: inLevel = done % B,
     progressPercent = round(inLevel*100/B), booksToNextLevel = B - inLevel) */
  const rewardProgress = rewardProgressBar ? rewardProgressBar.parentElement : null;
  const EXP_FILL_MS = 900;   // .exp-fill-anim transition 길이와 맞춘다

  /** { from, to, leveledUp } — 이번 한 권으로 바가 어디서 어디까지 차는지. 계산할 수 없으면 null */
  function expGainRange(result) {
    const to = result.progressPercent;
    const left = result.booksToNextLevel;
    if (to == null || left == null || left <= 0 || to >= 100) return null;   // 만렙 등
    const perLevel = Math.round(left / (1 - to / 100));
    if (!Number.isFinite(perLevel) || perLevel <= 0) return null;
    const step = 100 / perLevel;
    if (to <= 0) {
      // 이번 한 권으로 레벨업해 새 레벨 0%에서 시작 — 직전엔 한 권 모자란 상태였다
      return { from: 100 - step, to: 0, leveledUp: true };
    }
    return { from: Math.max(0, to - step), to, leveledUp: false };
  }

  // 이번 제출로 완독 1권이 오른 경우(서버 levelGained = 기본 첫 제출)에만. 심화·재도전·틀린문제 재제출,
  // 새로고침으로 직전 결과를 다시 불러온 경우엔 값이 없거나 false라 연출하지 않는다.
  function shouldPlayExp(result) {
    return !result.advanced && !result.mergedWrong && result.levelGained === true;
  }

  async function playExpReveal(result) {
    if (!rewardProgress || !shouldPlayExp(result) || reducedMotion()) return;
    const range = expGainRange(result);
    if (!range) return;
    try {
      await rewardMedal.decode();
    } catch (err) {
      // 폴백 중이어도 연출은 진행한다
    }

    const medal = createFlyClone(rewardMedal);
    const bar = createFlyClone(rewardProgress);
    if (!medal || !bar) return;
    const barFill = bar.clone.firstElementChild;

    // 가운데 배치 — 메달은 화면 짧은 변의 36% 높이, 바는 메달과 같은 배율로 그 바로 아래에 둔다
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const scale = (Math.min(vw, vh) * 0.36) / medal.rect.height;
    const medalCy = vh * 0.46;
    const barCy = medalCy + (medal.rect.height * scale) / 2 + (bar.rect.height * scale) / 2 + 18 * (vh / 800);
    setFlyStart(medal, vw / 2 - (medal.rect.left + medal.rect.width / 2), medalCy - (medal.rect.top + medal.rect.height / 2), scale);
    setFlyStart(bar, vw / 2 - (bar.rect.left + bar.rect.width / 2), barCy - (bar.rect.top + bar.rect.height / 2), scale);

    // 바는 이 책 읽기 전 진행률부터 시작 — 폭을 먼저 맞춘 뒤에 차오름(transition)을 켠다.
    // 순서가 바뀌면 복제 직후의 최종 폭에서 시작 폭으로 거꾸로 줄어드는 게 보인다.
    barFill.style.width = `${range.from}%`;
    void barFill.offsetWidth;
    barFill.classList.add('exp-fill-anim');

    const catcher = openTapCatcher();
    // 각 단계는 탭이 오면 true를 돌려주고, 그때부터 남은 단계를 모두 건너뛴다
    const steps = async () => {
      // 1) 가운데로 떠오르기
      if (await flyAppear([medal, bar], catcher.tapped)) return;
      [medal, bar].forEach(({ clone }) => clone.classList.replace('fly-appear', 'fly-hold'));
      if (await waitOrTap(150, catcher.tapped)) return;

      // 2) 차오르기 — 레벨업이면 끝까지 채웠다가 0%로 되돌려 새 레벨 진행률까지 다시
      if (range.leveledUp) {
        barFill.style.width = '100%';
        if (await waitOrTap(EXP_FILL_MS, catcher.tapped)) return;
        barFill.classList.remove('exp-fill-anim');
        barFill.style.width = '0%';
        void barFill.offsetWidth;
        barFill.classList.add('exp-fill-anim');
      }
      barFill.style.width = `${range.to}%`;
      await waitOrTap(EXP_FILL_MS + 250, catcher.tapped);   // 다 찬 모습을 잠깐 보여준다
    };
    await steps();

    // 3) 제자리로 — 탭으로 건너뛰었어도 바는 최종 진행률로 맞춰 원본과 같아지게 한다
    barFill.classList.remove('exp-fill-anim');
    barFill.style.width = `${range.to}%`;
    await runFly([medal, bar], 'fly-return', 1500);
    catcher.remove();
  }

  /* ── 뱃지 안착 연출(2026-09-28) ─────────────────────────────────────────
     이번에 새로 받은 뱃지를 화면 가운데에 크게 띄웠다가(잠깐 머묾), 보상 칸의 원래 자리로 날아가며
     줄어들고 살짝 튕긴 뒤 "착" 붙는다. 도중에 탭하면 바로 제자리로 간다. 원본 img는 건드리지 않고
     복제본만 움직인다(위 공용 함수). */
  const BADGE_HOLD_MS = 250;

  // 새로 받은 뱃지만 — 재제출이라 보유 뱃지(bookBadge)로 칸을 채운 경우엔 연출하지 않는다
  function badgeIsNew(result) {
    return (result.newBadges ?? []).length > 0;
  }

  async function flyInBadge(result) {
    if (!badgeIsNew(result) || badgeReward.hidden || reducedMotion()) return;

    try {
      await rewardBadgeImg.decode();
    } catch (err) {
      // 이미지 폴백 중이거나 디코딩을 지원하지 않아도 연출은 그대로 진행한다
    }

    const badge = createFlyClone(rewardBadgeImg);
    if (!badge) return;
    // 가운데까지의 거리와, 가운데에서의 크기(화면 짧은 변의 42%)를 원본 대비 배율로
    const scale = (Math.min(window.innerWidth, window.innerHeight) * 0.42) / badge.rect.height;
    setFlyStart(
      badge,
      window.innerWidth / 2 - (badge.rect.left + badge.rect.width / 2),
      window.innerHeight / 2 - (badge.rect.top + badge.rect.height / 2),
      scale,
    );

    const catcher = openTapCatcher();
    if (!(await flyAppear([badge], catcher.tapped))) {
      badge.clone.classList.replace('fly-appear', 'fly-hold');
      await waitOrTap(BADGE_HOLD_MS, catcher.tapped);
    }
    await runFly([badge], 'fly-return', 1500);
    catcher.remove();
  }
})();
