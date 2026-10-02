package com.hohoedu.book_clinic._core.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.UUID;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
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

    /** 워크시트(출력용) 이미지 디렉터리 — 미설정이면 'worksheets' (2026-09-14) */
    @Value("${ftp.worksheet-dir:worksheets}")
    private String worksheetDir;

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
     * 스페셜(RARE) 카드 이미지 URL — special_{학년 2자리}_{번호 2자리}.png.
     * 학년 = 그 스페셜 카드를 터뜨린(10·20…번째) 책의 학년, 번호 = triggerCount(발급 시점 누적 NORMAL 카드 수
     * 10, 20, ...)로 정한 그 학생의 몇 번째 스페셜 카드인지. 호스트나 학년을 모르면 null(호출부가 기본 이미지 유지).
     */
    public String specialCardUrl(String schoolyear, int triggerCount, int cardSetSize) {
        if (!isNotBlank(specialCardHost) || !isNotBlank(schoolyear) || triggerCount < cardSetSize) return null;
        String grade = schoolyear.trim();
        if (grade.length() == 1) grade = "0" + grade;
        int seq = (triggerCount / cardSetSize - 1) % SPECIAL_CARD_IMAGE_COUNT + 1;
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

    /**
     * 워크시트 저장 후 접근 가능한 URL 반환 (2026-09-14).
     * 여기서 돌려주는 호스팅 주소는 DB(erp_bookstore_card_path.worksheet_url)에만 남고 브라우저로는
     * 내려가지 않는다 — 출력은 /admin/monitor/worksheet/print 가 만들어 주는 PDF로만 나간다.
     *
     * PDF는 변환 없이 원본 그대로 둔다(2026-10-02 재변경). 한때 PNG로 구워 저장했는데, 래스터로
     * 굳히는 순간 인쇄물의 글자가 눈에 띄게 뭉개졌다 — 출력도 PDF로 하면 원본 벡터가 그대로
     * 프린터 해상도로 찍힌다. 이미지로 올린 워크시트는 그대로 두고, 출력할 때 PDF 한 장에 얹는다.
     */
    public String storeWorksheet(MultipartFile file, int contentId) throws IOException {
        if (isPdf(file)) {
            validatePdf(file);
            return store(file, worksheetDir, fileBase(contentId), ".pdf");
        }
        return store(file, worksheetDir, fileBase(contentId), extractExtension(file.getOriginalFilename()));
    }

    /** 업로드된 파일이 PDF인지 — content-type이 비거나 엉뚱하게 오는 브라우저가 있어 확장자도 본다 */
    public boolean isPdf(MultipartFile file) {
        if (file == null) return false;
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("application/pdf")) return true;
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".pdf");
    }

    /** 저장된 주소가 PDF인지 — 출력할 때 페이지를 가져다 쓸지, 그림으로 얹을지 가른다 */
    public boolean isPdfUrl(String storedUrl) {
        return stripQuery(storedUrl).endsWith(".pdf");
    }

    /** ?v=... 를 떼고 소문자로 — 확장자 판별용 */
    private String stripQuery(String storedUrl) {
        if (storedUrl == null) return "";
        int q = storedUrl.indexOf('?');
        return (q < 0 ? storedUrl : storedUrl.substring(0, q)).toLowerCase();
    }

    /**
     * 열리는 PDF인지만 확인하고 버린다 (2026-10-02).
     *
     * 변환해서 저장하지 않으니 업로드 시점에 내용을 볼 일이 없지만, 암호가 걸렸거나 깨진 파일을
     * 그대로 받아두면 몇 주 뒤 수업 직전 출력에서야 터진다. 그때는 등록한 사람도 자리에 없다.
     * 그래서 올리는 순간 한 번 열어보고 안 열리면 등록 자체를 막는다.
     */
    private void validatePdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.getNumberOfPages() == 0) {
                throw new Exception400("PDF에 페이지가 없습니다.");
            }
            if (document.getNumberOfPages() > 1) {
                log.warn("워크시트 PDF가 {}장입니다 — 출력은 첫 페이지만 나갑니다. (파일명: {})",
                        document.getNumberOfPages(), file.getOriginalFilename());
            }
        } catch (Exception400 e) {
            throw e;
        } catch (IOException e) {
            log.warn("워크시트 PDF 열기 실패 (파일명: {})", file.getOriginalFilename(), e);
            throw new Exception400("PDF를 열지 못했습니다. 암호가 걸려 있거나 손상된 파일일 수 있습니다.");
        }
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

    /**
     * 저장된 이미지를 서버가 직접 읽어 바이트로 돌려준다 (2026-09-14, 워크시트 프록시용).
     *
     * 워크시트는 호스팅 주소를 브라우저에 노출하지 않는 것이 핵심이라, 화면은 원본 URL 대신
     * /admin/monitor/worksheet/{contentId}만 받는다. 그 엔드포인트가 이 메서드로 원본을 가져온다.
     */
    public byte[] read(String storedUrl) throws IOException {
        if (!isNotBlank(storedUrl)) throw new IOException("이미지 주소가 비어 있습니다.");

        URLConnection conn = URI.create(storedUrl).toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** 저장 주소의 확장자로 추정한 MIME 타입 — 알 수 없으면 image/jpeg */
    public String contentTypeOf(String storedUrl) {
        String url = stripQuery(storedUrl);
        if (url.endsWith(".pdf")) return "application/pdf";
        if (url.endsWith(".png")) return "image/png";
        if (url.endsWith(".gif")) return "image/gif";
        if (url.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
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
