package com.hohoedu.book_clinic.book.model;

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
    private String imageUrl;

    @Builder
    public BookstoreItem(String bcode, Integer contentId, String bookTitle,
                         String publisher, String state, String imageUrl) {
        this.bcode = bcode;
        this.contentId = contentId;
        this.bookTitle = bookTitle;
        this.publisher = publisher;
        this.state = state;
        this.imageUrl = imageUrl;
    }
}
