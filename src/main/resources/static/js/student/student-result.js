(function () {
  const page = document.getElementById('resultPage');
  const studentId = page ? page.getAttribute('data-student-id') : null;

  const heroRibbon = document.getElementById('heroRibbon');
  const heroRibbonText = document.getElementById('heroRibbonText');
  const heroHeadline = document.getElementById('heroHeadline');
  const heroHeadlineText = document.getElementById('heroHeadlineText');

  // 달성 문구 이미지는 독서왕(ment.png)만 있고, 나머지 등급은 텍스트 제목으로 대체한다
  const HERO_HEADLINE_IMG = { KING: '/images/student_result/ment.png' };
  const heroCharacter = document.getElementById('heroCharacter');
  const scoreCorrectEl = document.getElementById('scoreCorrect');
  const scoreTotalEl = document.getElementById('scoreTotal');
  const scoreStars = document.getElementById('scoreStars');
  const resultTitle = document.getElementById('resultTitle');
  const resultRetryText = document.getElementById('resultRetryText');
  const rewardLevelNo = document.getElementById('rewardLevelNo');
  const rewardExpDesc = document.getElementById('rewardExpDesc');
  const rewardProgressBar = document.getElementById('rewardProgressBar');
  const retryBtn = document.getElementById('retryBtn');
  const wrongRetryBtn = document.getElementById('wrongRetryBtn');
  const advancedBtn = document.getElementById('advancedBtn');

  // student-question.js가 채점 직후 세션 저장소에 담아둔 결과 — 새로고침/직접 접근 등으로
  // 결과가 없으면 이 화면에서 보여줄 게 없으므로 메인으로 돌려보낸다
  const raw = sessionStorage.getItem('quizResult');
  if (!raw) {
    window.location.replace(`/student/main?studentId=${encodeURIComponent(studentId)}`);
    return;
  }
  sessionStorage.removeItem('quizResult');
  const result = JSON.parse(raw);

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

  // "틀린 문제 풀기" — 이번에 틀린 문항 번호만 세션 저장소에 담아두면 student-question.js가
  // 문제 목록을 불러온 뒤 그 번호만 걸러서 다시 낸다
  wrongRetryBtn.addEventListener('click', () => {
    sessionStorage.setItem('retryQnums', JSON.stringify(result.wrongQnums ?? []));
  });

  function renderScore(result) {
    const correct = result.correctCount ?? 0;
    const total = result.totalCount ?? 0;
    scoreCorrectEl.textContent = correct;
    scoreTotalEl.textContent = total;

    // 별 5개를 정답률로 채운다 (한 문제라도 맞히면 최소 1개)
    const ratio = total > 0 ? correct / total : 0;
    const onCount = correct > 0 ? Math.max(1, Math.round(ratio * 5)) : 0;
    Array.from(scoreStars.children).forEach((star, i) => {
      star.classList.toggle('is-on', i < onCount);
    });

    // 재도전 여부 — 지금은 이번 제출이 재도전이었는지(retry 플래그)만 표시한다
    resultRetryText.textContent = result.retried ? '있음' : '없음';
  }

  function renderAdvancedResult(result) {
    setHeroRibbon(true);
    setHero('ADVANCED', '심화문제 완료!');
    resultTitle.textContent = '심화문제까지 다 풀었어요!';

    retryBtn.hidden = true;
    wrongRetryBtn.hidden = true;
    advancedBtn.hidden = true;
  }

  function renderKingResult(result) {
    setHeroRibbon(true);
    setHero('KING', '독서왕 달성!');
    resultTitle.textContent = `${bookTitle(result)}을(를) 완독하고 멋지게 문제를 풀었어요!`;

    retryBtn.hidden = true;
    // 만점이면 다시 풀 문제가 없으므로 "틀린 문제 다시 풀기"는 감춘다
    wrongRetryBtn.hidden = (result.wrongQnums ?? []).length === 0;
    advancedBtn.hidden = false;
  }

  function renderFriendResult(result) {
    setHeroRibbon(true);
    setHero('FRIEND', '독서친구 달성!');
    resultTitle.textContent = `${bookTitle(result)}을(를) 읽고 문제를 풀었어요!`;

    retryBtn.hidden = true;
    wrongRetryBtn.hidden = false;
    advancedBtn.hidden = false;
  }

  function renderRetryResult(result) {
    setHeroRibbon(false);
    setHero('RETRY', '다시 도전!');
    resultTitle.textContent = '합격선에 조금 못 미쳤어요. 다시 풀어볼까요?';

    retryBtn.hidden = false;
    wrongRetryBtn.hidden = true;
    advancedBtn.hidden = true;
  }

  // 통과 등급은 "정말 잘했어요!" 리본 이미지(green.png), 재도전은 문구가 맞지 않아 텍스트 리본
  function setHeroRibbon(passed) {
    heroRibbon.hidden = !passed;
    heroRibbonText.hidden = passed;
  }

  // 달성 문구는 등급별 이미지가 있으면 이미지, 없으면 같은 자리에 텍스트로 표시한다.
  // 캐릭터는 아직 한 종류(kidoc.png)뿐이라 data-grade만 남겨 둔다
  function setHero(grade, headlineText) {
    const src = HERO_HEADLINE_IMG[grade];
    heroHeadline.hidden = !src;
    heroHeadlineText.hidden = Boolean(src);
    if (src) {
      heroHeadline.src = src;
      heroHeadline.alt = headlineText;
    } else {
      heroHeadlineText.textContent = headlineText;
    }
    heroHeadline.dataset.grade = grade;
    heroCharacter.dataset.grade = grade;
  }

  function bookTitle(result) {
    return result.bookTitle || '이 책';
  }

  // 보상 패널의 경험치 칸 — 카드/독서여권/독서탐험 칸은 기능 미구현이라 화면 자리만 잡아둔 상태
  function renderExp(result) {
    if (result.levelNo != null) {
      rewardLevelNo.textContent = `Lv. ${result.levelNo}`;
    }
    if (result.progressPercent != null) {
      rewardProgressBar.style.width = `${result.progressPercent}%`;
    }

    if (result.advanced) {
      rewardExpDesc.textContent = '심화문제는 추가 경험치가 없어요.';
    } else if (result.alreadyCompleted) {
      rewardExpDesc.textContent = '이미 완독한 책이라 추가 경험치는 없어요.';
    } else if (result.leveledUp) {
      rewardExpDesc.textContent = `EXP +${result.expGained} 획득! 레벨업했어요 🎉`;
    } else if (result.expGained > 0) {
      rewardExpDesc.textContent = `EXP +${result.expGained} 획득!`;
    } else {
      rewardExpDesc.textContent = '이번에는 획득한 경험치가 없어요.';
    }
  }
})();
