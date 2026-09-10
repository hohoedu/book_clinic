/* ============================================================
   회원가입(입회) 페이지 스크립트
   all_pass custom.js(openJusoPopup / jusoCallBack) + student.js(joinForm 검증·제출)
   에서 join 관련 로직만 추출해 이식.
   ============================================================ */

// ------------------------------------------------------------
// 주소 검색 팝업
// ------------------------------------------------------------
function openJusoPopup() {
    const width = 570;
    const height = 640;
    const left = (screen.width - width) / 2;
    const top = (screen.height - height) / 2;
    console.log("[팝업열기] 주소 검색 팝업을 엽니다.");
    window.open('/juso', 'jusoPopup', `width=${width}, height=${height}, top=${top}, left=${left}`);
}

// juso 팝업에서 부모창으로 값을 되돌려줄 때 호출 (juso-callback.html 에서도 직접 DOM 접근함)
function jusoCallBack(roadFullAddr, roadAddrPart1, addrDetail) {
    const addrInput = document.getElementById('address-input');
    if (addrInput) {
        addrInput.value = roadAddrPart1;
    }

    const detailInput = document.getElementById('address-detail-input');
    if (detailInput) {
        detailInput.value = addrDetail || '';
        detailInput.focus();
    }
}

// ------------------------------------------------------------
// 가입 폼 검증 + 제출
// ------------------------------------------------------------
document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("joinForm");
    if (!form) return;

    function isEmpty(value) {
        return !value || value.trim() === "";
    }

    function isValidBirth(birth) {
        if (!/^[0-9]{6}$/.test(birth)) {
            return { valid: false, msg: "생년월일은 숫자 6자리로 입력해 주세요." };
        }

        const yy = parseInt(birth.substring(0, 2));
        const month = parseInt(birth.substring(2, 4));
        const day = parseInt(birth.substring(4, 6));

        if (month < 1 || month > 12) {
            return { valid: false, msg: "생년월일이 올바르지 않습니다." };
        }

        if (day < 1) {
            return { valid: false, msg: "생년월일이 올바르지 않습니다." };
        }

        const currentYY = new Date().getFullYear() % 100;
        const fullYear = yy <= currentYY ? 2000 + yy : 1900 + yy;

        const lastDay = new Date(fullYear, month, 0).getDate();
        if (day > lastDay) {
            return { valid: false, msg: `${month}월은 ${lastDay}일까지만 있습니다.` };
        }

        const inputDate = new Date(fullYear, month - 1, day);
        if (inputDate > new Date()) {
            return { valid: false, msg: "미래 날짜는 입력할 수 없습니다." };
        }

        return { valid: true };
    }

    function isValidPhone(p1, p2, p3) {
        return p1.length === 3 && p2.length === 4 && p3.length === 4;
    }

    function isSignatureEmpty(canvas) {
        const blank = document.createElement("canvas");
        blank.width = canvas.width;
        blank.height = canvas.height;
        return canvas.toDataURL() === blank.toDataURL();
    }

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        const studentName = form.studentName.value;
        const birth = form.birth.value;
        const school = form.school.value;
        const gradeKey = form.gradeKey.value;
        const address = form.address.value;

        const parentName = form.parentName.value;
        const phone1 = form.parentTelFirst.value;
        const phone2 = form.parentTelMiddle.value;
        const phone3 = form.parentTelLast.value;
        const relationKey = form.relationKey.value;

        const privacyAgree = form.studentPrivacyAgree.checked;
        const parentAgree = form.parentPrivacyAgree.checked;

        const canvas = document.getElementById("signature-pad");

        // 학생 정보
        if (isEmpty(studentName)) {
            alert("학생 이름을 입력해 주세요.");
            form.studentName.focus();
            return;
        }

        const birthResult = isValidBirth(birth);
        if (!birthResult.valid) {
            alert(birthResult.msg);
            form.birth.focus();
            return;
        }

        if (isEmpty(school)) {
            alert("학교/유치원명을 입력해 주세요.");
            form.school.focus();
            return;
        }

        if (isEmpty(gradeKey)) {
            alert("연령/학년을 선택해 주세요.");
            form.gradeKey.focus();
            return;
        }

        if (isEmpty(address)) {
            alert("주소를 입력해 주세요.");
            form.address.focus();
            return;
        }

        if (!privacyAgree) {
            alert("학생 개인정보 수집 및 활용에 동의해 주세요.");
            return;
        }

        // 보호자 정보
        if (isEmpty(parentName)) {
            alert("법정대리인 성명을 입력해 주세요.");
            form.parentName.focus();
            return;
        }

        if (!isValidPhone(phone1, phone2, phone3)) {
            alert("법정대리인 연락처를 정확히 입력해 주세요.");
            form.parentTelFirst.focus();
            return;
        }

        if (isEmpty(relationKey)) {
            alert("법정대리인과의 관계를 선택해 주세요.");
            form.relationKey.focus();
            return;
        }

        if (!parentAgree) {
            alert("법정대리인 개인정보 수집 및 활용에 동의해 주세요.");
            return;
        }

        // 서명 체크
        if (isSignatureEmpty(canvas)) {
            alert("서명을 입력해 주세요.");
            return;
        }

        const formData = new FormData(form);

        let studentId = null;

        try {
            const joinResponse = await fetch(form.action, {
                method: "POST",
                body: formData
            });

            const result = await joinResponse.json();
            console.log(JSON.stringify(result, null, 2));

            if (!joinResponse.ok) {
                alert((result && (result.msg || (result.error && result.error.message))) || "가입 요청 중 오류가 발생했습니다.");
                return;
            }

            if (!result.success) {
                alert((result.msg || (result.error && result.error.message)) || "가입 처리 중 오류가 발생했습니다.");
                return;
            }

            studentId = result.response.studentId;
        } catch (err) {
            console.error("가입 요청 실패:", err);
            alert("가입 요청 중 오류가 발생했습니다.");
            return;
        }

        if (!studentId) {
            alert("가입 처리 중 오류가 발생했습니다. 다시 시도해 주세요.");
            return;
        }

        // 서명 PNG 변환 후 업로드
        const dataURL = canvas.toDataURL("image/png");
        const blob = await (await fetch(dataURL)).blob();
        console.log("서명 이미지 크기:", (blob.size / 1024).toFixed(2), "KB");

        const uploadForm = new FormData();
        uploadForm.append("file", blob, `${studentId}_signature.png`);
        uploadForm.append("studentId", studentId);

        try {
            const uploadResponse = await fetch("/student/upload/signature", {
                method: "POST",
                body: uploadForm
            });

            if (!uploadResponse.ok) {
                alert("서명 업로드 오류");
                return;
            }

            const uploadResult = await uploadResponse.json();

            if (!uploadResult.success) {
                alert("서명 업로드에 실패했습니다.");
                return;
            }
        } catch (err) {
            console.error("서명 업로드 실패:", err);
            alert("서명 업로드 중 오류가 발생했습니다.");
            return;
        }

        alert("가입이 완료되었습니다.");

        if (window.opener && !window.opener.closed) {
            window.opener.location.reload();
        }
        window.close();
    });
});
