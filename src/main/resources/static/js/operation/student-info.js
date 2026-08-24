/*
 * 학생 정보 — 목록은 아직 하드코딩 목업이라(student-info.html), 행을 클릭했을 때 뜨는 상세정보
 * 모달도 같은 단계의 목업이다. 모달 자체는 자매 서비스 all_pass(/Users/hohoedu/Desktop/all_pass)의
 * student-main-modal을 그대로 가져와 구조/클래스명을 재사용했다(student-info.css 참고) — 다만
 * all_pass는 "출결/상담내역" 탭인데 book_clinic은 "독서이력/예약" 탭이라 그 두 개만 새로 만들었다.
 *
 * 이름/학년/연락처/최근이용일/누적도서/레벨/상태는 클릭한 행의 data-* 값을 그대로 쓰고, 나머지
 * (생년월일·학교·주소·독서이력·예약현황·회비 등)는 API가 아직 없어서 모든 학생에게 같은 샘플
 * 값을 보여준다. 실제 상세 조회 API가 생기면 openStudentModal 안의 buildMockDetail 자리를
 * fetch(`/api/students/${studentId}`) 결과로 바꾸면 된다.
 */

document.addEventListener("DOMContentLoaded", () => {
  initStudentModal();
});

/* ===================== 목업 상세 데이터 ===================== */

const READING_HISTORY = [
  { no: 10, title: "책 먹는 여우", basic: "10/12", advanced: "6/6", result: "pass", note: "정독시간이 많이 짧아짐" },
  { no: 9, title: "미운아기오리", basic: "9/12", advanced: "5/6", result: "king", note: "" },
  { no: 8, title: "치과의사 드소토 선생님", basic: "10/12", advanced: "4/6", result: "retry", note: "독서여권을 쓰는데 어려움이 있음" },
  { no: 7, title: "이상한 나라의 앨리스", basic: "12/12", advanced: "6/6", result: "retry", note: "" },
  { no: 6, title: "프린들 주세요", basic: "10/12", advanced: "6/6", result: "pass", note: "" },
  { no: 5, title: "아주 이상한 물고기", basic: "9/12", advanced: "1/6", result: "pass", note: "" },
  { no: 4, title: "차례를 기다릴래요", basic: "10/12", advanced: "4/6", result: "king", note: "" },
  { no: 3, title: "뻥뻥! 꼬미야 조심해", basic: "12/12", advanced: "3/6", result: "retry", note: "" },
  { no: 2, title: "슈퍼 모험! 공룡 해적선", basic: "10/12", advanced: "6/6", result: "retry", note: "" },
  { no: 1, title: "벌거벗은 임금님", basic: "9/12", advanced: "2/6", result: "pass", note: "독서여권을 쓰는데 어려움이 있음" },
];

const RESULT_LABEL = { pass: "통과", king: "독서왕", retry: "재도전" };

const RECENT_BOOKS = [
  { title: "책 먹는 여우", date: "2026-08-12", basic: "10/12", advanced: "5/6", result: "pass" },
  { title: "백설공주", date: "2026-08-12", basic: "12/12", advanced: "5/6", result: "king" },
  { title: "치과의사 드소토 선생님", date: "2026-08-12", basic: "10/12", advanced: "5/6", result: "pass" },
  { title: "책 먹는 여우", date: "2026-08-12", basic: "12/12", advanced: "-", result: "king" },
];

const RESERVATIONS = [
  { date: "2026-08-19", status: "예약완료", statusClass: "resv-scheduled" },
  { date: "2026-08-12", status: "이용완료", statusClass: "resv-done" },
  { date: "2026-08-05", status: "이용완료", statusClass: "resv-done" },
  { date: "2026-07-29", status: "이용완료", statusClass: "resv-done" },
];

const GRADE_TO_SCHOOLYEAR = { "초1": "1학년", "초2": "2학년", "초3": "3학년", "초4": "4학년", "초5": "5학년", "초6": "6학년" };

