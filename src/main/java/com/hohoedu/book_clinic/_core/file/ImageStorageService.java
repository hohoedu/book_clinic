package com.hohoedu.book_clinic._core.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;

import lombok.extern.slf4j.Slf4j;

/**
 * 도서 이미지 저장 서비스
 *
 * 가비아 이미지 호스팅(FTP)에 업로드하고 공개 URL을 반환한다.
 *
 * 로컬 디스크 폴백(/uploads/**)은 없앴다(2026-10-02). prod에 ftp 설정이 빠져 있던 동안 운영 업로드가
 * 조용히 서버 디스크에 쌓였다 — 접속정보가 비어 있으면 저장하지 않고 실패시켜 바로 드러나게 한다.
 *
 * 도서 이미지(표지·카드·워크시트)의 파일명은 content_id다(2026-10-02, 이전엔 UUID) — 호스팅 폴더만 보고도
 * 어떤 책인지 알 수 있어야 해서다. 다시 올리면 같은 이름으로 덮어쓰므로, 반환 URL 끝에 ?v=업로드시각을 붙여
 * 브라우저·CDN이 옛 그림을 계속 보여주지 않게 한다. 연결 자체는 여전히 DB에 저장된 URL 값이 담당한다.
 * 서명 이미지는 책과 무관해 UUID 그대로다.
 *
 * 용도별로 원격 디렉터리를 나눈다(2026-09-02) — 표지는 master-book-dir, 수집 카드는 card-dir.
 * 파일명이 UUID라 한 폴더에 섞여도 충돌은 없지만, 카드만 따로 세거나 교체·정리하는 일이 생기므로
 * 처음부터 갈라둔다.
 */
@Slf4j
@Service
public class ImageStorageService {

    @Value("${ftp.server:}")
    private String ftpServer;
    @Value("${ftp.port:21}")
    private int ftpPort;
    @Value("${ftp.username:}")
    private String ftpUsername;
    @Value("${ftp.password:}")
    private String ftpPassword;
    @Value("${ftp.master-book-dir:}")
    private String masterBookDir;
    /** 수집 카드 이미지 디렉터리 — 미설정이면 'cards' */
    @Value("${ftp.card-dir:cards}")
    private String cardDir;

    /** 입회 서명 이미지 디렉터리 — 미설정이면 'signatures' */
    @Value("${ftp.signature-dir:signatures}")
    private String signatureDir;

    /** 스페셜 카드 이미지 디렉터리 — 업로드 기능 없이 이미지 호스팅에 special_{학년}_{번호}.png로 직접 올려둔다(2026-10-01) */
    @Value("${ftp.special-card-dir:bookstore/special_cards}")
    private String specialCardDir;

    /**
     * 스페셜 카드 이미지 호스트 — ftp.server가 없으면 secrets의 GABIA_FTP_HOST를 직접 쓴다.
     * ftp: 블록이 dev 프로필에만 있어 prod에선 ftp.server가 비는데, 업로드 경로(로컬 폴백)는 그대로 두고
     * 이미 호스팅에 올라가 있는 스페셜 카드 주소만 만들 수 있게 따로 읽는다.
     */
    @Value("${ftp.server:${GABIA_FTP_HOST:}}")
    private String specialCardHost;

    /** 학년별 스페셜 카드 그림 수 — 그 학년 11번째 스페셜 카드부터는 01부터 다시 돈다 */
    private static final int SPECIAL_CARD_IMAGE_COUNT = 10;

    /**
     * 스페셜 카드 이미지 URL — special_{학년 2자리}_{번호 2자리}.png.
     * 학년 = 그 카드를 받은 때의 학생 학년, 번호 = 그 학년에서 몇 번째 스페셜 카드인지(1부터,
     * SpecialCardPolicy.ordinal). 호스트나 학년을 모르면 null(호출부가 기본 이미지 유지).
     */
    public String specialCardUrl(String schoolyear, int ordinal) {
        if (!isNotBlank(specialCardHost) || !isNotBlank(schoolyear) || ordinal < 1) return null;
        String grade = schoolyear.trim();
        if (grade.length() == 1) grade = "0" + grade;
        int seq = (ordinal - 1) % SPECIAL_CARD_IMAGE_COUNT + 1;
        String path = normalizeDir(specialCardDir);
        return "https://" + specialCardHost + (path.isEmpty() ? "" : "/" + path)
                + String.format("/special_%s_%02d.png", grade, seq);
    }

