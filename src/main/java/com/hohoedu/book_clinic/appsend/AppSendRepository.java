package com.hohoedu.book_clinic.appsend;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.appsend._dto.AppSendRespDTO;

/** 독서 결과 발송 조회 MyBatis 매퍼 인터페이스 (2026-09-22) */
@Mapper
public interface AppSendRepository {

    /** 그날 그 센터의 회차 전체 — 그 회차에 학생이 없어도 드롭다운은 채워야 한다 */
    List<AppSendRespDTO.SlotDTO> findSendSlots(@Param("date") LocalDate date,
                                               @Param("centerCode") String centerCode);

    /** 발송 처리 대상 — 아직 미발송이고 이 센터 학생인 일지만 (푸시 문구에 쓸 학생명 포함) */
    List<AppSendRespDTO.SendTargetDTO> findSendTargets(@Param("diaryKeys") List<Integer> diaryKeys,
                                                       @Param("centerCode") String centerCode);

    /**
     * 발송 처리 — 일지의 is_send 를 0 → 1 로 올린다. 이미 발송된 건은 건드리지 않는다.
     *
     * @return 실제로 바뀐 행 수 (이미 발송됐거나 다른 센터 학생이면 세지 않는다)
     */
    int updateSendFlag(@Param("diaryKeys") List<Integer> diaryKeys,
                       @Param("centerCode") String centerCode);

    /** 그 학생이 이 센터 소속인지 — 미리보기 조회를 센터 밖 학생으로 넓히지 않기 위한 확인 */
    boolean existsStudentInCenter(@Param("studentId") String studentId,
                                  @Param("centerCode") String centerCode);

    /** 예약을 루트로 둔 발송 목록 (발송 상태는 일지 헤더에서 붙는다) */
    List<AppSendRespDTO.RowDTO> findSendRows(@Param("date") LocalDate date,
                                             @Param("centerCode") String centerCode,
                                             @Param("slotSeq") Integer slotSeq,
                                             @Param("keyword") String keyword);
}
