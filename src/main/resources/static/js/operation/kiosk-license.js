/* 기기 라이선스 — 본사가 센터 기기용 키를 발급/폐기하는 화면 (2026-08-20).

   키 원문은 발급 응답에만 실린다(서버는 해시만 저장). 그래서 발급 직후 화면에 한 번 보여주고,
   목록에는 두 번 다시 나오지 않는다. 잃어버리면 폐기 후 재발급이 정답이다. */
(function () {
  const CSRF_HEADER = "X-XSRF-TOKEN";

  function csrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : "";
  }

  async function api(url, options) {
    const res = await fetch(url, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        [CSRF_HEADER]: csrfToken(),
        ...(options && options.headers),
      },
    });
    const data = await res.json();
    if (!data.success) throw new Error(data.error?.message ?? "요청에 실패했습니다.");
    return data.response;
  }

  const centerSelect = document.getElementById("centerSelect");
  const filterSelect = document.getElementById("filterSelect");
  const labelInput = document.getElementById("labelInput");
  const issuedBox = document.getElementById("issuedBox");
  const issuedKey = document.getElementById("issuedKey");
  const issuedMeta = document.getElementById("issuedMeta");
  const licenseBody = document.getElementById("licenseBody");

  function formatDateTime(value) {
    if (!value) return "—";
    return value.replace("T", " ").slice(0, 16);
  }

  async function loadCenters() {
    const centers = await api("/admin/operation/kiosk/centers");
    // 값이 빈 문자열이면 전 센터 키 — 본사 테스트 태블릿 한 대로 어느 센터든 처리한다
    centerSelect.innerHTML =
      '<option value="">전 센터 (본사 테스트용)</option>' +
      centers.map((c) => `<option value="${c.centerCode}">${c.centerName}</option>`).join("");
    centerSelect.value = centers.length ? centers[0].centerCode : "";
    filterSelect.innerHTML =
      '<option value="">전체</option>' +
      centers.map((c) => `<option value="${c.centerCode}">${c.centerName}</option>`).join("");
  }

  async function loadLicenses() {
    const centerCode = filterSelect.value;
    const query = centerCode ? `?centerCode=${encodeURIComponent(centerCode)}` : "";
    const rows = await api(`/admin/operation/kiosk${query}`);

    if (!rows.length) {
      licenseBody.innerHTML = '<tr class="empty"><td colspan="5">발급된 키가 없습니다.</td></tr>';
      return;
    }

    licenseBody.innerHTML = rows
      .map((r) => {
        const limit = r.deviceLimit === 0 ? "무제한" : `${r.deviceLimit}대`;
        const count = r.deviceCount ?? 0;
        const full = r.deviceLimit > 0 && count >= r.deviceLimit;
        const allCenters = !r.centerCode;
        return `
        <tr>
          <td>${allCenters ? '<span class="all-centers">전 센터</span>' : r.centerName}</td>
          <td>${r.label}</td>
          <td>${formatDateTime(r.issuedAt)}<span class="by">${r.issuedBy ?? ""}</span></td>
          <td>
            <button type="button" class="link-btn" data-devices="${r.licenseId}">
              <span class="${full ? "count full" : "count"}">${count}</span> / ${limit}
            </button>
          </td>
          <td><button type="button" class="btn danger sm" data-revoke="${r.licenseId}" data-label="${r.label}">키 폐기</button></td>
        </tr>
        <tr class="device-row" id="devices-${r.licenseId}" hidden>
          <td colspan="5"><div class="device-list">불러오는 중…</div></td>
        </tr>`;
      })
      .join("");
  }

  document.getElementById("issueBtn").addEventListener("click", async () => {
    const centerCode = centerSelect.value;  // 빈 값 = 전 센터
    const label = labelInput.value.trim();
    const deviceLimit = Number(document.getElementById("deviceLimitInput").value);
    if (!label) return alert("기기 이름을 입력해주세요.");
    if (!centerCode && !confirm("전 센터 키는 어느 센터 학생이든 처리할 수 있습니다.\n본사 테스트용으로만 쓰세요. 발급할까요?")) return;
    if (!Number.isInteger(deviceLimit) || deviceLimit < 0) return alert("기기 수는 0 이상의 정수여야 합니다.");

    try {
      const issued = await api("/admin/operation/kiosk", {
        method: "POST",
        body: JSON.stringify({ centerCode, label, deviceLimit }),
      });
      issuedKey.textContent = issued.licenseKey;
      issuedMeta.textContent = `${centerSelect.options[centerSelect.selectedIndex].text} · ${issued.label} · ${deviceLimit === 0 ? "기기 무제한" : deviceLimit + "대"}`;
      issuedBox.hidden = false;
      labelInput.value = "";
      await loadLicenses();
    } catch (err) {
      alert(err.message);
    }
  });

  document.getElementById("copyBtn").addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(issuedKey.textContent);
      alert("복사했습니다.");
    } catch {
      alert("복사에 실패했습니다. 키를 직접 선택해 복사해주세요.");
    }
  });

  // 등록된 기기 목록 펼치기 — 한 키에 여러 대가 붙을 수 있어서 어디서 쓰는지 확인이 필요하다
  async function toggleDevices(licenseId) {
    const row = document.getElementById(`devices-${licenseId}`);
    if (!row.hidden) {
      row.hidden = true;
      return;
    }
    row.hidden = false;
    const box = row.querySelector(".device-list");
    try {
      const devices = await api(`/admin/operation/kiosk/${licenseId}/devices`);
      box.innerHTML = devices.length
        ? devices
            .map(
              (d) => `
              <div class="device-item">
                <div>
                  <span class="ua">${d.userAgent ?? "정보 없음"}</span>
                  <span class="meta">등록 ${formatDateTime(d.registeredAt)} · 마지막 사용 ${formatDateTime(d.lastUsedAt)}</span>
                </div>
                <button type="button" class="btn danger sm" data-revoke-device="${d.deviceId}">폐기</button>
              </div>`
            )
            .join("")
        : '<p class="device-empty">아직 등록된 기기가 없습니다.</p>';
    } catch (err) {
      box.textContent = err.message;
    }
  }

  // 폐기는 되돌릴 수 없고 해당 기기가 즉시 멈추므로 한 번 더 확인받는다
  licenseBody.addEventListener("click", async (event) => {
    const devicesBtn = event.target.closest("[data-devices]");
    if (devicesBtn) return toggleDevices(devicesBtn.dataset.devices);

    const deviceBtn = event.target.closest("[data-revoke-device]");
    if (deviceBtn) {
      if (!confirm("이 기기를 폐기할까요?\n해당 기기에서는 즉시 학생 로그인이 막힙니다.")) return;
      try {
        await api(`/admin/operation/kiosk/device/${deviceBtn.dataset.revokeDevice}`, { method: "DELETE" });
        await loadLicenses();
      } catch (err) {
        alert(err.message);
      }
      return;
    }

    const btn = event.target.closest("[data-revoke]");
    if (!btn) return;
    if (!confirm(`"${btn.dataset.label}" 키를 폐기할까요?\n이 키로 등록된 기기가 전부 막힙니다.`)) return;

    try {
      await api(`/admin/operation/kiosk/${btn.dataset.revoke}`, { method: "DELETE" });
      await loadLicenses();
    } catch (err) {
      alert(err.message);
    }
  });

  filterSelect.addEventListener("change", loadLicenses);

  (async function init() {
    try {
      await loadCenters();
      await loadLicenses();
    } catch (err) {
      licenseBody.innerHTML = `<tr class="empty"><td colspan="5">${err.message}</td></tr>`;
    }
  })();
})();
