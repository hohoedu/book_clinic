package com.hohoedu.book_clinic.student;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hohoedu.book_clinic._core.file.ImageStorageService;
import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception500;
import com.hohoedu.book_clinic._core.utils.HashUtils;
import com.hohoedu.book_clinic.student._dto.StudentJoinReqDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원가입(입회) 저장 — all_pass StudentService.studentInsert 이식 (2026-09-10).
 *
 * all_pass 대비 축소한 부분(book_clinic 데이터 모델에 없거나 설계상 다른 것):
 *  - erp_pending_student / erp_teacher_assign : book_clinic 엔 사전등록·교사배정 개념이 없어 제외
 *  - 형제(erp_student_sibling) 자동 그룹핑 : book_clinic 은 형제 등록을 "사람이 직접" 하는 설계라
 *    (schema.sql erp_student_sibling 주석) 전화번호 기반 자동 매칭은 이식하지 않는다
 *  - invite 코드로 pending 학생을 불러와 폼을 채우는 흐름 : 위와 같은 이유로 보류
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentJoinService {

    private final StudentRepository studentRepository;
    private final ImageStorageService imageStorageService;

    /** @return 채번된 studentId */
    @Transactional
    public String join(StudentJoinReqDTO dto) {
        if (dto.getCenterCode() == null || dto.getCenterCode().isBlank()) {
            throw new Exception400("센터 코드가 없습니다.");
        }
        if (studentRepository.countDuplicateOnJoin(
                dto.getStudentName(), dto.getParentTelMiddle(), dto.getParentTelLast()) > 0) {
            throw new Exception400("이미 등록된 학생입니다.");
        }

        String studentId = generateStudentId(dto.getCenterCode());
        String birth = convertBirthToFullDate(dto.getBirth());
        boolean gender = Boolean.parseBoolean(dto.getGender());

        String billingPhone = nvl(dto.getParentTelFirst()) + nvl(dto.getParentTelMiddle()) + nvl(dto.getParentTelLast());
        String appIdPrefix = nvl(dto.getParentTelMiddle()) + nvl(dto.getParentTelLast());
        Integer maxSuffix = studentRepository.findMaxAppIdSuffix(appIdPrefix);
        String appId = appIdPrefix + (maxSuffix == null ? 0 : maxSuffix + 1);
        String appPassword = HashUtils.hashPassword(nvl(dto.getParentTelLast()), null);

        boolean subHoho = false, subHan = false, subBook = false;
        switch (nvl(dto.getSubject())) {
            case "hoho" -> subHoho = true;
            case "han"  -> { subHan = true; subBook = true; }
            case "book" -> subBook = true;
            default -> log.warn("[입회] 알 수 없는 수업 과목: {}", dto.getSubject());
        }

        studentRepository.insertOnJoin(dto, studentId, birth, gender, appId, appPassword,
                billingPhone, "ACTIVE", subHan, subBook, subHoho);
        studentRepository.insertGuardianOnJoin(dto, studentId);

        log.info("[입회] 신규 학생 등록 studentId={}, center={}, appId={}", studentId, dto.getCenterCode(), appId);
        return studentId;
    }

    /** 가입 직후 서명 PNG 업로드 → 보호자 행에 URL 저장 */
    @Transactional
    public String saveSignature(String studentId, MultipartFile file) {
        if (studentId == null || studentId.isBlank()) {
            throw new Exception400("studentId 가 없습니다.");
        }
        if (file == null || file.isEmpty()) {
            throw new Exception400("서명 이미지가 비어 있습니다.");
        }
        String url;
        try {
            url = imageStorageService.storeSignature(file);
        } catch (IOException e) {
            throw new Exception500("서명 이미지 저장에 실패했습니다.");
        }
        studentRepository.updateGuardianSignature(studentId, url);
        return url;
    }

    // ------------------------------------------------------------

    /** centerCode + yyMMdd + UUID 끝 5자리(대문자) — all_pass 와 동일 규칙 */
    private String generateStudentId(String centerCode) {
        String random = UUID.randomUUID().toString().replace("-", "");
        String last5 = random.substring(random.length() - 5).toUpperCase();
        return centerCode + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + last5;
    }

    /** YYMMDD → YYYY-MM-DD (현재 연도 기준 세기 판정) — all_pass convertBirthToFullDate 이식 */
    private String convertBirthToFullDate(String birth) {
        if (birth == null || birth.length() != 6) {
            return null;
        }
        try {
            int yy = Integer.parseInt(birth.substring(0, 2));
            int mm = Integer.parseInt(birth.substring(2, 4));
            int dd = Integer.parseInt(birth.substring(4, 6));
            int currentYY = LocalDate.now().getYear() % 100;
            int fullYear = (yy > currentYY) ? 1900 + yy : 2000 + yy;
            return String.format("%04d-%02d-%02d", fullYear, mm, dd);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
