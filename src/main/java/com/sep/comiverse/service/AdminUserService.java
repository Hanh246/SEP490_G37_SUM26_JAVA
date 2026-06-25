package com.sep.comiverse.service;

import com.sep.comiverse.dto.pagination.AdminUserSearchDTO;
import com.sep.comiverse.dto.response.AdminUserResponse;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.util.EmailUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailUtil emailUtil;

    /**
     * List all users with search, role filter, and status filter.
     * Supports pagination via AdminUserSearchDTO.
     */
    public Page<AdminUserResponse> getAllUsers(AdminUserSearchDTO searchDTO) {
        Specification<UserEntity> spec = buildUserSpecification(searchDTO);
        Page<UserEntity> usersPage = userRepository.findAll(spec, searchDTO.toPageRequest());
        return usersPage.map(this::toAdminUserResponse);
    }

    /**
     * Ban a user by setting their status to INACTIVE.
     */
    @Transactional
    public AdminUserResponse banUser(UUID userId) {
        UserEntity user = findUserOrThrow(userId);

        if ("ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw new CustomException(400, "Cannot ban an ADMIN account", HttpStatus.BAD_REQUEST);
        }

        if ("INACTIVE".equals(user.getStatus())) {
            throw new CustomException(400, "User is already banned", HttpStatus.BAD_REQUEST);
        }

        user.setStatus("INACTIVE");
        userRepository.save(user);
        return toAdminUserResponse(user);
    }

    /**
     * Unban a user by setting their status back to ACTIVE.
     */
    @Transactional
    public AdminUserResponse unbanUser(UUID userId) {
        UserEntity user = findUserOrThrow(userId);

        if ("ACTIVE".equals(user.getStatus())) {
            throw new CustomException(400, "User is already active", HttpStatus.BAD_REQUEST);
        }

        user.setStatus("ACTIVE");
        userRepository.save(user);
        return toAdminUserResponse(user);
    }

    /**
     * Admin-initiated password reset:
     * Generates a temporary password, emails it to the user.
     */
    @Transactional
    public void resetUserPassword(UUID userId) {
        UserEntity user = findUserOrThrow(userId);

        // Generate a temporary password
        String tempPassword = generateTempPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setResetToken(null); // Clear any existing reset tokens
        userRepository.save(user);

        // Send email with the temporary password
        String subject = "Your Password Has Been Reset by Admin";
        String content = buildResetEmailContent(user.getFullName(), user.getUsername(), tempPassword);
        emailUtil.sendEmail(user.getEmail(), subject, content);
    }

    /**
     * Get a single user's details by ID.
     */
    public AdminUserResponse getUserById(UUID userId) {
        UserEntity user = findUserOrThrow(userId);
        return toAdminUserResponse(user);
    }

    // ── PRIVATE HELPERS ────────────────────────────────

    private UserEntity findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
    }

    private Specification<UserEntity> buildUserSpecification(AdminUserSearchDTO searchDTO) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only non-deleted users
            predicates.add(cb.equal(root.get("deleted"), false));

            // Search by name, username, or email
            String search = searchDTO.getSearch();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("fullName")), pattern);
                Predicate usernameLike = cb.like(cb.lower(root.get("username")), pattern);
                Predicate emailLike = cb.like(cb.lower(root.get("email")), pattern);
                predicates.add(cb.or(nameLike, usernameLike, emailLike));
            }

            // Filter by role
            String role = searchDTO.getRole();
            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("role").get("roleName")), role.toLowerCase()));
            }

            // Filter by status
            String status = searchDTO.getStatus();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.toUpperCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AdminUserResponse toAdminUserResponse(UserEntity user) {
        String roleName = user.getRole() != null ? user.getRole().getRoleName() : "USER";

        // Map INACTIVE -> Banned, ACTIVE -> Active for frontend display
        String displayStatus;
        if ("INACTIVE".equalsIgnoreCase(user.getStatus())) {
            displayStatus = "Banned";
        } else {
            displayStatus = "Active";
        }

        return AdminUserResponse.builder()
                .id(user.getId())
                .userId("USR-" + user.getId().toString().substring(0, 8).toUpperCase())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(capitalizeFirst(roleName))
                .status(displayStatus)
                .provider(user.getProvider())
                .avatarUrl(user.getAvatarUrl())
                .createdDate(user.getCreatedAt())
                .updatedDate(user.getUpdatedAt())
                .build();
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String buildResetEmailContent(String name, String username, String tempPassword) {
        return """
            <!DOCTYPE html>
            <html><head><meta charset='UTF-8'>
            <style>
            body{font-family:sans-serif;padding:20px;background:#f4f4f4;}
            .box{max-width:600px;margin:auto;background:#fff;padding:20px 30px;border-radius:8px;border:1px solid #ddd;}
            h2{color:#333;}
            p{color:#555;line-height:1.5;}
            .pw-container{text-align:center;padding:15px;background:#e9ecef;border-radius:4px;margin:25px 0;}
            .pw{font-size:24px;font-weight:bold;color:#dc3545;letter-spacing:3px;font-family:monospace;}
            hr{border:0;border-top:1px solid #eee;margin:20px 0;}
            b{color:#333;}
            </style></head><body>
            <div class='box'>
            <h2>Password Reset by Administrator</h2>
            <p>Hello <b>%s</b>,</p>
            <p>An administrator has reset the password for your account (<b>%s</b>). Your new temporary password is:</p>
            <div class='pw-container'><div class='pw'>%s</div></div>
            <p><b>Important:</b> Please log in with this temporary password and change it immediately for security.</p>
            <hr><p style='font-size:12px;color:#999;'>If you did not expect this, please contact the administrator immediately.</p>
            <p>Best regards,<br>ComiVerse Admin Team</p>
            </div></body></html>
            """.formatted(name, username, tempPassword);
    }
}
