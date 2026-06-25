package com.hohoedu.book_clinic.common.code;

import java.util.List;

import org.springframework.stereotype.Service;

import com.hohoedu.book_clinic.common.code._dto.CodeReqDTO;
import com.hohoedu.book_clinic.common.code._dto.CodeRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 공통 코드 비즈니스 로직 서비스
 * 장르(G), 학년(S), 수록구분(C), 유형(T) 등 도메인 전반에서 사용하는 코드 관리
 */
@Service
@RequiredArgsConstructor
public class CodeService {

    private final CodeRepository codeRepository;

    /** 공통 코드 등록 */
    public void registerCode(CodeReqDTO.RegisterReqDTO reqDTO) {
        codeRepository.registerCode(reqDTO);
    }

    /** 공통 코드 수정 */
    public void updateCode(CodeReqDTO.UpdateReqDTO reqDTO) {
        codeRepository.updateCode(reqDTO);
    }

    /** 공통 코드 삭제 */
    public void deleteCode(CodeReqDTO.DeleteReqDTO reqDTO) {
        codeRepository.deleteCode(reqDTO.getCodeId());
    }

    /** 특정 그룹 코드 목록 조회 */
    public List<CodeRespDTO.CodeDTO> findByGroupCode(String groupCode) {
        return codeRepository.findByGroupCode(groupCode);
    }

    /** 전체 공통 코드 목록 조회 */
    public List<CodeRespDTO.CodeDTO> findAll() {
        return codeRepository.findAll();
    }

}
