package com.hohoedu.book_clinic.book.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BookstoreItem {

    private Integer itemId;
    private String bcode;
    private Integer contentId;
    private String centerCode;
    private String bookTitle;
    private String publisher;
    private String status;
    private String imageUrl;

    @Builder
    public BookstoreItem(Integer itemId, String bcode, Integer contentId, String centerCode, String bookTitle,
                         String publisher, String status, String imageUrl) {
        this.itemId = itemId;
        this.bcode = bcode;
        this.contentId = contentId;
        this.centerCode = centerCode;
        this.bookTitle = bookTitle;
        this.publisher = publisher;
        this.status = status;
        this.imageUrl = imageUrl;
    }
}