/* 행의 data-* 값 + 나머지는 공용 샘플값으로 채운 상세 목업 1건 */
function buildMockDetail(row) {
  return {
    name: row.dataset.name,
    grade: row.dataset.grade,
    schoolyearLabel: GRADE_TO_SCHOOLYEAR[row.dataset.grade] ?? row.dataset.grade,
    phone: row.dataset.phone,
    regDate: row.dataset.regDate,
    visitDate: row.dataset.visitDate,
    books: row.dataset.books,
    level: row.dataset.level,
    levelClass: row.dataset.levelClass,
    status: row.dataset.status,
    statusClass: row.dataset.statusClass,
    // 아래는 API 연동 전까지 모든 학생 공통 샘플값 (all_pass 목업 값 참고)
    birthDate: "2016년 03월 15일",
    school: "호랑초등학교",
    address: "부산 해운대구 센텀중앙로97",
    detailAddress: "센텀스카이비즈 A동 2810호",
    parentPhone: "010-1234-5678",
    gender: "남자",
    relation: "모",
    joinDate: "2025년 2월 20일",
    bookLevelName: "독서 3단계",
    bookTeacher: "김서연 선생님",
    bookFee: "90,000",
    bookMaterialFee: "12,000",
    kingCount: 4,
    badgeCount: 35,
  };
}

/* ===================== 모달 열기/닫기/탭 ===================== */

let currentDetail = null;

function initStudentModal() {
  const modal = document.getElementById("studentModal");
  const listBody = document.getElementById("studentListBody");

  listBody.addEventListener("click", (event) => {
    const row = event.target.closest(".student-row");
    if (!row) return;
    openStudentModal(row);
  });

  document.getElementById("btnCloseStudentModal").addEventListener("click", closeStudentModal);
  modal.addEventListener("click", (event) => {
    if (event.target === modal) closeStudentModal();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !modal.hidden) closeStudentModal();
  });

  document.getElementById("studentTabs").addEventListener("click", (event) => {
    const tabBtn = event.target.closest("button[data-tab]");
    if (!tabBtn) return;
    switchTab(tabBtn.dataset.tab);
  });
}

function openStudentModal(row) {
  currentDetail = buildMockDetail(row);

  document.getElementById("studentModalTitle").textContent = `${currentDetail.name} 학생 상세정보`;
  switchTab("all");
  document.getElementById("studentModal").hidden = false;
}

function closeStudentModal() {
  document.getElementById("studentModal").hidden = true;
  currentDetail = null;
}

function switchTab(tab) {
  document.querySelectorAll("#studentTabs button[data-tab]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.tab === tab);
  });

  const renderers = {
    all: renderAllTab,
    basic: renderBasicTab,
    fee: renderFeeTab,
    reading: renderReadingTab,
    reservation: renderReservationTab,
  };

  document.getElementById("studentModalBody").innerHTML = renderers[tab](currentDetail);
}

/* 상태 코드 → all_pass 상태버튼(재원중/입학취소/휴원/탈퇴) 매핑. book_clinic은 "이용중/탈퇴" 2단계뿐이라
   재원중/탈퇴만 실제로 쓰이고 나머지 둘은 모양만 맞춰 흐리게 둔다 */
function statusButtons(status) {
  const map = [
    { key: "이용중", label: "재원중", cls: "active" },
    { key: "입학취소", label: "입학취소", cls: "cancel" },
    { key: "휴원", label: "휴원", cls: "closed" },
    { key: "탈퇴", label: "탈퇴", cls: "danger" },
  ];
  return `
    <div class="status-buttons">
      ${map.map((m) => `<button type="button" class="btn-status ${m.cls} ${m.key === status ? "selected" : ""}">${m.label}</button>`).join("")}
    </div>
  `;
}

/* 전체 탭 헤더용 — 4단계 버튼 그룹 대신 현재 상태 하나만 재원중/탈퇴 색으로 표시 */
function singleStatusPill(status) {
  const cls = status === "탈퇴" ? "danger" : "active";
  return `<span class="status-chip ${cls}">${status}</span>`;
}

