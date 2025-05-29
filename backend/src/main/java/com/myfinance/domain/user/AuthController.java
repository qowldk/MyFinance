package com.myfinance.domain.user;

import com.myfinance.domain.token.RefreshToken;
import com.myfinance.domain.token.RefreshTokenRepository;
import com.myfinance.domain.token.dto.RefreshTokenRequest;
import com.myfinance.domain.user.dto.LoginRequest;
import com.myfinance.domain.user.dto.RegisterRequest;
import com.myfinance.domain.user.dto.UserInfoResponse;
import com.myfinance.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name="인증 API", description = "회원가입 및 로그인 관련 API")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "사용자 정보를 등록하여 회원가입합니다.")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        try {
            userService.register(request);
            return ResponseEntity.ok("회원가입이 완료되었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "아이디와 비밀번호를 입력하고 JWT 토큰을 반환합니다.")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        try {
            Map<String, String> tokens = userService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(tokens);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/info")
    @Operation(summary = "사용자 정보 조회", description = "JWT 인증을 통해 로그인한 사용자의 정보를 조회합니다.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공적으로 사용자 정보를 반환함"),
            @ApiResponse(responseCode = "401", description = "JWT 토큰이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "404", description = "해당 사용자 정보를 찾을 수 없음")
    })
    public ResponseEntity<UserInfoResponse> userInfo(Authentication authentication) {
        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 정보를 찾을 수 없습니다."));

        return ResponseEntity.ok(new UserInfoResponse(user.getUsername(), user.getRole()));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Access Token 재발급",
            description = "만료된 Access Token을 대체할 새로운 Access Token을 발급합니다. 유효한 Refresh Token이 필요합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "새로운 Access Token 발급 성공"),
            @ApiResponse(responseCode = "401", description = "Refresh Token이 유효하지 않거나 만료됨"),
            @ApiResponse(responseCode = "404", description = "해당 Refresh Token이 DB에 존재하지 않음")
    })
    public ResponseEntity<?> refreshAccessToken(@RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        String username = jwtUtil.validateToken(refreshToken);

        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
        }

        Optional<RefreshToken> savedTokenOpt = refreshTokenRepository.findByToken(refreshToken);
        if (savedTokenOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("저장된 토큰이 없습니다.");
        }

        RefreshToken savedToken = savedTokenOpt.get();
        if (savedToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.deleteByUsername(username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh Token이 만료되었습니다.");
        }

        String newAccessToken = jwtUtil.generateToken(username);

        return ResponseEntity.ok(Map.of("access_token", newAccessToken));
    }
}
