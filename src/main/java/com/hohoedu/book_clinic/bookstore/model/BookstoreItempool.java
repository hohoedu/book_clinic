package com.hohoedu.book_clinic.bookstore.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BookstoreItempool {

    private Integer contentId;
    private String qnum;
    private String q;
    private String qex;
    private String e1;
    private String e2;
    private String e3;
    private String e4;
    private String ans;
    private String qtype;
    private String qexgb;
    private String state;

    @Builder
    public BookstoreItempool(Integer contentId, String qnum, String q, String qex,
                             String e1, String e2, String e3, String e4,
                             String ans, String qtype, String qexgb, String state) {
        this.contentId = contentId;
        this.qnum = qnum;
        this.q = q;
        this.qex = qex;
        this.e1 = e1;
        this.e2 = e2;
        this.e3 = e3;
        this.e4 = e4;
        this.ans = ans;
        this.qtype = qtype;
        this.qexgb = qexgb;
        this.state = state;
    }
}
