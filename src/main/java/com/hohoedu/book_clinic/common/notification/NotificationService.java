package com.hohoedu.book_clinic.common.notification;

import com.hohoedu.book_clinic.common.notification._dto.NotificationReqDTO;
import com.hohoedu.book_clinic.common.notification._dto.NotificationRespDTO;
import com.hohoedu.book_clinic.common.notification.model.Notification;
import com.hohoedu.book_clinic.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 푸시 알림 발송 비즈니스 로직 서비스
 * 발송 대상(STUDENT/CENTER/ALL)에 따라 FCM 발송 후 이력 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final FcmService fcmService;
    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;

    /**
     * 알림 발송 진입점 - targetType에 따라 단건/센터/전체 발송으로 분기
     *
     * @param reqDTO  발송 요청 DTO (title, body, targetType, targetValue)
     * @param sentBy  발송자 userId
     */
    public NotificationRespDTO.SendResultDTO send(NotificationReqDTO.SendReqDTO reqDTO, String sentBy) {
        return switch (reqDTO.getTargetType().toUpperCase()) {
            case "STUDENT" -> sendToStudent(reqDTO, sentBy);
            case "CENTER"  -> sendToCenter(reqDTO, sentBy);
            case "ALL"     -> sendToAll(reqDTO, sentBy);
            default -> throw new IllegalArgumentException("targetType은 STUDENT | CENTER | ALL 중 하나여야 합니다.");
        };
    }

    /**
     * 단일 학생에게 FCM 발송 (targetValue = studentId)
     */
    private NotificationRespDTO.SendResultDTO sendToStudent(NotificationReqDTO.SendReqDTO reqDTO, String sentBy) {
        var student = studentRepository.findById(reqDTO.getTargetValue());
        if (student == null || student.getAppToken() == null || student.getAppToken().isBlank()) {
            saveHistory(sentBy, reqDTO, "STUDENT", reqDTO.getTargetValue(), null, false, "FCM 토큰 없음");
            return new NotificationRespDTO.SendResultDTO(0, 1);
        }

        // 앱 알림 설정에서 끈 종류면 보내지 않는다. 보낸 것처럼 돌려주고 이력도 남기지 않는다 — 발송한 쪽에는 알리지 않는다
        if (isMuted(student.getAppToken(), reqDTO.getData())) {
            log.info("앱 알림 수신 거부로 미발송 studentId={} type={}", student.getStudentId(), reqDTO.getData().get("type"));
            return new NotificationRespDTO.SendResultDTO(1, 0);
        }

        boolean success = fcmService.sendToToken(student.getAppToken(), reqDTO.getTitle(), reqDTO.getBody(), reqDTO.getData());
        saveHistory(sentBy, reqDTO, "STUDENT", student.getStudentId(), student.getAppToken(),
                success, success ? null : "FCM 발송 실패");
        return new NotificationRespDTO.SendResultDTO(success ? 1 : 0, success ? 0 : 1);
    }

    // 책방 알림(data.type) → 앱 알림 설정 항목(erp_app_noti_type.type_code). 서당과 같은 스위치로 켜고 끈다
    private static final java.util.Map<String, String> NOTI_OPTION_BY_PUSH_TYPE = java.util.Map.of(
            "BOOKSTORE_REPORT", "READING_CLINIC",     // 정독 결과 → 독서클리닉
            "BOOKSTORE_ATTENDANCE", "ATTENDANCE");    // 입실·퇴실 → 출석체크

    // 설정 항목이 없는 종류(예: 예약 안내)는 끌 수 없으니 그대로 보낸다. 조회 실패도 보내는 쪽으로 둔다
    private boolean isMuted(String token, java.util.Map<String, String> data) {
        String option = data == null ? null : NOTI_OPTION_BY_PUSH_TYPE.get(data.get("type"));
        if (option == null) return false;
        try {
            return studentRepository.isNotiMuted(token, option);
        } catch (Exception e) {
            log.error("알림 수신 설정 조회 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 특정 센터 소속 학생 전체에게 FCM 발송 (targetValue = centerCode)
     */
    private NotificationRespDTO.SendResultDTO sendToCenter(NotificationReqDTO.SendReqDTO reqDTO, String sentBy) {
        List<String> tokens = studentRepository.findTokensByCenter(reqDTO.getTargetValue());
        if (tokens.isEmpty()) {
            return new NotificationRespDTO.SendResultDTO(0, 0);
        }
        int[] result = fcmService.sendMulticast(tokens, reqDTO.getTitle(), reqDTO.getBody());
        saveHistory(sentBy, reqDTO, "CENTER", null, null,
                result[1] == 0, result[1] > 0 ? "일부 발송 실패 " + result[1] + "건" : null);
        return new NotificationRespDTO.SendResultDTO(result[0], result[1]);
    }

    /**
     * 전체 학생에게 FCM 발송
     */
    private NotificationRespDTO.SendResultDTO sendToAll(NotificationReqDTO.SendReqDTO reqDTO, String sentBy) {
        List<String> tokens = studentRepository.findAllTokens();
        if (tokens.isEmpty()) {
            return new NotificationRespDTO.SendResultDTO(0, 0);
        }
        int[] result = fcmService.sendMulticast(tokens, reqDTO.getTitle(), reqDTO.getBody());
        saveHistory(sentBy, reqDTO, "ALL", null, null,
                result[1] == 0, result[1] > 0 ? "일부 발송 실패 " + result[1] + "건" : null);
        return new NotificationRespDTO.SendResultDTO(result[0], result[1]);
    }

    /**
     * 전체 알림 발송 이력 조회
     */
    public List<Notification> getHistory() {
        return notificationRepository.findAll();
    }

    /**
     * 특정 학생의 알림 발송 이력 조회
     *
     * @param studentId 학생 로그인 ID
     */
    public List<Notification> getHistoryByStudent(String studentId) {
        return notificationRepository.findByTargetId(studentId);
    }

    /**
     * 알림 발송 이력 저장 내부 메서드
     */
    private void saveHistory(String sentBy, NotificationReqDTO.SendReqDTO reqDTO,
            String targetType, String targetId, String fcmToken, boolean success, String errorMsg) {
        notificationRepository.save(Notification.builder()
                .sentBy(sentBy)
                .title(reqDTO.getTitle())
                .body(reqDTO.getBody())
                .targetType(targetType)
                .targetId(targetId)
                .fcmToken(fcmToken)
                .status(success ? "SUCCESS" : "FAIL")
                .errorMsg(errorMsg)
                .build());
    }
}
