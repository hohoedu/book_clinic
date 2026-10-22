package com.hohoedu.book_clinic.common.notification;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.hohoedu.book_clinic.common.notification._dto.NotificationReqDTO;
import com.hohoedu.book_clinic.monitor.AttendancePushEvent;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 입실·퇴실 학부모 푸시 (2026-10-22).
 *
 * 출석체크 PWA(QR 입실·퇴실)와 직원 퇴실 처리 모두 MonitorService 를 거치므로 거기서 나온 이벤트 하나로 처리한다.
 * 커밋 이후(AFTER_COMMIT)에 별도 스레드(pushExecutor)에서 보낸다 — 입실이 롤백되면 알림도 없고,
 * FCM 이 느리거나 실패해도 입실·퇴실 처리와 출석체크 기기 응답에는 영향이 없다.
 *
 * data.type 은 i-with 가 알림을 눌렀을 때 책방 출석체크 화면을 여는 데 쓴다(push_route.dart).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendancePushListener {

    private static final String PUSH_TYPE_ATTENDANCE = "BOOKSTORE_ATTENDANCE";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationService notificationService;
    private final StudentRepository studentRepository;

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttendance(AttendancePushEvent event) {
        try {
            Student student = studentRepository.findById(event.studentId());
            String name = student == null ? "" : student.getStudentName() + " 학생이 ";
            String time = event.at().format(TIME);
            String title = event.entered() ? "호호책방 입실" : "호호책방 퇴실";
            String body = event.entered()
                    ? name + "호호책방에 입실했어요. (" + time + ")"
                    : name + "호호책방에서 퇴실했어요. (" + time + ")";
            notificationService.send(new NotificationReqDTO.SendReqDTO(title, body, "STUDENT", event.studentId(),
                    Map.of("type", PUSH_TYPE_ATTENDANCE, "studentId", event.studentId())), "SYSTEM");
        } catch (Exception e) {
            // 푸시 실패는 입실·퇴실 처리와 무관하다 — 로그만 남긴다(토큰 없음은 NotificationService 가 이력으로 남김)
            log.warn("입실·퇴실 푸시 실패 studentId={} entered={} error={}", event.studentId(), event.entered(), e.getMessage());
        }
    }
}
