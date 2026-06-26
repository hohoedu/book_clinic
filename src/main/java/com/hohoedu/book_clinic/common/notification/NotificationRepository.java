package com.hohoedu.book_clinic.common.notification;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.common.notification.model.Notification;

import java.util.List;

/**
 * 알림 발송 이력 데이터 접근 인터페이스 (erp_notification 테이블)
 */
@Mapper
public interface NotificationRepository {

    /** 알림 발송 이력 저장 */
    void save(Notification notification);

    /** 전체 알림 발송 이력 조회 (최신순) */
    List<Notification> findAll();

    /** 특정 학생의 알림 발송 이력 조회 */
    List<Notification> findByTargetId(@Param("targetId") String targetId);
}
