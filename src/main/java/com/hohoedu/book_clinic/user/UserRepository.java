package com.hohoedu.book_clinic.user;

import com.hohoedu.book_clinic.user.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRepository {

    User findByUserId(String userId);
}
