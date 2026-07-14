package com.example.banking.service;

import com.example.banking.config.JwtProvider;
import com.example.banking.domain.user.User;
import com.example.banking.domain.user.UserRepository;
import com.example.banking.dto.request.LoginRequest;
import com.example.banking.dto.request.SignupRequest;
import com.example.banking.dto.response.TokenResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public void signup(SignupRequest request){
        if(userRepository.findByEmail(request.getEmail()).isPresent()){
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(User.Role.USER)
                .build();

        userRepository.save(user);
    }

    public TokenResponse login(LoginRequest request){
         User user = userRepository.findByEmail(request.getEmail())
                 .orElseThrow(()-> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다."));

         if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())){
             throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
         }

         String accessToken = jwtProvider.createAccessToken(user.getId());
         String refreshToken = jwtProvider.createRefreshToken(user.getId());


         return new TokenResponse(accessToken, refreshToken);
    }
}
