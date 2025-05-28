package com.myfinance.domain.user;

import com.myfinance.domain.token.RefreshToken;
import com.myfinance.domain.token.RefreshTokenRepository;
import com.myfinance.domain.user.dto.RegisterRequest;
import com.myfinance.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 사용자명입니다.");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();

        userRepository.save(user);
    }

    public Map<String, String> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtUtil.generateToken(username);
        String refreshToken = jwtUtil.generateRefreshToken(username);

        RefreshToken tokenEntity = RefreshToken.builder()
                .username(username)
                .token(refreshToken)
                .expiryDate(LocalDateTime.now().plusHours(7))
                .build();
        refreshTokenRepository.save(tokenEntity);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        );
    }
}
