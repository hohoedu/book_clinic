package com.hohoedu.book_clinic.common.code;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.common.code._dto.CodeReqDTO;

import lombok.RequiredArgsConstructor;

/**
 * 공통 코드 관리 API 컨트롤러
 * 도서, 문제 등 여러 도메인에서 공통으로 사용하는 코드 테이블(erp_bookstore_code) 관리
 */
@RestController
@RequestMapping("/code")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    /** 공통 코드 등록 */
    @PostMapping("/register")
    public ResponseEntity<?> registerCode(@RequestBody CodeReqDTO.RegisterReqDTO reqDTO) {
        codeService.registerCode(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("등록되었습니다."));
    }

    /** 공통 코드 수정 (코드명, 정렬순서, 사용여부) */
    @PutMapping("/update")
    public ResponseEntity<?> updateCode(@RequestBody CodeReqDTO.UpdateReqDTO reqDTO) {
        codeService.updateCode(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("수정되었습니다."));
    }

    /** 공통 코드 삭제 */
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteCode(@RequestBody CodeReqDTO.DeleteReqDTO reqDTO) {
        codeService.deleteCode(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("삭제되었습니다."));
    }

    /** 특정 그룹 코드 목록 조회 (정렬순서 오름차순) */
    @GetMapping("/list/{groupCode}")
    public ResponseEntity<?> findByGroupCode(@PathVariable("groupCode") String groupCode) {
        return ResponseEntity.ok(ApiUtils.success(codeService.findByGroupCode(groupCode)));
    }

    /** 전체 공통 코드 목록 조회 (그룹코드, 정렬순서 오름차순) */
    @GetMapping("/list")
    public ResponseEntity<?> findAll() {
        return ResponseEntity.ok(ApiUtils.success(codeService.findAll()));
    }

}