    /**
     * 도서 표지 저장 후 접근 가능한 URL 반환 — 파일명 {contentId 3자리}.확장자(예: 001.jpg).
     * 지점 하위도서(item) 표지는 마스터 표지를 덮어쓰면 안 되므로 {contentId}_{centerCode}.확장자로 둔다.
     */
    public String store(MultipartFile file, int contentId, String centerCode) throws IOException {
        String baseName = isNotBlank(centerCode) ? fileBase(contentId) + "_" + centerCode.trim() : fileBase(contentId);
        return store(file, masterBookDir, baseName, extractExtension(file.getOriginalFilename()));
    }

    /** 파일명용 content_id — 3자리 0 채움(1 → 001, 11 → 011). 1000부터는 그대로(1234) */
    private String fileBase(int contentId) {
        return String.format("%03d", contentId);
    }

    /** 수집 카드 이미지 저장 후 접근 가능한 URL 반환 — 파일명 {contentId}.확장자 */
    public String storeCard(MultipartFile file, int contentId) throws IOException {
        return store(file, cardDir, fileBase(contentId), extractExtension(file.getOriginalFilename()));
    }

    /** 입회 서명 이미지 저장 후 접근 가능한 URL 반환 (2026-09-10, 회원가입 이식) */
    public String storeSignature(MultipartFile file) throws IOException {
        return store(file, signatureDir, UUID.randomUUID().toString().replace("-", ""),
                extractExtension(file.getOriginalFilename()));
    }

    /**
     * remoteDir: 가비아 FTP 기준 디렉터리, baseName: 확장자를 뺀 파일명.
     * 같은 이름이 있으면 덮어쓴다(FTP STOR) — 반환 URL의 ?v=가 매번 달라 캐시된 옛 그림이 보이지 않는다.
     */
    private String store(MultipartFile file, String remoteDir, String baseName, String extension)
            throws IOException {
        if (!isNotBlank(ftpServer)) {
            throw new IOException("가비아 FTP 접속정보(ftp.server)가 설정되지 않았습니다.");
        }
        try (InputStream in = file.getInputStream()) {
            return storeToGabia(in, baseName + extension, remoteDir) + "?v=" + System.currentTimeMillis();
        }
    }

    /** 가비아 이미지 호스팅(FTP) 업로드 — 테스트가 FTP 없이 저장 결과를 보도록 패키지 범위로 둔다 */
    String storeToGabia(InputStream in, String filename, String remoteDir) throws IOException {
        FTPClient ftp = new FTPClient();
        try {
            ftp.connect(ftpServer, ftpPort);
            if (!ftp.login(ftpUsername, ftpPassword)) {
                throw new IOException("가비아 FTP 로그인 실패: " + ftp.getReplyString());
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            changeToDir(ftp, remoteDir);

            if (!ftp.storeFile(filename, in)) {
                throw new IOException("가비아 FTP 업로드 실패: " + ftp.getReplyString());
            }
        } finally {
            disconnectQuietly(ftp);
        }
        // https로 내려준다(2026-09-02) — 학생 PWA가 https로 서비스되므로 http 주소를 쓰면 브라우저가
        // 혼합 콘텐츠로 차단해 이미지가 통째로 안 뜬다. 가비아 이미지 호스팅은 https를 지원한다.
        String path = normalizeDir(remoteDir);
        return "https://" + ftpServer + (path.isEmpty() ? "" : "/" + path) + "/" + filename;
    }

    private void changeToDir(FTPClient ftp, String dir) throws IOException {
        if (!isNotBlank(dir)) return;
        for (String segment : dir.split("/")) {
            if (segment.isBlank()) continue;
            if (!ftp.changeWorkingDirectory(segment)) {
                ftp.makeDirectory(segment); 
                if (!ftp.changeWorkingDirectory(segment)) {
                    throw new IOException("원격 디렉터리 이동 실패: " + segment);
                }
            }
        }
    }

    /** 앞뒤 슬래시 제거 */
    private String normalizeDir(String dir) {
        return isNotBlank(dir) ? dir.replaceAll("^/+", "").replaceAll("/+$", "") : "";
    }

    /** 허용 확장자만 통과, 그 외는 확장자 제거 */
    private String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0) return "";
        String ext = originalFilename.substring(dot).toLowerCase();
        return ext.matches("\\.(png|jpg|jpeg|gif|webp)") ? ext : "";
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void disconnectQuietly(FTPClient ftp) {
        try {
            if (ftp.isConnected()) {
                ftp.logout();
                ftp.disconnect();
            }
        } catch (IOException e) {
            log.warn("FTP 연결 종료 중 오류", e);
        }
    }
}
