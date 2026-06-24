package com.hohoedu.book_clinic.student.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Student {

    private Integer id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String centerCode;
    private String gradeKey;
    private String statusKey;
    private String school;
    private String studentId;
    private String studentName;
    private String address;
    private String addressDetail;
    private String appId;
    private String appPassword;
    private String appToken;
    private String birth;
    private String profileImg;
    private String consultKey;
    private String billingPhone;
    private String serialNum;
    private Boolean gender;
    private Boolean studentPrivacyAgree;
    private Boolean isHoho;
    private Boolean subHan;
    private Boolean subBook;
    private Boolean subHoho;

    @Builder
    public Student(Integer id, LocalDateTime createdAt, LocalDateTime updatedAt,
            String centerCode, String gradeKey, String statusKey, String school,
            String studentId, String studentName, String address, String addressDetail,
            String appId, String appPassword, String appToken, String birth,
            String profileImg, String consultKey, String billingPhone, String serialNum,
            Boolean gender, Boolean studentPrivacyAgree, Boolean isHoho,
            Boolean subHan, Boolean subBook, Boolean subHoho) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.centerCode = centerCode;
        this.gradeKey = gradeKey;
        this.statusKey = statusKey;
        this.school = school;
        this.studentId = studentId;
        this.studentName = studentName;
        this.address = address;
        this.addressDetail = addressDetail;
        this.appId = appId;
        this.appPassword = appPassword;
        this.appToken = appToken;
        this.birth = birth;
        this.profileImg = profileImg;
        this.consultKey = consultKey;
        this.billingPhone = billingPhone;
        this.serialNum = serialNum;
        this.gender = gender;
        this.studentPrivacyAgree = studentPrivacyAgree;
        this.isHoho = isHoho;
        this.subHan = subHan;
        this.subBook = subBook;
        this.subHoho = subHoho;
    }
}
