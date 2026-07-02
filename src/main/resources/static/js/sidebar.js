document.addEventListener("DOMContentLoaded", initSidebar);

function initSidebar() {
  const layout = document.querySelector(".admin-layout");
  const sidebar = document.querySelector(".sidebar");
  const mainItems = document.querySelectorAll(".main-menu-item");
  const subMenus = document.querySelectorAll(".sub-menu");
  const subLinks = document.querySelectorAll(".sub-menu a");

  const normalizePage = (path) => path.split("/").pop().replace(/\.html$/, "");
  const currentPage = normalizePage(location.pathname);

  let currentMenuKey = null;

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
    item.addEventListener("mouseenter", () => openMenu(item.dataset.menu));
  });

  sidebar.addEventListener("mouseleave", closeMenu);
}
