package com.hohoedu.book_clinic.book._dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 도서 관련 응답 DTO 모음 */
public class BookRespDTO {

    /** 마스터 도서 조회 응답 */
    @Data
    public static class ContentRespDTO {
        private Integer contentId;
        private String originalTitle;
        private String author;
        private String genre;
        private String genreName;
        private String contentType;
        private String contentTypeName;
        private String schoolyear;
        private String schoolyearName;
        private String summary;
        private String keywords;
        private String state;         // 사용여부 (Y: 사용중, N: 절판)
        private String publisher;     // 출판사
        private String imageUrl;      // 도서 이미지 경로
        private String readingTime;   // 독서 예상 시간
        private String difficulty;    // 난이도 (상/중/하)
        // 분류(contentType)에 딸린 부가 정보 (연계교과/추천기관명/수상명 중 해당 분류 값 하나, 도서당 최대 1행)
        private String extraDetailName;
    }

    /** 실물 도서(판본) 조회 응답 (2026-07-29 재설계: bcode+센터당 1행, qty 카운터로 보유수량 관리) */
    @Data
    public static class ItemRespDTO {
        private Integer itemId;
        private String bcode;
        private Integer contentId;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
        private String centerCode;
        private Integer qty;
        private Integer loanedQty;
        private Integer lostQty;
    }

    /** 삭제된 실물 도서 조회 응답 */
    @Data
    public static class ItemDelRespDTO {
        private Integer delId;
        private LocalDateTime deletedAt;
        private String deletedBy;
        private Integer itemId;
        private String bcode;
        private Integer contentId;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
        private String centerCode;
        private Integer qty;
        private Integer loanedQty;
        private Integer lostQty;
    }

    /**
     * 센터별 보유 도서(판본) 조회 응답 — bcode당 1행에 qty/loanedQty/lostQty가 이미 집계돼 있다
     * (프런트 카드가 기대하는 필드 구조는 유지)
     */
    @Data
    public static class ItemCenterRespDTO {
        private String bcode;
        private String centerCode;
        private Integer quantity;
        private Integer loanedQty;
        private Integer lostQty;
        private String state;  // 사본 상태 스냅샷 (AVAILABLE/LOANED/LOST)
        private LocalDateTime registeredAt;
        private Integer contentId;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
    }

    /** 실물 도서 대여 이력 조회 응답 (item_id 기준, bcode/centerCode는 조인으로 채워 화면 호환 유지) */
    @Data
    public static class ItemLoanRespDTO {
        private Integer loanId;
        private Integer itemId;
        private String bcode;
        private String centerCode;
        private String studentId;
        private String studentName;
        private LocalDateTime loanedAt;
        private LocalDateTime returnedAt;
        private String status;
    }

    /** 삭제된 마스터 도서 조회 응답 */
    @Data
    public static class ContentDelRespDTO {
        private Integer delId;
        private LocalDateTime deletedAt;
        private String deletedBy;
        private Integer contentId;
        private String originalTitle;
        private String author;
        private String genre;
        private String contentType;
        private String schoolyear;
        private String summary;
        private String keywords;
    }

    /** 보유도서 설정 화면 1행 — 마스터 도서 정보 + 우리 센터 보유 수량(사본 수) */
    @Data
    public static class StockRespDTO {
        private Integer contentId;
        private String originalTitle;
        private String imageUrl;
        private String schoolyear;      // 학년 코드 (뱃지 색상 매핑용)
        private String schoolyearName;
        private String contentTypeName;
        private String genreName;
        private String difficulty;      // 상/중/하
        private Integer quantity;       // 우리 센터 보유 사본 수
        private Integer loanedQty;      // 그중 대여 중(LOANED) — 수량을 줄일 수 없는 하한
        private LocalDateTime lastChangedAt;  // 마지막 수량 변경일시 (stock_log 기준, 변경 이력 없으면 null)
    }

    /** 보유도서 설정 화면 — 마스터 도서 행을 펼쳤을 때 보이는 하위 판본(bcode) 1행 (bcode당 수량이 이미 집계돼 있다) */
    @Data
    public static class StockItemRespDTO {
        private Integer itemId;
        private String bcode;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
        private Integer qty;
        private Integer loanedQty;
        private Integer lostQty;
        private LocalDateTime registeredAt;
    }

    /** 보유수량 일괄 등록 — 개별 항목 처리 결과 (실패해도 나머지 항목은 계속 진행되므로 항목별로 성공 여부를 담는다) */
    @Data
    public static class StockBulkResultRespDTO {
        private Integer contentId;
        private boolean success;
        private Integer quantity;  // 성공 시 실제로 반영된 수량
        private String message;    // 실패 시 사유
    }

    /** 보유 수량 변경 이력 1행 */
    @Data
    public static class StockLogRespDTO {
        private Integer logId;
        private Integer beforeQty;
        private Integer afterQty;
        private String memo;       // 감소 사유 (늘린 변경이면 "추가")
        private String changedBy;
        private LocalDateTime changedAt;
    }

}
