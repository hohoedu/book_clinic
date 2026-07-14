(function () {
  const page = document.getElementById('resultPage');
  const studentId = page ? page.getAttribute('data-student-id') : null;

  const resultMascot = document.getElementById('resultMascot');
  const resultGradePill = document.getElementById('resultGradePill');
  const resultTitle = document.getElementById('resultTitle');
  const resultExp = document.getElementById('resultExp');
  const scoreCorrectEl = document.getElementById('scoreCorrect');
  const scoreWrongEl = document.getElementById('scoreWrong');
  const resultStatusText = document.getElementById('resultStatusText');
  const statsBar = document.querySelector('.result-stats-bar');
  const retryBtn = document.getElementById('retryBtn');
  const wrongRetryBtn = document.getElementById('wrongRetryBtn');
  const advancedBtn = document.getElementById('advancedBtn');
  const recommendBtn = document.getElementById('recommendBtn');
  const recommendConfirmModal = document.getElementById('recommendConfirmModal');
  const confirmYesBtn = document.getElementById('confirmYesBtn');
  const confirmNoBtn = document.getElementById('confirmNoBtn');

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
  renderNewBadges(result.newBadges);
  renderExp(result);

  // "틀린 문제 풀기" — 이번에 틀린 문항 번호만 세션 저장소에 담아두면 student-question.js가
  // 문제 목록을 불러온 뒤 그 번호만 걸러서 다시 낸다
  wrongRetryBtn.addEventListener('click', () => {
    sessionStorage.setItem('retryQnums', JSON.stringify(result.wrongQnums ?? []));
  });

  // "책 추천 받기" — 새 책을 받으면 이전 책은 자동 반납되므로(ClinicService.recommendBook),
  // 실수로 누르지 않도록 확인창을 한 번 거친다. (출석/퇴실 연동 전까지는 "남은 시간" 문구는 뺀 상태)
  recommendBtn.addEventListener('click', (e) => {
    e.preventDefault();
    recommendConfirmModal.hidden = false;
  });
  confirmNoBtn.addEventListener('click', () => {
    recommendConfirmModal.hidden = true;
  });
  confirmYesBtn.addEventListener('click', () => {
    window.location.href = recommendBtn.href;
  });

  function renderScore(result) {
    scoreCorrectEl.textContent = result.correctCount;
    scoreWrongEl.textContent = result.totalCount - result.correctCount;
  }

  function renderAdvancedResult(result) {
    resultStatusText.textContent = '완료!';
    statsBar.classList.remove('is-retry');

    resultGradePill.classList.remove('king', 'retry');
    resultGradePill.textContent = '심화문제';
    resultMascot.innerHTML = '<i class="fa-solid fa-star" aria-hidden="true"></i>';
    resultTitle.textContent = '심화문제까지 다 풀었어요!';

    retryBtn.hidden = true;
    wrongRetryBtn.hidden = true;
    advancedBtn.hidden = true;
    recommendBtn.hidden = false;
  }

  function renderKingResult(result) {
    resultStatusText.textContent = '통과!';
    statsBar.classList.remove('is-retry');

    resultGradePill.classList.remove('king', 'retry');
    resultGradePill.textContent = '독서왕';
    resultGradePill.classList.add('king');
    resultMascot.innerHTML = '<i class="fa-solid fa-crown" aria-hidden="true"></i>';
    resultTitle.textContent = '정말 멋져요! 책을 끝까지 읽고 문제도 잘 풀었어요!';

    retryBtn.hidden = true;
    wrongRetryBtn.hidden = true;
    advancedBtn.hidden = false;
    recommendBtn.hidden = false;
  }

  function renderFriendResult(result) {
    resultStatusText.textContent = '통과!';
    statsBar.classList.remove('is-retry');

    resultGradePill.classList.remove('king', 'retry');
    resultGradePill.textContent = '독서친구';
    resultMascot.innerHTML = '<i class="fa-solid fa-trophy" aria-hidden="true"></i>';
    resultTitle.textContent = '독서친구가 됐어요!';

    retryBtn.hidden = true;
    wrongRetryBtn.hidden = false;
    advancedBtn.hidden = false;
    recommendBtn.hidden = false;
  }

  function renderRetryResult(result) {
    resultStatusText.textContent = '재도전';
    statsBar.classList.add('is-retry');

    resultGradePill.classList.remove('king', 'retry');
    resultGradePill.textContent = '재도전 필요';
    resultGradePill.classList.add('retry');
    resultMascot.innerHTML = '<i class="fa-solid fa-rotate-right" aria-hidden="true"></i>';
    resultTitle.textContent = '합격선에 조금 못 미쳤어요. 다시 풀어볼까요?';

    retryBtn.hidden = false;
    wrongRetryBtn.hidden = true;
    advancedBtn.hidden = true;
    recommendBtn.hidden = true;
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

  function renderExp(result) {
    if (result.advanced) {
      resultExp.hidden = true;
      return;
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
  }
})();