/* ===================== 탭별 렌더링 ===================== */

function renderAllTab(d) {
  return `
    <div class="all-info">
      <div class="card-left">
        <article class="card-common card-compact">
          <header class="card-header">
            <h4 class="sub-themes">기본 정보</h4>
            ${singleStatusPill(d.status)}
          </header>
          <section class="card-body">
            <figure class="profile-img">
              <img src="/images/stu-img.png" alt="학생 프로필 이미지">
            </figure>
            <div>
              <h5 class="student-name">${d.name}</h5>
              <p class="student-tel">${d.phone}</p>
            </div>
          </section>
          <dl class="student-details">
            <div class="detail-row"><dt>입회일</dt><dd>${d.joinDate}</dd></div>
            <div class="detail-row"><dt>학교</dt><dd>${d.school}</dd></div>
            <div class="detail-row"><dt>학년</dt><dd>${d.schoolyearLabel}</dd></div>
            <div class="detail-row"><dt>생년월일</dt><dd>${d.birthDate}</dd></div>
            <div class="detail-row"><dt>주소</dt><dd>${d.address}</dd></div>
            <div class="detail-row"><dt>상세주소</dt><dd>${d.detailAddress}</dd></div>
          </dl>
        </article>

        <article class="card-common card-grow">
          <h4 class="sub-themes">예약 현황</h4>
          <table class="basic-table table-head-fixed">
            <colgroup><col style="width:50%"><col style="width:50%"></colgroup>
            <thead><tr><th>날짜</th><th>상태</th></tr></thead>
          </table>
          <div class="table-frame">
            <table class="basic-table">
              <colgroup><col style="width:50%"><col style="width:50%"></colgroup>
              <tbody>
                ${RESERVATIONS.map((r) => `
                  <tr><td>${r.date}</td><td><span class="resv-pill ${r.statusClass}">${r.status}</span></td></tr>
                `).join("")}
              </tbody>
            </table>
          </div>
        </article>
      </div>

      <div class="card-right">
        <div class="card-common">
          <h4 class="sub-themes">독서 현황</h4>
          <div class="stat-tiles">
            <div class="stat-tile">
              <div class="stat-icon">📖</div>
              <div class="stat-label">누적 독서</div>
              <div class="stat-value">${d.books}권</div>
            </div>
            <div class="stat-tile">
              <div class="stat-icon">🏅</div>
              <div class="stat-label">현재 레벨</div>
              <div class="stat-value">Lv. ${d.level}</div>
            </div>
            <div class="stat-tile">
              <div class="stat-icon">🏆</div>
              <div class="stat-label">독서왕 횟수</div>
              <div class="stat-value">${d.kingCount}회</div>
            </div>
            <div class="stat-tile">
              <div class="stat-icon">🛡️</div>
              <div class="stat-label">획득 뱃지</div>
              <div class="stat-value">${d.badgeCount}개</div>
            </div>
          </div>
        </div>

        <div class="card-common card-grow">
          <h4 class="sub-themes">최근 읽은 책</h4>
          <div class="recent-book-scroll">
            <ul class="recent-book-list">
              ${RECENT_BOOKS.map((b) => `
                <li>
                  <div class="recent-book-thumb"></div>
                  <div class="recent-book-info">
                    <div class="recent-book-title">${b.title}</div>
                    <div class="recent-book-meta">
                      ${b.date} &nbsp;|&nbsp; 기본문제 ${b.basic} &nbsp;|&nbsp; 심화문제 ${b.advanced} &nbsp;|&nbsp;
                      <span class="result-text result-${b.result}">${RESULT_LABEL[b.result]}</span>
                    </div>
                  </div>
                </li>
              `).join("")}
            </ul>
          </div>
        </div>
      </div>
    </div>
  `;
}

