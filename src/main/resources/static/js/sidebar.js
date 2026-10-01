document.addEventListener("DOMContentLoaded", initSidebar);
document.addEventListener("DOMContentLoaded", hideHqOnlyMenus);
document.addEventListener("DOMContentLoaded", initDemoReset);

/* 로그인 직원 정보 — 사이드바 기능들이 같이 쓰므로 한 번만 조회한다. 실패하면 null */
let sidebarMePromise = null;
function loadSidebarMe() {
  if (!sidebarMePromise) {
    sidebarMePromise = fetch("/api/user/me")
      .then((res) => res.json())
      .then((data) => (data.success ? data.response : null))
      .catch(() => null);
  }
  return sidebarMePromise;
}

/* 본사 전용 메뉴 숨김 — 화면에서 숨기는 것만으로는 API 직접 호출을 못 막으므로
   서버(CenterPolicy.assertHq)가 항상 한 번 더 검사한다. 여기는 표시 정리용이다. */
async function hideHqOnlyMenus() {
  const hqOnly = document.querySelectorAll("[data-hq-only]");
  if (!hqOnly.length) return;

  /* 조회에 실패하면 숨긴 채로 둔다 — 잘못 보여주는 쪽보다 안전하다 */
  const me = await loadSidebarMe();
  if (me && me.centerCode === "PUS001") return;
  hqOnly.forEach((el) => el.remove());
}

/* 시연 데이터 초기화 (2026-10-01) — 시연 관리자 계정에서만 버튼을 보인다.
   실행 권한/비밀번호/대상 학생은 서버(DemoResetService)가 다시 검사한다. */
const DEMO_USER_CODE = "PUS001cos";

async function initDemoReset() {
  const btn = document.getElementById("demoResetBtn");
  if (!btn) return;

  const me = await loadSidebarMe();
  if (!me || me.userCode !== DEMO_USER_CODE) {
    btn.remove();
    return;
  }
  btn.hidden = false;

  const ACTIONS = {
    reset: { url: "/admin/demo/reset", done: "기준점 상태로 초기화했습니다." },
    snapshot: { url: "/admin/demo/snapshot", done: "현재 상태를 기준점으로 저장했습니다." },
  };

  btn.addEventListener("click", async () => {
    const picked = await promptDemoReset();
    if (!picked) return;
    const action = ACTIONS[picked.action];

    btn.disabled = true;
    try {
      const token = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
      const res = await fetch(action.url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { "X-XSRF-TOKEN": decodeURIComponent(token[1]) } : {}),
        },
        body: JSON.stringify({ password: picked.password }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok || !data?.success) {
        alert(data?.error?.message || "처리에 실패했습니다.");
        return;
      }
      alert(action.done);
      location.reload();
    } catch {
      alert("요청 중 오류가 발생했습니다.");
    } finally {
      btn.disabled = false;
    }
  });
}

/* 시연 초기화 모달 — 공용 컨펌 모달(common.css) 모양에 비밀번호 입력칸을 더했다.
   { action: "reset" | "snapshot", password } 또는 취소하면 null */
function promptDemoReset() {
  return new Promise((resolve) => {
    const overlay = document.createElement("div");
    overlay.className = "custom-confirm-overlay";
    overlay.innerHTML = `
      <div class="custom-confirm">
        <p class="custom-confirm-message">[초기화] 시연 계정 학생들을 저장해 둔 기준점 상태로 되돌립니다. 시연 전부터 있던 기록은 남습니다.\n[기준점 저장] 지금 상태를 새 기준점으로 저장합니다. 시연 전 깨끗한 상태에서만 눌러 주세요.\n\n비밀번호를 입력해 주세요.</p>
        <input type="password" class="custom-confirm-input" autocomplete="current-password" placeholder="비밀번호">
        <div class="custom-confirm-actions">
          <button type="button" class="btn outline custom-confirm-cancel">취소</button>
          <button type="button" class="btn outline" data-action="snapshot">기준점 저장</button>
          <button type="button" class="btn primary" data-action="reset">초기화</button>
        </div>
      </div>
    `;
    const input = overlay.querySelector(".custom-confirm-input");

    const close = (result) => {
      overlay.remove();
      document.removeEventListener("keydown", onKeydown);
      resolve(result);
    };
    const submit = (action) => {
      if (!input.value) {
        input.focus();
        return;
      }
      close({ action, password: input.value });
    };
    const onKeydown = (event) => {
      if (event.key === "Escape") close(null);
      if (event.key === "Enter") submit("reset");
    };

    overlay.querySelector(".custom-confirm-cancel").addEventListener("click", () => close(null));
    overlay.querySelectorAll("[data-action]").forEach((b) =>
      b.addEventListener("click", () => submit(b.dataset.action)));
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) close(null);
    });
    document.addEventListener("keydown", onKeydown);

    document.body.appendChild(overlay);
    input.focus();
  });
}

function initSidebar() {
  const layout = document.querySelector(".admin-layout");
  const sidebar = document.querySelector(".sidebar");
  const mainItems = document.querySelectorAll(".main-menu-item");
  const subMenus = document.querySelectorAll(".sub-menu");
  const subLinks = document.querySelectorAll(".sub-menu a");

  const normalizePage = (path) => path.split("/").pop().replace(/\.html$/, "");
  const currentPage = normalizePage(location.pathname);

  let currentMenuKey = null;
  // 클릭으로 "고정"된 메뉴 — 고정 중에는 다른 아이콘에 마우스가 스쳐도 서브메뉴가 안 바뀌고,
  // 사이드바 영역을 완전히 벗어나야 고정이 풀린다(2026-07-30).
  let pinnedMenuKey = null;

  subLinks.forEach((link) => {
    const href = link.getAttribute("href");

    if (normalizePage(href) !== currentPage) return;

    link.classList.add("active");
    currentMenuKey = link.closest(".sub-menu").dataset.sub;
  });

  function setActiveMenu(menuKey) {
    mainItems.forEach((item) => {
      item.classList.toggle("active", item.dataset.menu === menuKey);
    });

    subMenus.forEach((menu) => {
      menu.classList.toggle("active", menu.dataset.sub === menuKey);
    });
  }

  function openMenu(menuKey) {
    layout.classList.add("sidebar-open");
    setActiveMenu(menuKey);
  }

  function closeMenu() {
    layout.classList.remove("sidebar-open");
    setActiveMenu(currentMenuKey);
  }

  setActiveMenu(currentMenuKey);

  mainItems.forEach((item) => {
    item.addEventListener("mouseenter", () => {
      if (pinnedMenuKey) return; // 고정 중엔 다른 아이콘에 스쳐도 서브메뉴를 바꾸지 않는다
      openMenu(item.dataset.menu);
    });

    item.addEventListener("click", () => {
      pinnedMenuKey = item.dataset.menu;
      layout.classList.add("sidebar-pinned"); // 고정 상태 표시(체크 아이콘 등 CSS 훅)
      openMenu(pinnedMenuKey);
    });
  });

  sidebar.addEventListener("mouseleave", () => {
    pinnedMenuKey = null;
    layout.classList.remove("sidebar-pinned");
    closeMenu();
  });
}
