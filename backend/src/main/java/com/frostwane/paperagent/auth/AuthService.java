package com.frostwane.paperagent.auth;

import com.frostwane.paperagent.auth.dto.AuthDtos.AuthResponse;
import com.frostwane.paperagent.auth.dto.AuthDtos.LoginRequest;
import com.frostwane.paperagent.auth.dto.AuthDtos.RegisterRequest;
import com.frostwane.paperagent.auth.dto.AuthDtos.UserResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException("用户名已存在");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("邮箱已注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        String account = request.account().trim();
        User user = account.contains("@")
            ? userRepository.findByEmailIgnoreCase(account).orElseThrow(() -> new BadCredentialsException("Bad credentials"))
            : userRepository.findByUsernameIgnoreCase(account).orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Bad credentials");
        }
        return buildAuthResponse(user);
    }

    public UserResponse currentUser(User user) {
        return toUserResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(jwtService.issueToken(user.getId(), user.getUsername()), toUserResponse(user));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getRole().name(),
            user.getCreatedAt()
        );
    }
}
