package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.TranslatorRegistrationRequest;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.TranslatorEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.ITranslatorRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/translator-registration")
@CrossOrigin(origins = "*")
public class TranslatorRegistrationController {

    private final IUserRepository userRepository;
    private final ITranslatorRepository translatorRepository;
    private final IRoleRepository roleRepository;

    @PostMapping
    public ResponseEntity<?> translatorRegistration(@RequestBody TranslatorRegistrationRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "You must be logged in to register as a translator."));
        }

        String principalName = authentication.getName(); 

        UserEntity user = userRepository.findByEmail(principalName)
                .orElseGet(() -> userRepository.findByUsername(principalName).orElse(null));

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found."));
        }

        if (translatorRepository.existsByUser_Id(user.getId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "You are already registered as a translator."));
        }

        RoleEntity translatorRole = roleRepository.findByRoleName("TRANSLATOR")
                .orElseThrow(() -> new IllegalStateException(
                        "Role TRANSLATOR not found in database — check seed data in table 'roles'."));

        user.setRole(translatorRole);
        userRepository.save(user);

        TranslatorEntity translator = TranslatorEntity.builder()
                .user(user)
                .specializations(request.getSpecializations())
                .experienceYears(request.getExperiencedYears())
                .phoneNumber(request.getPhone())
                .facebookUrl(request.getFacebookUrl())
                .cvUrl(request.getCvUrl())
                .bio(request.getBio())
                .joinedProjectCount(0)
                .build();

        TranslatorEntity created = translatorRepository.save(translator);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getMyProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "You must be logged in."));
        }

        String principalName = authentication.getName();

        UserEntity user = userRepository.findByEmail(principalName)
                .orElseGet(() -> userRepository.findByUsername(principalName).orElse(null));

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found."));
        }

        return translatorRepository.findByUser_Id(user.getId())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "No translator profile found.")));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateMyProfile(@RequestBody TranslatorRegistrationRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "You must be logged in."));
        }

        String principalName = authentication.getName();

        UserEntity user = userRepository.findByEmail(principalName)
                .orElseGet(() -> userRepository.findByUsername(principalName).orElse(null));

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found."));
        }

        TranslatorEntity translator = translatorRepository.findByUser_Id(user.getId())
                .orElseGet(() -> TranslatorEntity.builder().user(user).joinedProjectCount(0).build());

        if (request.getSpecializations() != null) {
            translator.setSpecializations(request.getSpecializations());
        }
        if (request.getExperiencedYears() != null) {
            translator.setExperienceYears(request.getExperiencedYears());
        }
        if (request.getPhone() != null) {
            translator.setPhoneNumber(request.getPhone());
        }
        if (request.getFacebookUrl() != null) {
            translator.setFacebookUrl(request.getFacebookUrl());
        }
        if (request.getCvUrl() != null) {
            translator.setCvUrl(request.getCvUrl());
        }
        if (request.getBio() != null) {
            translator.setBio(request.getBio());
        }

        TranslatorEntity saved = translatorRepository.save(translator);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<?> getProfileByUserId(@PathVariable UUID userId) {
        return translatorRepository.findByUser_Id(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "No translator profile found for this user.")));
    }
}