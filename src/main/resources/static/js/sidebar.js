document.addEventListener("DOMContentLoaded", initSidebar);
document.addEventListener("DOMContentLoaded", hideHqOnlyMenus);

/* 본사 전용 메뉴 숨김 — 화면에서 숨기는 것만으로는 API 직접 호출을 못 막으므로
   서버(CenterPolicy.assertHq)가 항상 한 번 더 검사한다. 여기는 표시 정리용이다. */
async function hideHqOnlyMenus() {
  const hqOnly = document.querySelectorAll("[data-hq-only]");
  if (!hqOnly.length) return;

  try {
    const res = await fetch("/api/user/me");
    const data = await res.json();
    if (data.success && data.response.centerCode === "PUS001") return;
  } catch {
    /* 조회에 실패하면 숨긴 채로 둔다 — 잘못 보여주는 쪽보다 안전하다 */
  }
  hqOnly.forEach((el) => el.remove());
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
