package com.hohoedu.book_clinic._core.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
public class LoginUser implements Serializable {

    private Integer id;
    private String centerCode;
    private String roleKey;
    private String userId;
    private String userName;
    private String type;
    private Boolean isHan;
    private Boolean isBook;
    private Boolean isClinic;

    @Builder
    public LoginUser(Integer id, String centerCode, String roleKey, String userId,
                     String userName, String type, Boolean isHan, Boolean isBook, Boolean isClinic) {
        this.id = id;
        this.centerCode = centerCode;
        this.roleKey = roleKey;
        this.userId = userId;
        this.userName = userName;
        this.type = type;
        this.isHan = isHan;
        this.isBook = isBook;
        this.isClinic = isClinic;
    }
}
