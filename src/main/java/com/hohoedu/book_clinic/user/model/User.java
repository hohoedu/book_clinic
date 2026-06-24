package com.hohoedu.book_clinic.user.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class User {

    private Integer id;
    private LocalDateTime createdAt;
    private String centerCode;
    private String roleKey;
    private String userCode;
    private String userId;
    private String userName;
    private String passwordHash;
    private String salt;
    private String type;
    private String userPhone;
    private Boolean isHan;
    private Boolean isBook;
    private Boolean useYn;
    private Boolean isClinic;

    @Builder
    public User(Integer id, LocalDateTime createdAt, String centerCode, String roleKey,
            String userCode, String userId, String userName, String passwordHash,
            String salt, String type, String userPhone, Boolean isHan, Boolean isBook,
            Boolean useYn, Boolean isClinic) {
        this.id = id;
        this.createdAt = createdAt;
        this.centerCode = centerCode;
        this.roleKey = roleKey;
        this.userCode = userCode;
        this.userId = userId;
        this.userName = userName;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.type = type;
        this.userPhone = userPhone;
        this.isHan = isHan;
        this.isBook = isBook;
        this.useYn = useYn;
        this.isClinic = isClinic;
    }
}
