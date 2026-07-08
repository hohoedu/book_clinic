package com.hohoedu.book_clinic.clinic;

import java.time.Year;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.book.BookRepository;
import com.hohoedu.book_clinic.book._dto.BookRespDTO;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 학생 독서 클리닉(student-main 화면) 비즈니스 로직
 * - 레벨/EXP: 레벨별 required_exp는 "그 레벨에서 다음 레벨로 올라가는 데 필요한 누적 EXP"
 * - 추천: 활성 순위 초안을 순위대로 스캔해 (문제풀이 미완료 · 소속 센터 대여 가능 · 직전 완독과
 *   분류/장르 다름) 조건을 모두 만족하는 첫 도서를 고른다. 이 스캔은 ClinicRepository.pickNextContentId
 *   단일 SQL 쿼리로 처리한다 (DB 왕복 1회로 순위 전체를 훑는 것과 동일한 결과).
 * - 추천 = 대여: 추천되는 순간 실물 재고 하나를 그 학생 앞으로 잡아둔다(loaned_qty +1 + item_loan
 *   기록). 그렇지 않으면 재고가 그대로 남아 있는 것처럼 보여 같은 책이 다른 학생에게도 추천된다.
 *   "다른 도서 추천"으로 책이 바뀔 때는 새 책을 먼저 확보한 뒤 이전에 잡아뒀던 hold를 반납한다.
 */
@Service
@RequiredArgsConstructor
public class ClinicService {

    // erp_student.grade_key가 비어있는 학생(아직 학년 미등록)을 위한 안전망 기본값
    private static final String FALLBACK_SCHOOLYEAR = "05";

    private final ClinicRepository clinicRepository;
    private final BookRepository bookRepository;

    /** 학생 현황 + 레벨 진행률 계산 */
    public ClinicRespDTO.StudentInfoDTO findStudentInfo(String studentId) {
        ClinicRespDTO.StudentInfoDTO info = clinicRepository.findStudentInfo(studentId);
        if (info == null) throw new Exception404("학생 독서 정보를 찾을 수 없습니다: " + studentId);

        // 현재 레벨 구간(이전 레벨 기준치 ~ 현재 레벨 기준치) 내 진행률과 남은 권수 계산
        List<ClinicRespDTO.LevelDTO> levels = clinicRepository.findAllLevels();
        int prevRequired = levels.stream()
                .filter(l -> l.getLevelNo() == info.getLevelNo() - 1)
                .map(ClinicRespDTO.LevelDTO::getRequiredExp)
                .findFirst().orElse(0);
        int span = Math.max(info.getRequiredExp() - prevRequired, 1);
        int gained = Math.max(info.getExp() - prevRequired, 0);
        info.setProgressPercent(Math.min(gained * 100 / span, 100));

        Integer expPerBook = clinicRepository.findExpPerBook(resolveSchoolyear(studentId));
        if (expPerBook != null && expPerBook > 0) {
            int remain = Math.max(info.getRequiredExp() - info.getExp(), 0);
            info.setBooksToNextLevel((remain + expPerBook - 1) / expPerBook);  // 올림
        }
        return info;
    }

    public List<ClinicRespDTO.BadgeDTO> findBadges(String studentId) {
        return clinicRepository.findBadges(studentId);
    }

    public List<ClinicRespDTO.MonthBookDTO> findMonthBooks(String studentId) {
        return clinicRepository.findMonthBooks(studentId);
    }

    /**
     * 오늘의 추천 도서
     * - 오늘 이미 추천된 책이 있으면 그대로 유지 (재접속 시 바뀌지 않도록)
     * - 없으면 다음 우선순위 도서를 골라 이력에 기록
     */
    @Transactional
    public ClinicRespDTO.RecommendBookDTO recommendBook(String studentId) {
        ClinicRespDTO.RecommendBookDTO today = clinicRepository.findTodayRecommend(studentId);
        if (today != null) return today;
        return recommendNext(studentId);
    }

    /**
     * 다음 우선순위 도서 추천 ("다른 도서 추천" 버튼에서도 사용)
     * 새 책의 실물 재고를 먼저 확보(대여)한 뒤에 이전 hold를 반납한다 — 순서를 반대로 하면
     * 새 책 확보에 실패했을 때(동시성 등) 이전 hold까지 잃어버리게 된다.
     */
    @Transactional
    public ClinicRespDTO.RecommendBookDTO recommendNext(String studentId) {
        Integer picked = pickNextContentId(studentId);
        String centerCode = clinicRepository.findCenterCode(studentId);

        String bcode = clinicRepository.pickAvailableBcode(picked, centerCode);
        if (bcode == null) throw new Exception404("대여 가능한 실물 도서가 없습니다.");
        int updated = bookRepository.incrementLoanedQty(bcode, centerCode);
        if (updated == 0) throw new Exception400("대여 가능한 재고가 없습니다.");

        releaseActiveLoan(studentId);
        bookRepository.insertItemLoan(bcode, centerCode, studentId);
        clinicRepository.insertRecommendLog(studentId, picked);
        return clinicRepository.findBookCard(picked);
    }

    /** 학생이 이전에 추천받아 잡아두고 있던 실물 재고(hold)를 반납 처리 (없으면 아무 것도 안 함) */
    private void releaseActiveLoan(String studentId) {
        BookRespDTO.ItemLoanRespDTO loan = bookRepository.findActiveLoanByStudent(studentId);
        if (loan == null) return;
        bookRepository.updateLoanReturned(loan.getLoanId());
        bookRepository.decrementLoanedQty(loan.getBcode(), loan.getCenterCode());
    }

    /** 디버그용 — 센터/학년/최근 완독 분류·장르/최종 선택 결과를 그대로 노출 */
    public java.util.Map<String, Object> debugRecommend(String studentId) {
        String year = String.valueOf(Year.now().getValue());
        String centerCode = clinicRepository.findCenterCode(studentId);
        String schoolyear = resolveSchoolyear(studentId);
        ClinicRespDTO.LastReadDTO lastRead = clinicRepository.findLastRead(studentId);

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("year", year);
        result.put("schoolyear", schoolyear);
        result.put("centerCode", centerCode);
        result.put("lastReadType", lastRead == null ? null : lastRead.getContentType());
        result.put("lastReadGenre", lastRead == null ? null : lastRead.getGenre());
        result.put("picked", pickNextContentId(studentId));
        return result;
    }

    private Integer pickNextContentId(String studentId) {
        String year = String.valueOf(Year.now().getValue());
        String centerCode = clinicRepository.findCenterCode(studentId);
        if (centerCode == null) throw new Exception404("학생의 소속 센터를 찾을 수 없습니다: " + studentId);
        String schoolyear = resolveSchoolyear(studentId);

        ClinicRespDTO.LastReadDTO lastRead = clinicRepository.findLastRead(studentId);
        String lastType  = lastRead == null ? null : lastRead.getContentType();
        String lastGenre = lastRead == null ? null : lastRead.getGenre();

        Integer picked = clinicRepository.pickNextContentId(studentId, centerCode, year, schoolyear, lastType, lastGenre);
        if (picked == null) throw new Exception404("추천할 수 있는 도서가 더 이상 없습니다.");
        return picked;
    }

    /** 학생의 실제 학년(grade_key)을 학년 기준 로직에 쓸 S코드로 변환 — 미등록 학생은 기본값으로 대체 */
    private String resolveSchoolyear(String studentId) {
        String gradeKey = clinicRepository.findGradeKey(studentId);
        return (gradeKey == null || gradeKey.isBlank()) ? FALLBACK_SCHOOLYEAR : gradeKey;
    }
}
