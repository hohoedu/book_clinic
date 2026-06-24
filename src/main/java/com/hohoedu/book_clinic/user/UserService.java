package com.hohoedu.book_clinic.user;

import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public User findByUserId(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) throw new Exception404("존재하지 않는 계정입니다.");
        return user;
    }
}
