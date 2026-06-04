package com.frostwane.paperagent.auth;

import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRepository;
import com.frostwane.paperagent.user.UserStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getRequiredUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException("请先登录");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.getStatus() != UserStatus.NORMAL) {
            throw new BusinessException("账号已被禁用");
        }
        return user;
    }
}
