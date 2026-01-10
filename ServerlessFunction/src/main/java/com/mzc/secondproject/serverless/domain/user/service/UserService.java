package com.mzc.secondproject.serverless.domain.user.service;

import com.mzc.secondproject.serverless.common.auth.jwt.JwtService;
import com.mzc.secondproject.serverless.domain.user.dto.LoginRequest;
import com.mzc.secondproject.serverless.domain.user.dto.SignUpRequest;
import com.mzc.secondproject.serverless.domain.user.model.User;
import com.mzc.secondproject.serverless.domain.user.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public User signUp(SignUpRequest request) {

        if (request.getEmail() == null || !request.getEmail().contains("@")) {
            throw new IllegalArgumentException("올바르지 않은 이메일 형식입니다.");
        }

        // 비밀번호 유효성 검사 (최소 8자)
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new IllegalArgumentException("비밀번호는 최소 8자 이상이어야 합니다.");
        }

        if (request.getNickname() == null || !request.getNickname().trim().isEmpty()) {
            throw new IllegalArgumentException("닉네임을 입력해주세요 ");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        String level = request.getLevel();
        if (level != null && !level.equals("BEGINNER") && !level.equals("INTERMEDIATE") && !level.equals("ADVANCED")) {
            throw new IllegalArgumentException("난이도 값은 BEGINNER, INTERMEDIATE, ADVANCED 중 하나여야 합니다");
        }

        String userId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        User user = User.builder()
                .userId(userId)
                .gsi1pk("EMAIL#" + request.getEmail())
                .email(request.getEmail())
                .password(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()))
                .nickname(request.getNickname())
                .level(level != null ? level : "BEGINNER")
                .createdAt(now)
                .updatedAt(now)
                .build();

        userRepository.save(user);

        user.setPassword(null);
        return user;
    }


    public LoginResult login(LoginRequest request) {
        // 이메일로 사용자 조회
        Optional<User> optUser = userRepository.findByEmail(request.getEmail());
        if (optUser.isEmpty()) {
            throw new SecurityException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        User user = optUser.get();

        // 비밀번호 검증
        if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new SecurityException("비밀번호가 올바르지 않습니다");
        }

        // JWT 토큰 생성
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("nickname", user.getNickname());
        claims.put("level", user.getLevel());

        String token = jwtService.generateToken(user.getUserId(), claims);

        logger.info("로그인한 사용자: userId={}, 로그인한 사용자 이메일: email={}", user.getUserId(), user.getEmail());

        user.setPassword(null);

        return new LoginResult(token, user);
    }

    public record LoginResult(String token, User user) {}

}
