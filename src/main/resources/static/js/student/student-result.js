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
  const rewardLevelName = document.getElementById('rewardLevelName');
  const rewardExpDesc = document.getElementById('rewardExpDesc');
  const cardReward = document.getElementById('cardReward');
  const rareFlag = document.getElementById('rareFlag');
  const rewardCardImg = document.getElementById('rewardCardImg');
  const rewardCardName = document.getElementById('rewardCardName');
  const rewardCardDesc = document.getElementById('rewardCardDesc');
  const rewardProgressBar = document.getElementById('rewardProgressBar');
  const rewardStepNow = document.getElementById('rewardStepNow');
  const rewardStepTotal = document.getElementById('rewardStepTotal');
  const retryBtn = document.getElementById('retryBtn');
  const wrongRetryBtn = document.getElementById('wrongRetryBtn');
  const advancedBtn = document.getElementById('advancedBtn');

  const homeBtn = document.querySelector('.btn-home');
  const logoutBtn = document.querySelector('.logout-btn');
  const contentId = page ? page.getAttribute('data-content-id') : null;
  const qlevel = (page ? page.getAttribute('data-qlevel') : null) || '01';

  // 결과 화면의 "로그아웃" 버튼 — 예전엔 아예 연결돼있지 않아 눌러도 아무 반응이 없었다(2026-08-26).
  // student-main.js와 같은 방식으로 서버 세션을 먼저 무효화하고("문제 푸는 중"/"결과 확인중" 표시도
  // 함께 해제) 로그인 화면으로 이동한다.
  if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
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
    });
  }

  // 이 책이 끝난 상태인지(=책은 이미 반납됨) — 독서왕/독서친구/심화완료면 true, 재도전(불합격)이면
  // false. true일 때 "홈으로"를 누르면 일반 홈이 아니라 완료 화면(mode=retryDone)으로 보낸다 —
  // 문제 풀기 버튼 자리에 남은 액션(틀린 문제 다시 풀기/심화 문제 풀기)과 "책 추천받기" 버튼을
  // 보여주기 위함이다(2026-08-25, 예전의 "다음 책 받을까요?" 팝업을 대체)
  let bookFinished = false;

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
    renderCard(result);

    // "틀린 문제 풀기" — 이번에 틀린 문항 번호만 세션 저장소에 담아두면 student-question.js가
    // 문제 목록을 불러온 뒤 그 번호만 걸러서 다시 낸다
    wrongRetryBtn.addEventListener('click', () => {
      sessionStorage.setItem('retryQnums', JSON.stringify(result.wrongQnums ?? []));
    });
  })();

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

    // 재도전 여부 — 서버가 내려주는 attemptNo(이번 제출이 몇 번째 시도인지)로 몇 번째 재도전인지 표시한다
    // (1=첫 시도라 재도전 없음, 2 이상이면 그 값-1이 이번까지의 재도전 횟수)
    const attemptNo = result.attemptNo ?? 1;
    resultRetryText.textContent = attemptNo > 1 ? `${attemptNo - 1}번째` : '없음';
  }

  function renderAdvancedResult(result) {
    setHeroRibbon(true);
    setHero('ADVANCED', '심화문제 완료!');
    resultTitle.textContent = '심화문제까지 다 풀었어요!';

    retryBtn.hidden = true;
    wrongRetryBtn.hidden = true;
    advancedBtn.hidden = true;
    bookFinished = true;
  }

  function renderKingResult(result) {
    setHeroRibbon(true);
    setHero('KING', '독서왕 달성!');
    resultTitle.textContent = `${bookTitle(result)}을(를) 완독하고 멋지게 문제를 풀었어요!`;

    // 만점(독서왕)은 재도전·틀린 문제 다시 풀기 둘 다 없고, 심화만 남는다(2026-08-28)
    retryBtn.hidden = true;
    wrongRetryBtn.hidden = true;
    advancedBtn.hidden = false;
    // "홈으로"를 누르면 완료 화면(남은 액션 + 책 추천받기)으로 간다 — 다시풀기(alreadyCompleted)
    // 재제출이어도 마찬가지다(2026-08-25, 예전엔 이때만 예외로 그냥 홈으로 보냈다)
    bookFinished = true;
  }

  function renderFriendResult(result) {
    setHeroRibbon(true);
    setHero('FRIEND', '독서친구 달성!');
    resultTitle.textContent = `${bookTitle(result)}을(를) 읽고 문제를 풀었어요!`;

    // 독서친구는 재도전(점수 올리기) / 틀린 문제 다시 풀기 / 심화 셋 다 열어준다(2026-08-28).
    // 재도전으로 만점 치면 grade·뱃지가 독서왕으로 올라가 다음엔 renderKingResult로 그려진다.
    // 재도전 점수가 더 낮으면 처음 점수·등급·뱃지는 그대로고 최종 점수도 안 내려간다(max).
    retryBtn.hidden = false;
    // "틀린 문제 다시 풀기"로 남은 오답을 다 없앴으면(result.wrongQnums가 비었으면) 버튼을 감춘다
    wrongRetryBtn.hidden = (result.wrongQnums ?? []).length === 0;
    advancedBtn.hidden = false;
    bookFinished = true;
  }

  function renderRetryResult(result) {
    setHeroRibbon(false);
    setHero('RETRY', '다시 도전!');
    resultTitle.textContent = '합격선에 조금 못 미쳤어요. 다시 풀어볼까요?';

    retryBtn.hidden = false;
    wrongRetryBtn.hidden = true;
    advancedBtn.hidden = true;
    bookFinished = false;
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

  // 보상 패널의 레벨 칸 — EXP 폐지, 완독 권수로 레벨업(카드/독서여권 칸은 기능 미구현이라 자리만 잡아둔 상태)
  function renderExp(result) {
    if (result.levelNo != null) {
      rewardLevelNo.textContent = `Lv. ${result.levelNo}`;
    }
    rewardLevelName.textContent = result.levelTitle ?? '';
    if (result.progressPercent != null) {
      rewardProgressBar.style.width = `${result.progressPercent}%`;
    }
    if (result.stepNow != null) rewardStepNow.textContent = result.stepNow;
    if (result.stepTotal != null) rewardStepTotal.textContent = result.stepTotal;

    if (result.advanced) {
      rewardExpDesc.textContent = '심화문제는 레벨과 무관해요.';
    } else if (result.alreadyCompleted) {
      rewardExpDesc.textContent = '이미 완독한 책이에요.';
    } else if (result.leveledUp) {
      rewardExpDesc.textContent = `레벨업했어요! 🎉 Lv. ${result.levelNo} 달성`;
    } else if (result.passed && result.booksToNextLevel != null) {
      rewardExpDesc.textContent = `완독! 다음 레벨까지 ${result.booksToNextLevel}권 남았어요.`;
    } else if (result.passed) {
      rewardExpDesc.textContent = '완독했어요! 참 잘했어요 🎉';
    } else {
      rewardExpDesc.textContent = '아직 완독하지 못했어요. 다시 도전해봐요!';
    }
  }

  // 온라인 카드 칸 — 이번 제출로 새 완독이 되어 카드를 획득한 경우에만 노출한다(책당 1장).
  // 카드의 정체(책 제목/표지)는 가리고(???) 진행도만 보여준다. 10장을 채우면 실물 교환 안내.
  function renderCard(result) {
    if (!result.cardName) {
      cardReward.hidden = true;
      return;
    }
    cardReward.hidden = false;
    rareFlag.hidden = false;
    rewardCardName.textContent = '???';
    const collected = result.totalCards != null ? ((result.totalCards - 1) % 10) + 1 : null;
    if (result.cardRewardReached) {
      rewardCardDesc.textContent = '카드 10장 완성! 선생님께 실물 카드를 받으세요 🎉';
    } else if (collected != null) {
      rewardCardDesc.textContent = `카드 ${collected}/10장을 모았어요!`;
    } else {
      rewardCardDesc.textContent = '카드를 모았어요!';
    }
  }
})();
