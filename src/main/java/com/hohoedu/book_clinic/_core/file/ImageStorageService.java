package com.hohoedu.book_clinic._core.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

/**
 * 도서 이미지 저장 서비스
 *
 * 가비아 이미지 호스팅(FTP) 접속정보가 설정되어 있으면 FTP로 업로드하고 공개 URL을 반환한다.
 * 접속정보가 비어 있으면(개발 환경 등) 서버 로컬 디스크에 저장하고 /uploads/** URL을 반환한다.
 *
 * 도서 ↔ 이미지 연결은 파일명이 아니라 DB image_url 값이 담당하므로 파일명은 UUID로 고유하게 둔다.
 *
 * 용도별로 원격 디렉터리를 나눈다(2026-09-02) — 표지는 master-book-dir, 수집 카드는 card-dir.
 * 파일명이 UUID라 한 폴더에 섞여도 충돌은 없지만, 카드만 따로 세거나 교체·정리하는 일이 생기므로
 * 처음부터 갈라둔다. 로컬 폴백도 같은 이유로 uploads/book, uploads/card로 나눈다.
 */
@Slf4j
@Service
public class ImageStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;
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

    /** 도서 표지 저장 후 접근 가능한 URL 반환 */
    public String store(MultipartFile file) throws IOException {
        return store(file, masterBookDir, "book");
    }

    /** 수집 카드 이미지 저장 후 접근 가능한 URL 반환 */
    public String storeCard(MultipartFile file) throws IOException {
        return store(file, cardDir, "card");
    }

    /**
     * remoteDir: 가비아 FTP 기준 디렉터리, localSubDir: 로컬 폴백 시 uploads 아래 하위 폴더.
     * 둘을 따로 받는 건 원격 경로가 환경변수(계정마다 다름)인 반면 로컬은 고정이기 때문이다.
     */
    private String store(MultipartFile file, String remoteDir, String localSubDir) throws IOException {
        String filename = UUID.randomUUID().toString().replace("-", "") + extractExtension(file.getOriginalFilename());
        return gabiaEnabled() ? storeToGabia(file, filename, remoteDir) : storeToLocal(file, filename, localSubDir);
    }

    /** 가비아 FTP 접속정보가 설정되어 있는지 */
    private boolean gabiaEnabled() {
        return isNotBlank(ftpServer);
    }

    /** 가비아 이미지 호스팅(FTP) 업로드 */
    private String storeToGabia(MultipartFile file, String filename, String remoteDir) throws IOException {
        FTPClient ftp = new FTPClient();
        try {
            ftp.connect(ftpServer, ftpPort);
            if (!ftp.login(ftpUsername, ftpPassword)) {
                throw new IOException("가비아 FTP 로그인 실패: " + ftp.getReplyString());
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            changeToDir(ftp, remoteDir);

            try (InputStream in = file.getInputStream()) {
                if (!ftp.storeFile(filename, in)) {
                    throw new IOException("가비아 FTP 업로드 실패: " + ftp.getReplyString());
                }
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

    /** 서버 로컬 디스크에 저장 */
    private String storeToLocal(MultipartFile file, String filename, String subDir) throws IOException {
        Path dir = Paths.get(uploadDir, subDir);
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(filename).toAbsolutePath());
        return "/uploads/" + subDir + "/" + filename;
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
