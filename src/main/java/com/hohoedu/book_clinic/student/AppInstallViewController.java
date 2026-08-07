package com.hohoedu.book_clinic.student;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 학부모 앱(Flutter, 결제·이용권 조회용) 설치 랜딩 페이지.
 *
 * 학생 PWA(StudentViewController./student/app/install)와 다르다 — 이건 브라우저에 설치되는
 * 웹앱이 아니라 진짜 안드로이드 네이티브 앱이라, beforeinstallprompt를 못 쓰고 .apk 파일을
 * 그냥 내려받아 안드로이드가 직접 설치하게 하는 것 말고는 방법이 없다(스토어 미게시,
 * static/apk/books_pay.apk를 이 서버가 직접 서빙).
 */
@Controller
public class AppInstallViewController {

    @GetMapping("/app/install")
    public String getInstallPage() {
        return "/payment/app-install";
    }
}
