package com.hohoedu.book_clinic.bookstore.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BookstoreItem {

    private String bcode;
    private Integer contentId;
    private String bookTitle;
    private String publisher;
    private String state;
    private String keywords;

    @Builder
    public BookstoreItem(String bcode, Integer contentId, String bookTitle,
                         String publisher, String state, String keywords) {
        this.bcode = bcode;
        this.contentId = contentId;
        this.bookTitle = bookTitle;
        this.publisher = publisher;
        this.state = state;
        this.keywords = keywords;
    }
}