function renderBasicTab(d) {
  return `
    <table class="base-info-t">
      <tbody>
        <tr>
          <th>이름</th>
          <td><input type="text" value="${d.name}" readonly></td>
          <th>상태</th>
          <td>${statusButtons(d.status)}</td>
        </tr>
        <tr>
          <th>생년월일</th>
          <td class="icon-field">${d.birthDate} <i class="fa-regular fa-calendar icon-btn"></i></td>
          <th>성별</th>
          <td class="choose-group">
            <span class="btn-choose ${d.gender === "남자" ? "active" : ""}">남자</span>
            <span class="btn-choose ${d.gender === "여자" ? "active" : ""}">여자</span>
          </td>
        </tr>
        <tr>
          <th>학교</th>
          <td class="icon-field"><input type="text" value="${d.school}" readonly> <i class="fa-solid fa-magnifying-glass icon-btn"></i></td>
          <th>주소</th>
          <td class="icon-field"><input type="text" value="${d.address}" readonly> <i class="fa-solid fa-magnifying-glass icon-btn"></i></td>
        </tr>
        <tr>
          <th>학년</th>
          <td>${d.schoolyearLabel}</td>
          <th>상세주소</th>
          <td><input type="text" value="${d.detailAddress}" readonly></td>
        </tr>
        <tr>
          <th>부모님HP</th>
          <td><input type="tel" value="${d.parentPhone}" readonly></td>
          <th>학생과의 관계</th>
          <td class="choose-group">
            <span class="btn-choose ${d.relation === "부" ? "active" : ""}">부</span>
            <span class="btn-choose ${d.relation === "모" ? "active" : ""}">모</span>
            <span class="btn-choose ${d.relation === "기타" ? "active" : ""}">기타</span>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="save-btn-frame">
      <button type="submit" class="save-btn" disabled title="상세정보 API 연동 전이라 저장은 아직 동작하지 않습니다">저장</button>
    </div>
  `;
}

function renderFeeTab(d) {
  return `
    <div class="small-title">수강과목(독서)</div>
    <table class="base-info-t">
      <colgroup><col style="width:15%"><col style="width:35%"><col style="width:15%"><col style="width:35%"></colgroup>
      <tbody>
        <tr>
          <th>독서 단계</th>
          <td>${d.bookLevelName}</td>
          <th>수강 상태</th>
          <td class="choose-group"><span class="btn-choose active">수강</span><span class="btn-choose">미수강</span></td>
        </tr>
        <tr>
          <th>담당 선생님</th>
          <td>${d.bookTeacher}</td>
          <th>시작일자</th>
          <td>${d.joinDate}</td>
        </tr>
        <tr>
          <th>교육비</th>
          <td><input type="text" value="${d.bookFee}" readonly></td>
          <th>교재비</th>
          <td><input type="text" value="${d.bookMaterialFee}" readonly></td>
        </tr>
      </tbody>
    </table>
    <div class="dues-sum"><p>회비 합계 <span>${d.bookFee}</span>원</p></div>
    <div class="save-btn-frame">
      <button type="submit" class="save-btn" disabled title="회비 API 연동 전이라 저장은 아직 동작하지 않습니다">교재비 저장</button>
    </div>
  `;
}

/* all_pass엔 없는 book_clinic 자체 탭 — 독서이력/예약 */

function renderReadingTab() {
  return `
    <table class="history-table">
      <thead>
        <tr><th>No</th><th>도서명</th><th>기본 문제</th><th>심화 문제</th><th>결과</th><th>기타 전달사항</th></tr>
      </thead>
      <tbody>
        ${READING_HISTORY.map((h) => `
          <tr>
            <td class="col-no">${h.no}</td>
            <td class="col-title">${h.title}</td>
            <td>${h.basic}</td>
            <td>${h.advanced}</td>
            <td><span class="result-badge result-${h.result}">${RESULT_LABEL[h.result]}</span></td>
            <td class="col-note">${h.note}</td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
}

function renderReservationTab() {
  return `
    <table class="history-table">
      <thead><tr><th>날짜</th><th>상태</th></tr></thead>
      <tbody>
        ${RESERVATIONS.map((r) => `
          <tr>
            <td>${r.date}</td>
            <td><span class="resv-pill ${r.statusClass}">${r.status}</span></td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
}
