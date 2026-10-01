package com.hohoedu.book_clinic.demo;

import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception403;
import com.hohoedu.book_clinic._core.utils.HashUtils;
import com.hohoedu.book_clinic.demo.DemoResetRepository.Target;
import com.hohoedu.book_clinic.user.UserRepository;
import com.hohoedu.book_clinic.user.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시연 데이터 기준점 저장/원복 (2026-10-01) — 반복 시연용.
 *
 * 시연 계정 학생들은 시연 전부터 있던 기록(미리 세팅한 완독·이용권 등)을 지켜야 하므로, 행을 지우는
 * 게 아니라 "기준점" 상태로 되돌린다. 기준점 저장 시 그 학생들에 걸린 행을 스냅샷 테이블에 복사해 두고,
 * 초기화 시 현재 행을 지운 뒤 스냅샷을 원래 PK 그대로 다시 넣는다 — 시연 중 수정된 기존 행
 * (이용권 잔여, 추천 상태 등)도 기준점 값으로 돌아간다. 공용 테이블인 item / slot_instance는
 * 덮어쓰지 않고 이 학생들 때문에 생긴 차이만큼만 되돌린다.
 *
 * 도서 세팅(content/item/priority 등 공용 마스터)은 건드리지 않는다. 대상 학생은 아래 상수로
 * 고정하고, 시연 관리자 계정(DEMO_USER_CODE)이 비밀번호를 한 번 더 입력해야만 실행된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoResetService {

    public static final String DEMO_USER_CODE = "PUS001cos";

    static final List<String> DEMO_STUDENT_IDS = List.of(
            "PUS001251202A0DA2",
            "PUS001251202BD9E7",
            "PUS001251202FDA6E",
            "PUS001251202C4C78",
            "PUS0012512104CA9F");

    private final DemoResetRepository demoResetRepository;
    private final UserRepository userRepository;

    /** 실행 권한 확인 — 시연 관리자 계정이 아니거나 비밀번호가 틀리면 예외 */
    public void verify(CustomUserDetails userDetails, String rawPassword) {
        if (userDetails == null || !DEMO_USER_CODE.equals(userDetails.getLoginUser().getUserCode())) {
            throw new Exception403("시연 관리자 계정에서만 실행할 수 있습니다.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new Exception400("비밀번호를 입력해 주세요.");
        }
        User user = userRepository.findByUserId(userDetails.getLoginUser().getUserId());
        if (user == null || !HashUtils.hashPassword(rawPassword, user.getSalt()).equalsIgnoreCase(user.getPasswordHash())) {
            throw new Exception400("비밀번호가 일치하지 않습니다.");
        }
    }

    /** 기준점 저장 — 지금 상태를 시연 시작 상태로 기억한다(이전 기준점은 덮어씀). 저장한 행 수 */
    @Transactional
    public int saveSnapshot() {
        String studentIn = DemoResetRepository.studentIn(DEMO_STUDENT_IDS);
        int saved = 0;
        demoResetRepository.saveSnapshotStudents(DEMO_STUDENT_IDS);
        for (Target t : Target.values()) {
            saved += demoResetRepository.saveSnapshot(t, studentIn);
        }
        log.info("[시연 기준점 저장] 학생 {}명, {}행", DEMO_STUDENT_IDS.size(), saved);
        return saved;
    }

    /**
     * 기준점으로 원복 — 한 트랜잭션이라 중간에 실패하면 전부 롤백된다.
     * Firestore 모니터링 카드는 커밋 뒤에 지워야 하므로 시연 중 새로 생긴 예약 ID를 돌려준다.
     */
    @Transactional
    public List<Long> restore() {
        if (!demoResetRepository.hasSnapshot()) {
            throw new Exception400("저장된 기준점이 없습니다. 먼저 [기준점 저장]을 해 주세요.");
        }
        // 시연 학생 목록이 기준점 저장 이후 바뀌었으면 원복하지 않는다 — 새로 추가된 학생은 기준점에 행이
        // 없어 기존 데이터까지 통째로 지워지고, 빠진 학생은 원복 대상에서 누락된다
        List<String> snapStudents = demoResetRepository.findSnapshotStudents();
        if (snapStudents == null || !new HashSet<>(snapStudents).equals(new HashSet<>(DEMO_STUDENT_IDS))) {
            throw new Exception400("시연 학생 목록이 바뀌었습니다. 학생 데이터를 확인한 뒤 [기준점 저장]을 다시 해 주세요.");
        }
        String studentIn = DemoResetRepository.studentIn(DEMO_STUDENT_IDS);

        // 차이 계산은 현재 행이 있어야 하므로 삭제보다 먼저
        List<Long> newReservationIds = demoResetRepository.findNewReservationIds(studentIn);
        demoResetRepository.restoreItemQty(studentIn);
        demoResetRepository.restoreSlotSeats(studentIn);

        Target[] targets = Target.values();
        int deleted = 0;
        for (int i = targets.length - 1; i >= 0; i--) {
            deleted += demoResetRepository.deleteCurrent(targets[i], studentIn);
        }
        int restored = 0;
        for (Target t : targets) {
            restored += demoResetRepository.restoreSnapshot(t);
        }

        log.info("[시연 초기화] 학생 {}명 — 삭제 {}행, 기준점 복원 {}행, 새 예약 {}건",
                DEMO_STUDENT_IDS.size(), deleted, restored, newReservationIds.size());
        return newReservationIds;
    }
}
