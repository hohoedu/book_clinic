package com.hohoedu.book_clinic.appsend;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception403;
import com.hohoedu.book_clinic.app.AppReportService;
import com.hohoedu.book_clinic.app._dto.AppRespDTO;
import com.hohoedu.book_clinic.appsend._dto.AppSendRespDTO;
import com.hohoedu.book_clinic.diary.DiaryRepository;
import com.hohoedu.book_clinic.common.notification.NotificationService;
import com.hohoedu.book_clinic.common.notification._dto.NotificationReqDTO;
import com.hohoedu.book_clinic.diary._dto.DiaryRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 독서 결과 발송 목록 조회 (2026-09-22).
 *
 * 읽은 책은 독서일지와 같은 조회(DiaryRepository.findDiaryBooks)를 그대로 쓴다 — 같은 날짜의
 * 같은 "일지 스냅샷 + 제출 전 추천분" 규칙이라 조회를 따로 만들면 두 화면의 책 목록이 갈라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSendService {

    /** 푸시 제목 — 앱 알림창에 그대로 뜬다 */
    private static final String PUSH_TITLE = "독서 결과 도착";

    private final AppSendRepository appSendRepository;
    private final DiaryRepository diaryRepository;
    private final AppReportService appReportService;
    private final NotificationService notificationService;

    public AppSendRespDTO.SendViewRespDTO getSendView(LocalDate date, String centerCode,
                                                      Integer slotSeq, String keyword) {
        List<AppSendRespDTO.RowDTO> rows = appSendRepository.findSendRows(date, centerCode, slotSeq, keyword);

        Map<String, List<DiaryRespDTO.BookDTO>> booksByStudent = diaryRepository.findDiaryBooks(date, centerCode)
                .stream()
                .collect(Collectors.groupingBy(DiaryRespDTO.BookDTO::getStudentId));

        rows.forEach(row -> row.setBooks(booksByStudent.getOrDefault(row.getStudentId(), List.of())));

        AppSendRespDTO.SendViewRespDTO resp = new AppSendRespDTO.SendViewRespDTO();
        resp.setSlots(appSendRepository.findSendSlots(date, centerCode));
        resp.setRows(rows);
        return resp;
    }

    /**
     * 발송 미리보기 — 앱의 정독 결과 화면과 같은 응답을 그대로 준다
     * ({@link AppReportService#getReport}). 미리보기가 "학부모에게 이렇게 나간다"를 보여주는
     * 화면이라, 조립을 따로 두면 미리보기와 발송물이 조용히 갈라진다.
     *
     * sentOnly=false — 아직 발송하지 않은 일지를 봐야 하는 화면이다(그게 미리보기의 전부다).
     * 대신 앱과 달리 studentId 를 밖에서 받으므로, 먼저 로그인 직원의 센터 학생인지 본다.
     */
    public AppRespDTO.BookstoreReportDTO getPreviewReport(String studentId, LocalDate date, String centerCode) {
        if (!appSendRepository.existsStudentInCenter(studentId, centerCode)) {
            throw new Exception403("다른 센터의 학생입니다.");
        }
        return appReportService.getReport(studentId, date == null ? null : date.toString(), false);
    }

    /**
     * 발송 처리 — 일지의 is_send 를 1 로 올리고, 그 학생 앱으로 푸시를 보낸다(2026-09-23).
     *
     * 두 가지를 같이 하는 이유는 하나만 해서는 발송이 되지 않기 때문이다. is_send 는 앱 조회의
     * 문을 여는 플래그이고(앱은 1만 본다), 푸시는 학부모에게 도착을 알리는 쪽이다.
     *
     * 트랜잭션을 걸지 않는다 — 갱신이 UPDATE 한 문장이라 그 자체로 원자적이고, 푸시(FCM)는
     * 외부 호출이라 트랜잭션 안에서 N번 돌리면 그동안 DB 커넥션과 락을 잡고 있게 된다.
     *
     * 푸시가 실패해도 is_send 는 되돌리지 않는다. 앱에서 결과를 보는 길은 열렸고(학부모가 앱을
     * 열면 보인다), 되돌리면 다음 발송 때 결과가 두 번 나간다. 실패는 erp_notification 에
     * 이력으로 남으므로 거기서 확인한다(NotificationService.saveHistory).
     *
     * @return 실제로 미발송 → 발송으로 바뀐 건수.
     */
    public int send(List<Integer> diaryKeys, String centerCode, String sentBy) {
        if (diaryKeys == null || diaryKeys.isEmpty()) {
            throw new Exception400("발송할 학생을 선택해 주세요.");
        }

        // 대상을 먼저 확정한다 — 갱신 뒤에 뽑으면 원래 발송돼 있던 건까지 섞여 푸시가 두 번 나간다
        List<AppSendRespDTO.SendTargetDTO> targets = appSendRepository.findSendTargets(diaryKeys, centerCode);
        if (targets.isEmpty()) return 0;

        int updated = appSendRepository.updateSendFlag(
                targets.stream().map(AppSendRespDTO.SendTargetDTO::getDiaryKey).toList(), centerCode);

        targets.forEach(target -> pushResult(target, sentBy));
        return updated;
    }

    /**
     * 학생 한 명에게 결과 도착 푸시.
     *
     * 멀티캐스트가 아니라 한 명씩 보내는 건 문구에 학생 이름이 들어가서다 — 형제가 한 기기를
     * 쓰는 경우 누구 결과인지 알 수 없으면 알림의 뜻이 없다. 이력도 학생 단위로 남는다.
     *
     * 앱 미연동(토큰 없음)은 NotificationService 가 FAIL 이력으로 남기고 넘어간다. 한 명이
     * 실패해도 나머지 발송은 계속돼야 하므로 여기서 삼킨다.
     */
    private void pushResult(AppSendRespDTO.SendTargetDTO target, String sentBy) {
        String body = target.getStudentName() + " 학생의 독서 결과가 도착했어요. 앱에서 확인해 보세요.";
        try {
            notificationService.send(
                    new NotificationReqDTO.SendReqDTO(PUSH_TITLE, body, "STUDENT", target.getStudentId()), sentBy);
        } catch (Exception e) {
            log.warn("독서 결과 푸시 실패 studentId={} error={}", target.getStudentId(), e.getMessage());
        }
    }
}
