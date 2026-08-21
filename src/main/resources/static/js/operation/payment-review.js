document.addEventListener("DOMContentLoaded", async () => {
  await loadReviewList();
});

const CSRF_HEADER = "X-XSRF-TOKEN";
function getCsrfToken() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}

async function getJson(url) {
  const response = await fetch(url);
  const data = await response.json();
  if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");
  return data.response;
}

async function postJson(url) {
  const response = await fetch(url, {
    method: "POST",
    headers: { [CSRF_HEADER]: getCsrfToken() },
  });
  const data = await response.json();
  if (!data.success) throw new Error(data.error?.message ?? "요청 처리에 실패했습니다.");
  return data.response;
}

async function loadReviewList() {
  try {
    const list = await getJson("/admin/payment/review/list");
    renderReviewList(list);
  } catch (e) {
    console.error("결제 이상 건 조회 실패", e);
    alert(e.message);
  }
}

function formatDateTime(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

function renderReviewList(list) {
  const body = document.getElementById("reviewListBody");
  const empty = document.getElementById("reviewEmpty");
  body.innerHTML = "";

  if (list.length === 0) {
    empty.hidden = false;
    return;
  }
  empty.hidden = true;

  list.forEach((row) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${formatDateTime(row.requestedAt)}</td>
      <td>${row.orderNo}${row.groupOrderNo ? `<br><small>그룹: ${row.groupOrderNo}</small>` : ""}</td>
      <td>${row.studentId ?? "-"}</td>
      <td>${row.centerCode ?? "-"}</td>
      <td>${row.productName ?? "-"}</td>
      <td>${row.amount != null ? row.amount.toLocaleString() + "원" : "-"}</td>
      <td>${row.status ?? "-"}</td>
      <td class="review-reason">${row.reviewReason ?? "-"}</td>
      <td><button type="button" class="btn outline small btn-resolve">처리 완료</button></td>
    `;
    tr.querySelector(".btn-resolve").addEventListener("click", () => resolveReview(row.paymentId));
    body.appendChild(tr);
  });
}

async function resolveReview(paymentId) {
  if (!confirm("이니시스 상점관리자에서 실제 확인·조치를 마치셨나요? 목록에서 제거됩니다.")) return;

  try {
    await postJson(`/admin/payment/review/${paymentId}/resolve`);
    await loadReviewList();
  } catch (e) {
    alert(e.message);
  }
}
