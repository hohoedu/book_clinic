package com.hohoedu.book_clinic.user._dto;

import com.hohoedu.book_clinic._core.auth.LoginUser;
import lombok.Getter;

public class UserRespDTO {

    @Getter
    public static class DetailRespDTO {
        private final Integer id;
        private final String userId;
        private final String userName;
        private final String centerCode;
        private final String roleKey;
        private final String type;
        private final Boolean isHan;
        private final Boolean isBook;
        private final Boolean isClinic;

        public DetailRespDTO(LoginUser loginUser) {
            this.id = loginUser.getId();
            this.userId = loginUser.getUserId();
            this.userName = loginUser.getUserName();
            this.centerCode = loginUser.getCenterCode();
            this.roleKey = loginUser.getRoleKey();
            this.type = loginUser.getType();
            this.isHan = loginUser.getIsHan();
            this.isBook = loginUser.getIsBook();
            this.isClinic = loginUser.getIsClinic();
        }
    }
}
