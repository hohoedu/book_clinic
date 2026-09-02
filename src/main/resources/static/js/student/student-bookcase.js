/*
  나의 책장 / 나의 카드 컬렉션 공용 화면.
  student-main 에서 <iframe src="/student/bookcase?type=book|card"> 로 띄운다.
  ?type=book  → 올해 읽은 책(완독/독서왕/재도전/읽는 중)
  ?type=card  → 완독 시 획득한 수집 카드(일반/레어)
  레이아웃(선반/그리드/학년탭/정렬)은 동일하고 데이터만 갈아끼운다.

  데이터: 서버가 템플릿에 심어준 window.__BOOKCASE__ 를 우선 사용하고,
  (단독 미리보기 등으로) 없으면 아래 임시 목업으로 폴백한다.
*/
(function () {
  const SERVER = window.__BOOKCASE__ || null;
  const params = new URLSearchParams(window.location.search);
  const TYPE = (SERVER && SERVER.type) || (params.get('type') === 'card' ? 'card' : 'book');

  // ── 폴백용 임시 목업 데이터 (서버 데이터가 없을 때만 사용) ─────────────
  const MOCK = {
    book: [
      { grade: 1, date: '2026-08-24', title: '치과의사 드소토 선생님', imageUrl: '/images/book-sample.png', status: 'complete' },
      { grade: 1, date: '2026-08-17', title: '소금 공해 이제 그만!', imageUrl: '/images/book-sample.png', status: 'king' },
      { grade: 1, date: '2026-08-10', title: '별빛 랜턴', imageUrl: '/images/book-sample.png', status: 'complete' },
      { grade: 1, date: '2026-08-03', title: '별의 조각', imageUrl: '/images/book-sample.png', status: 'retry' },
      { grade: 2, date: '2026-07-30', title: '반딧불이의 선물', imageUrl: '/images/book-sample.png', status: 'complete' },
      { grade: 3, date: '2026-07-19', title: '가을이 준 편지', imageUrl: '/images/book-sample.png', status: 'complete' }
    ],
    card: [
      { grade: 1, date: '2026-08-24', title: '치과의사 드소토 선생님', imageUrl: '/images/student_result/card.png', status: 'normal' },
      { grade: 1, date: '2026-08-17', title: '소금 공해 이제 그만!', imageUrl: '/images/student_result/card.png', status: 'normal' },
      { grade: null, date: '2026-08-10', title: '레어 카드', imageUrl: '/images/student_result/rare.png', status: 'rare' }
    ]
  };

  // type 별 화면 문구
  const PROFILE = {
    book: {
      title: '나의 책장',
      subtitle: '지금까지 읽은 책을 모았어요!',
      summaryLabel: '올해 읽은 책',
      unit: '권',
      emptyText: '다음 책이<br>기다리고 있어요!',
      statusLabel: (s) => ({ king: '독서왕', retry: '재도전', reading: '읽는 중' }[s] || '완독'),
      fallbackImg: '/images/book-sample.png',
      useGradeTabs: true
    },
    card: {
      title: '나의 카드 컬렉션',
      subtitle: '완독하고 모은 카드를 확인해요!',
      summaryLabel: '모은 카드',
      unit: '장',
      emptyText: '카드를<br>모아보세요!',
      statusLabel: (s) => (s === 'rare' ? '레어' : '일반'),
      fallbackImg: '/images/student_result/card.png',
      useGradeTabs: false
    }
  }[TYPE];

  const DATA = (SERVER && Array.isArray(SERVER.items)) ? SERVER.items : MOCK[TYPE];
  const DEFAULT_GRADE = (SERVER && SERVER.defaultGrade) || 1;

  // ── 상태 ───────────────────────────────────────────────────────────
  let currentGrade = DEFAULT_GRADE;
  let currentSort = 'latest';

  const stage = document.getElementById('stage');
  const titleEl = document.getElementById('bcTitle');
  const subtitleEl = document.getElementById('bcSubtitle');
  const summaryLabelEl = document.getElementById('bcSummaryLabel');
  const gradeTabsWrap = document.getElementById('gradeTabs');
  const gradeTabs = document.querySelectorAll('.grade-tab');
  const sortSelect = document.getElementById('sortSelect');
  const countEl = document.getElementById('bcCount');
  const shelfRows = document.getElementById('shelfRows');
  const shelfScroll = document.getElementById('shelfScroll');
  const closeBtn = document.querySelector('.close');

  stage.dataset.type = TYPE;
  document.title = `호호책방 - ${PROFILE.title}`;
  titleEl.textContent = PROFILE.title;
  subtitleEl.textContent = PROFILE.subtitle;
  summaryLabelEl.textContent = PROFILE.summaryLabel;

  // 카드 모드는 학년 구분이 애매해(레어 카드엔 학년이 없음) 학년 탭을 숨기고 전체를 한 번에 보여준다
  if (!PROFILE.useGradeTabs) {
    gradeTabsWrap.style.visibility = 'hidden';
  } else {
    // 학생의 현재 학년 탭을 초기 선택으로
    gradeTabs.forEach((tab) => {
      tab.classList.toggle('active', Number(tab.dataset.grade) === currentGrade);
    });
  }

  function makeCard(item) {
    const slot = document.createElement('div');
    slot.className = 'book-slot';

    const card = document.createElement('div');
    card.className = 'book-card';

    const img = document.createElement('img');
    img.src = item.imageUrl || PROFILE.fallbackImg;
    img.alt = item.title || '';
    img.onerror = () => {
      img.onerror = null;
      img.src = PROFILE.fallbackImg;
    };

    const badge = document.createElement('span');
    badge.className = 'status ' + item.status;
    badge.textContent = PROFILE.statusLabel(item.status);

    card.appendChild(img);
    card.appendChild(badge);
    slot.appendChild(card);
    return slot;
  }

  function makeEmpty() {
    const slot = document.createElement('div');
    slot.className = 'book-slot';
    slot.innerHTML = `
      <div class="empty">
        <span>
          <span class="icon">▢</span>
          ${PROFILE.emptyText}
        </span>
      </div>`;
    return slot;
  }

  function render() {
    let filtered = DATA.slice();
    if (PROFILE.useGradeTabs) {
      filtered = filtered.filter((item) => Number(item.grade) === currentGrade);
    }
    filtered.sort((a, b) => {
      const da = new Date(a.date);
      const db = new Date(b.date);
      return currentSort === 'latest' ? db - da : da - db;
    });

    countEl.textContent = filtered.length + PROFILE.unit;
    shelfRows.innerHTML = '';

    const items = [...filtered];
    if (items.length === 0) {
      for (let i = 0; i < 8; i++) items.push(null);
    } else {
      const remainder = items.length % 8;
      if (remainder !== 0) {
        for (let i = 0; i < 8 - remainder; i++) items.push(null);
      }
    }

    for (let i = 0; i < items.length; i += 8) {
      const row = document.createElement('div');
      row.className = 'shelf-row';
      items.slice(i, i + 8).forEach((item) => {
        row.appendChild(item ? makeCard(item) : makeEmpty());
      });
      shelfRows.appendChild(row);
    }

    shelfScroll.scrollTop = 0;
  }

  gradeTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      gradeTabs.forEach((item) => item.classList.remove('active'));
      tab.classList.add('active');
      currentGrade = Number(tab.dataset.grade);
      render();
    });
  });

  sortSelect.addEventListener('change', () => {
    currentSort = sortSelect.value;
    render();
  });

  // 닫기 — iframe 안에서 뜨므로 부모(student-main)에게 알린다.
  // 단독 접근 시엔 그냥 히스토리 뒤로.
  function requestClose() {
    if (window.parent && window.parent !== window) {
      window.parent.postMessage({ type: 'bookcase:close' }, '*');
    } else if (history.length > 1) {
      history.back();
    } else {
      window.close();
    }
  }
  closeBtn.addEventListener('click', requestClose);
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') requestClose();
  });

  render();
})();
