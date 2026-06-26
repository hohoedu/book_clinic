package com.hohoedu.book_clinic.common.notification;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.common.notification._dto.NotificationReqDTO;
import com.hohoedu.book_clinic.common.notification._dto.NotificationRespDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 푸시 알림 발송 및 이력 조회 컨트롤러
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * FCM 푸시 알림 발송
     * POST /api/notification/send
     * targetType: STUDENT(단건) | CENTER(센터 전체) | ALL(전체)
     */
    @PostMapping("/send")
    public ResponseEntity<?> send(
            @RequestBody @Valid NotificationReqDTO.SendReqDTO reqDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String sentBy = userDetails.getLoginUser().getUserId();
        NotificationRespDTO.SendResultDTO result = notificationService.send(reqDTO, sentBy);
        return ResponseEntity.ok(ApiUtils.success(result));
    }

    /**
     * 전체 알림 발송 이력 조회
     * GET /api/notification/history
     */
    @GetMapping("/history")
    public ResponseEntity<?> getHistory() {
        List<NotificationRespDTO.NotificationDTO> list = notificationService.getHistory()
                .stream().map(NotificationRespDTO.NotificationDTO::new).collect(Collectors.toList());
        return ResponseEntity.ok(ApiUtils.success(list));
    }

    /**
     * 특정 학생의 알림 발송 이력 조회
     * GET /api/notification/history/student/{studentId}
     */
    @GetMapping("/history/student/{studentId}")
    public ResponseEntity<?> getHistoryByStudent(@PathVariable String studentId) {
        List<NotificationRespDTO.NotificationDTO> list = notificationService.getHistoryByStudent(studentId)
                .stream().map(NotificationRespDTO.NotificationDTO::new).collect(Collectors.toList());
        return ResponseEntity.ok(ApiUtils.success(list));
    }
}
