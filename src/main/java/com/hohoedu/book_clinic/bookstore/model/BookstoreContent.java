package com.hohoedu.book_clinic.bookstore.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BookstoreContent {

    private Integer contentId;
    private String originalTitle;
    private String author;
    private String genre;
    private String contentType;  // DB 컬럼명 'class' — Java 예약어로 인해 변경
    private String schoolyear;
    private String summary;

    @Builder
    public BookstoreContent(Integer contentId, String originalTitle, String author,
                            String genre, String contentType, String schoolyear, String summary) {
        this.contentId = contentId;
        this.originalTitle = originalTitle;
        this.author = author;
        this.genre = genre;
        this.contentType = contentType;
        this.schoolyear = schoolyear;
        this.summary = summary;
    }
}
