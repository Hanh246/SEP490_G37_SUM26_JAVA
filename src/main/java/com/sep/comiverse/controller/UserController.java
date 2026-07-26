package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.TranslatorResponse;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.IUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User - Public/Shared Operations", description = "APIs for querying user directory information")
public class UserController {

    private final IUserRepository userRepository;

    /**
     * GET /users/translators
     * Searches active, non-deleted users who have the role TRANSLATOR.
     */
    @GetMapping("/translators")
    @Operation(summary = "Search translators", description = "Search active translators in the system by username, full name, or email")
    public ResponseEntity<BaseResponse<List<TranslatorResponse>>> searchTranslators(
            @RequestParam(value = "query", required = false) String query
    ) {
        List<UserEntity> translators = userRepository.searchTranslators(query);
        List<TranslatorResponse> responseList = translators.stream()
                .map(u -> {
                    // Extract initials from full name or username
                    String initials = "TR";
                    String nameToUse = u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
                    if (nameToUse != null && !nameToUse.isBlank()) {
                        String[] parts = nameToUse.trim().split("\\s+");
                        initials = Arrays.stream(parts)
                                .map(part -> part.substring(0, 1))
                                .collect(Collectors.joining())
                                .toUpperCase();
                        if (initials.length() > 2) {
                            initials = initials.substring(0, 2);
                        }
                    }

                    return TranslatorResponse.builder()
                            .id(u.getId())
                            .username(u.getUsername())
                            .fullName(u.getFullName())
                            .email(u.getEmail())
                            .avatarUrl(u.getAvatarUrl())
                            .initials(initials)
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                BaseResponse.<List<TranslatorResponse>>builder()
                        .success(true)
                        .data(responseList)
                        .build()
        );
    }

    /**
     * GET /users/project-leaders
     * Searches active, non-deleted users who have the role PROJECT_LEADER (hired staff).
     * Strictly excludes TRANSLATOR role.
     */
    @GetMapping("/project-leaders")
    @Operation(summary = "Search project leaders", description = "Search active project leaders (hired staff) in the system by username, full name, or email")
    public ResponseEntity<BaseResponse<List<TranslatorResponse>>> searchProjectLeaders(
            @RequestParam(value = "query", required = false) String query
    ) {
        List<UserEntity> leaders = userRepository.searchProjectLeaders(query);
        List<TranslatorResponse> responseList = leaders.stream()
                .map(u -> {
                    String initials = "PL";
                    String nameToUse = u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
                    if (nameToUse != null && !nameToUse.isBlank()) {
                        String[] parts = nameToUse.trim().split("\\s+");
                        initials = Arrays.stream(parts)
                                .map(part -> part.substring(0, 1))
                                .collect(Collectors.joining())
                                .toUpperCase();
                        if (initials.length() > 2) {
                            initials = initials.substring(0, 2);
                        }
                    }

                    return TranslatorResponse.builder()
                            .id(u.getId())
                            .username(u.getUsername())
                            .fullName(u.getFullName())
                            .email(u.getEmail())
                            .avatarUrl(u.getAvatarUrl())
                            .initials(initials)
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                BaseResponse.<List<TranslatorResponse>>builder()
                        .success(true)
                        .data(responseList)
                        .build()
        );
    }
}
