package com.hohoedu.book_clinic._core.view;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.common.code.CodeService;
import com.hohoedu.book_clinic.student.StudentJoinService;
import com.hohoedu.book_clinic.student._dto.StudentJoinReqDTO;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 회원가입(입회) 페이지 — all_pass 에서 이식 (2026-09-10).
 *
 * 이식 범위: 프론트(join.html / join.css / join.js / signature.js), 화면 라우트,
 * 주소검색 팝업(/juso, /jusoCallBack), 그리고 저장(POST /student/join,
 * POST /student/upload/signature). 저장 로직은 {@link StudentJoinService} 참고 —
 * all_pass 대비 사전등록/교사배정/형제 자동그룹핑은 book_clinic 설계에 맞춰 제외했다.
 */
@Controller
@RequiredArgsConstructor
public class JoinViewController {

    private final CodeService codeService;
    private final StudentJoinService studentJoinService;

    /** all_pass 에는 relation_code 테이블이 있었으나 book_clinic 엔 없어 고정 목록으로 대체 */
    private static final List<Map<String, String>> RELATION_CODES = List.of(
            Map.of("key", "MO", "name", "모"),
            Map.of("key", "FA", "name", "부"),
            Map.of("key", "GM", "name", "조모"),
            Map.of("key", "GF", "name", "조부"),
            Map.of("key", "ETC", "name", "기타"));

    // ------------------------------------------------------------
    // 화면
    // ------------------------------------------------------------

    /** 직원용 입회 등록 화면 (로그인 세션의 센터로 고정) */
    @GetMapping("/student/web/join")
    public String getStudentJoinPage(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("centerCode",
                userDetails != null && userDetails.getLoginUser() != null
                        ? userDetails.getLoginUser().getCenterCode()
                        : null);
        model.addAttribute("gradeCodes", codeService.findBookstoreCodes("S"));
        model.addAttribute("relationCodes", RELATION_CODES);
        return "student/join";
    }

    /** 학부모용 입회 등록 화면 (문자로 전달된 centerCode / invite 코드로 진입) */
    @GetMapping("/signup")
    public String getStudentJoinPageByParent(
            @RequestParam(value = "centerCode") String centerCode,
            @RequestParam(value = "invite", required = false) String invite,
            Model model) {
        model.addAttribute("centerCode", centerCode);
        model.addAttribute("inviteCode", invite);
        // TODO: invite 코드로 사전등록(pending) 학생을 조회해 폼을 채우던 로직 이식 필요
        model.addAttribute("pendingStudent", null);
        model.addAttribute("gradeCodes", codeService.findBookstoreCodes("S"));
        model.addAttribute("relationCodes", RELATION_CODES);
        return "student/join";
    }

    // ------------------------------------------------------------
    // 주소 검색 팝업 (도로명주소 API)
    // ------------------------------------------------------------

    @GetMapping("/juso")
    public String jusoPopup(HttpServletRequest request, Model model) {
        // juso.go.kr 이 검색 결과를 POST 로 되돌려줄 주소 — 현재 요청의 스킴+호스트에서 구성한다
        StringBuilder base = new StringBuilder()
                .append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        if (port != 80 && port != 443) {
            base.append(':').append(port);
        }
        model.addAttribute("jusoReturnUrl", base.append("/juso").toString());
        return "juso";
    }

    /** juso.go.kr 이 결과를 POST 로 되돌려주므로, 파라미터를 담아 GET 콜백으로 리다이렉트 */
    @PostMapping("/juso")
    public String jusoReturn(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("roadFullAddr", request.getParameter("roadFullAddr"));
        redirectAttributes.addAttribute("roadAddrPart1", request.getParameter("roadAddrPart1"));
        redirectAttributes.addAttribute("roadAddrPart2", request.getParameter("roadAddrPart2"));
        redirectAttributes.addAttribute("addrDetail", request.getParameter("addrDetail"));
        return "redirect:/jusoCallBack";
    }

    @GetMapping("/jusoCallBack")
    public String jusoCallback(
            @RequestParam(name = "roadFullAddr", required = false) String roadFullAddr,
            @RequestParam(name = "roadAddrPart1", required = false) String roadAddrPart1,
            @RequestParam(name = "roadAddrPart2", required = false) String roadAddrPart2,
            @RequestParam(name = "addrDetail", required = false) String addrDetail,
            Model model) {
        String roadAddrPart = (roadAddrPart1 == null ? "" : roadAddrPart1)
                + " " + (roadAddrPart2 == null ? "" : roadAddrPart2);
        model.addAttribute("roadFullAddr", roadFullAddr);
        model.addAttribute("roadAddrPart", roadAddrPart);
        model.addAttribute("addrDetail", addrDetail);
        return "juso-callback";
    }

    // ------------------------------------------------------------
    // 저장
    // ------------------------------------------------------------

    /** 입회 폼 제출 — 학생 + 보호자 저장 후 studentId 반환 (서명은 이어서 별도 업로드) */
    @PostMapping("/student/join")
    @ResponseBody
    public ResponseEntity<?> studentJoin(@ModelAttribute StudentJoinReqDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 직원용(web) 진입은 hidden centerCode 가 비어 올 수 있어 세션 센터로 보완
        if ((dto.getCenterCode() == null || dto.getCenterCode().isBlank())
                && userDetails != null && userDetails.getLoginUser() != null) {
            dto.setCenterCode(userDetails.getLoginUser().getCenterCode());
        }
        String studentId = studentJoinService.join(dto);
        return ResponseEntity.ok(ApiUtils.success(Map.of("studentId", studentId)));
    }

    /** 가입 직후 서명 PNG 업로드 */
    @PostMapping("/student/upload/signature")
    @ResponseBody
    public ResponseEntity<?> uploadSignature(
            @RequestParam("file") MultipartFile file,
            @RequestParam("studentId") String studentId) {
        return ResponseEntity.ok(ApiUtils.success(studentJoinService.saveSignature(studentId, file)));
    }
}
