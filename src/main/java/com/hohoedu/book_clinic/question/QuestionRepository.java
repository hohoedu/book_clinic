package com.hohoedu.book_clinic.question;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.question._dto.QuestionReqDTO;
import com.hohoedu.book_clinic.question._dto.QuestionRespDTO;

/**
 * 문제 MyBatis 매퍼 인터페이스
 * QuestionMapper.xml과 매핑되어 DB 쿼리를 실행
 */
@Mapper
public interface QuestionRepository {

    /** 문제 등록 */
    void registerQuestion(QuestionReqDTO.RegisterReqDTO reqDTO);

    /** 문제 일괄 등록 (엑셀 업로드용) */
    void registerQuestionBatch(@Param("list") List<QuestionReqDTO.RegisterReqDTO> list);

    /** 문제 수정 */
    void updateQuestion(QuestionReqDTO.UpdateReqDTO reqDTO);

    /** 문제 변경 전 스냅샷을 itempool_del에 기록 (logType: DELETE=삭제, UPDATE=수정) */
    void archiveQuestion(@Param("contentId") Integer contentId, @Param("qlevel") String qlevel, @Param("qnum") String qnum, @Param("loggedBy") String loggedBy, @Param("logType") String logType);

    /** 문제 삭제 */
    void deleteQuestion(@Param("contentId") Integer contentId, @Param("qlevel") String qlevel, @Param("qnum") String qnum);

    /** itempool_del에서 itempool로 복사 복원 (로그는 보존) */
    void restoreQuestionFromDel(@Param("delId") Integer delId);

    /** 도서별 문제 목록 조회 (contentId 필수, qlevel/qtype/state 선택) */
    List<QuestionRespDTO.QuestionDTO> searchQuestions(@Param("contentId") Integer contentId, @Param("qlevel") String qlevel, @Param("qtype") String qtype, @Param("state") String state);

    /** 삭제된 문제 목록 조회 (contentId 기준) */
    List<QuestionRespDTO.QuestionDelDTO> findDeletedQuestions(@Param("contentId") Integer contentId);

    /** 등록 대상 목록 중 이미 존재하는 문제 조회 (중복 체크용) */
    List<QuestionRespDTO.DuplicateInfo> findExistingQuestions(@Param("list") List<QuestionReqDTO.RegisterReqDTO> list);

    /** 문제 전체 내용 업데이트 (덮어쓰기용) */
    void overwriteQuestion(QuestionReqDTO.RegisterReqDTO reqDTO);

}
