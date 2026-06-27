package com.sep.comiverse.service;

import lombok.RequiredArgsConstructor;
import com.sep.comiverse.dto.request.RegisterRequest;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.util.EmailUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailUtil emailUtil;

    public UserEntity authenticate(String username, String password) {
        UserEntity user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new CustomException(401, "Invalid username or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CustomException(401, "Invalid username or password", HttpStatus.UNAUTHORIZED);
        }
        if ("INACTIVE".equals(user.getStatus())) {
            throw new CustomException(403, "Your account has been banned!", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    @Transactional
    public UserEntity register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new CustomException(400, "Username already exists", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(400, "Email already exists", HttpStatus.BAD_REQUEST);
        }

        RoleEntity userRole = roleRepository.findByRoleName("READER")
                .orElseThrow(() -> new CustomException(500, "Role READER not found", HttpStatus.INTERNAL_SERVER_ERROR));

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(userRole)
                .status("ACTIVE")
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(404, "No account found with this email", HttpStatus.NOT_FOUND));

        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setResetToken(otp);
        userRepository.save(user);

        emailUtil.sendOTP(user.getEmail(), otp, user.getFullName());
    }

    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(404, "No account found with this email", HttpStatus.NOT_FOUND));
        if (user.getResetToken() == null || !user.getResetToken().equals(otp)) {
            throw new CustomException(400, "Invalid or expired OTP code", HttpStatus.BAD_REQUEST);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        userRepository.save(user);
    }

    @Transactional
    public UserEntity registerStaff(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new CustomException(400, "Username already exists", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(400, "Email already exists", HttpStatus.BAD_REQUEST);
        }

        String roleParam = request.getRole();
        final String finalRoleName = (roleParam == null || roleParam.trim().isEmpty()) 
                ? "READER" 
                : roleParam.toUpperCase().trim();

        RoleEntity targetRole = roleRepository.findByRoleName(finalRoleName)
                .orElseThrow(() -> new CustomException(400, "Role " + finalRoleName + " not found", HttpStatus.BAD_REQUEST));

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(targetRole)
                .status("ACTIVE")
                .build();

        return userRepository.save(user);
    }
}
