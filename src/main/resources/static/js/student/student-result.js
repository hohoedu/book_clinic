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
  const rewardCardImg = document.getElementById('rewardCardImg');
  const rewardCardName = document.getElementById('rewardCardName');
  const rewardCardDesc = document.getElementById('rewardCardDesc');
  const rewardProgressBar = document.getElementById('rewardProgressBar');
  const retryBtn = document.getElementById('retryBtn');
  const wrongRetryBtn = document.getElementById('wrongRetryBtn');
  const advancedBtn = document.getElementById('advancedBtn');

  const nextBookModal = document.getElementById('nextBookModal');
  const nextBookYesBtn = document.getElementById('nextBookYesBtn');
  const nextBookNoBtn = document.getElementById('nextBookNoBtn');
  const homeBtn = document.querySelector('.btn-home');

  // 이 책이 끝난 상태인지(=책은 이미 반납됨) — 독서왕/독서친구/심화완료면 true, 재도전(불합격)이면
  // false. true일 때만 "홈으로"를 눌렀을 때 다음 책 질문 팝업을 가로채서 띄운다.
  let bookFinished = false;

  // "홈으로"를 누른 순간 물어본다(2026-07-29) — 결과 화면에 도착하자마자 묻지 않는다. 책은 이미
  // ClinicService.submitQuiz에서 반납 처리돼 있으므로 "아니요"를 골라도 반납 자체는 유지된다.
  if (homeBtn) {
    homeBtn.addEventListener('click', (e) => {
      if (!bookFinished) return; // 재도전(불합격) 등은 그냥 홈으로 이동
      e.preventDefault();
      if (nextBookModal) nextBookModal.hidden = false;
    });
  }

  if (nextBookYesBtn) {
    nextBookYesBtn.addEventListener('click', async () => {
      nextBookYesBtn.disabled = true;
      try {
        const res = await fetch('/clinic/recommend', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ studentId }),
        });
        const data = await res.json();
        if (!data.success) throw new Error(data.error?.message ?? '추천에 실패했어요.');
        window.location.href = `/student/main?studentId=${encodeURIComponent(studentId)}`;
      } catch (err) {
        console.error(err);
        alert(err.message || '추천에 실패했어요.');
        nextBookYesBtn.disabled = false;
      }
    });
  }
  if (nextBookNoBtn) {
    nextBookNoBtn.addEventListener('click', () => {
      window.location.href = `/student/main?studentId=${encodeURIComponent(studentId)}`;
    });
  }

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
  renderCard(result);

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
    bookFinished = true;
  }

  function renderKingResult(result) {
    setHeroRibbon(true);
    setHero('KING', '독서왕 달성!');
    resultTitle.textContent = `${bookTitle(result)}을(를) 완독하고 멋지게 문제를 풀었어요!`;

    retryBtn.hidden = true;
    // 만점이면 다시 풀 문제가 없으므로 "틀린 문제 다시 풀기"는 감춘다
    wrongRetryBtn.hidden = (result.wrongQnums ?? []).length === 0;
    advancedBtn.hidden = false;
    bookFinished = !result.alreadyCompleted;
  }

  function renderFriendResult(result) {
    setHeroRibbon(true);
    setHero('FRIEND', '독서친구 달성!');
    resultTitle.textContent = `${bookTitle(result)}을(를) 읽고 문제를 풀었어요!`;

    retryBtn.hidden = true;
    wrongRetryBtn.hidden = false;
    advancedBtn.hidden = false;
    bookFinished = !result.alreadyCompleted;
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
