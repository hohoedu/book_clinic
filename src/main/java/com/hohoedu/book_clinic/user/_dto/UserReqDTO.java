package com.hohoedu.book_clinic.user._dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class UserReqDTO {

    @Data
    public static class LoginReqDTO {

        @NotBlank
        private String userId;

        @NotBlank
        private String password;
    }
}
