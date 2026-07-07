package com.hohoedu.book_clinic.clinic;

import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 학생 독서 클리닉(student-main 화면) 비즈니스 로직
 * - 레벨/EXP: 레벨별 required_exp는 "그 레벨에서 다음 레벨로 올라가는 데 필요한 누적 EXP"
 * - 추천: 활성 순위 초안을 순위대로 순회하며 기읽음/기추천/최근 분류/최근 장르를 스킵.
 *   전부 스킵되면 조건을 한 단계씩 완화(장르 무시 → 분류 무시)해서라도 한 권을 찾는다.
 */
@Service
@RequiredArgsConstructor
public class ClinicService {

    // TODO: erp_student.grade_key 체계 확인 후 학생 학년 → S코드 매핑으로 교체 (지금은 데모 학생 초5 고정)
    private static final String DEMO_SCHOOLYEAR = "05";

    private final ClinicRepository clinicRepository;

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

        Integer expPerBook = clinicRepository.findExpPerBook(DEMO_SCHOOLYEAR);
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

    /** 다음 우선순위 도서 추천 ("다른 도서 추천" 버튼에서도 사용) */
    @Transactional
    public ClinicRespDTO.RecommendBookDTO recommendNext(String studentId) {
        Integer picked = pickNextContentId(studentId);
        clinicRepository.insertRecommendLog(studentId, picked);
        return clinicRepository.findBookCard(picked);
    }

    /** 디버그용 — 후보 순서/제외 목록/최근 완독 분류·장르/최종 선택 결과를 그대로 노출 */
    public java.util.Map<String, Object> debugRecommend(String studentId) {
        String year = String.valueOf(Year.now().getValue());
        List<ClinicRespDTO.CandidateDTO> candidates = clinicRepository.findCandidates(year, DEMO_SCHOOLYEAR);
        Set<Integer> excluded = new HashSet<>();
        excluded.addAll(clinicRepository.findReadContentIds(studentId));
        excluded.addAll(clinicRepository.findRecommendedContentIds(studentId));
        ClinicRespDTO.LastReadDTO lastRead = clinicRepository.findLastRead(studentId);

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("year", year);
        result.put("schoolyear", DEMO_SCHOOLYEAR);
        result.put("candidateCount", candidates.size());
        result.put("candidates", candidates);
        result.put("excludedIds", excluded);
        result.put("lastReadType", lastRead == null ? null : lastRead.getContentType());
        result.put("lastReadGenre", lastRead == null ? null : lastRead.getGenre());
        result.put("picked", pickNextContentId(studentId));
        return result;
    }

    private Integer pickNextContentId(String studentId) {
        String year = String.valueOf(Year.now().getValue());
        List<ClinicRespDTO.CandidateDTO> candidates = clinicRepository.findCandidates(year, DEMO_SCHOOLYEAR);
        if (candidates.isEmpty()) throw new Exception404("적용 중인 권장도서 순위가 없습니다: " + year + "/" + DEMO_SCHOOLYEAR);

        Set<Integer> excluded = new HashSet<>();
        excluded.addAll(clinicRepository.findReadContentIds(studentId));
        excluded.addAll(clinicRepository.findRecommendedContentIds(studentId));

        ClinicRespDTO.LastReadDTO lastRead = clinicRepository.findLastRead(studentId);
        String lastType  = lastRead == null ? null : lastRead.getContentType();
        String lastGenre = lastRead == null ? null : lastRead.getGenre();

        // 1차: 분류·장르 모두 다른 책 → 2차: 장르 조건 완화 → 3차: 분류 조건도 완화(안 읽은 책이면 아무거나)
        Integer picked = pick(candidates, excluded, lastType, lastGenre);
        if (picked == null) picked = pick(candidates, excluded, lastType, null);
        if (picked == null) picked = pick(candidates, excluded, null, null);
        if (picked == null) throw new Exception404("추천할 수 있는 도서가 더 이상 없습니다.");
        return picked;
    }

    private Integer pick(List<ClinicRespDTO.CandidateDTO> candidates, Set<Integer> excluded,
                         String skipType, String skipGenre) {
        for (ClinicRespDTO.CandidateDTO c : candidates) {
            if (excluded.contains(c.getContentId())) continue;
            if (skipType != null && Objects.equals(c.getContentType(), skipType)) continue;
            if (skipGenre != null && Objects.equals(c.getGenre(), skipGenre)) continue;
            return c.getContentId();
        }
        return null;
    }
}
