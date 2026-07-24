package com.sep.comiverse.controller;

import com.sep.comiverse.dto.TranslationRequestDTO;
import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.plugin.mapper.ProjectTeamMapperPlugin;
import com.sep.comiverse.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/translation-pool")
@RequiredArgsConstructor
@Tag(name = "Translation Pool", description = "APIs for requesting and claiming translation jobs")
@CrossOrigin(origins = "*")
public class TranslationPoolController {

    private final IProjectTeamRepository projectTeamRepository;
    private final IComicRepository comicRepository;
    private final ProjectTeamMapperPlugin projectTeamMapper;
    private final NotificationService notificationService;

    @PostMapping("/request")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @Operation(summary = "Submit translation requests", description = "Creates a separate unclaimed translation project for each target language. Source language and title are loaded from the comic.")
    public ResponseEntity<BaseResponse<String>> requestTranslation(@RequestBody TranslationRequestDTO request) {
        if (request == null || request.getComicId() == null) {
            return ResponseEntity.badRequest().body(
                    BaseResponse.<String>builder()
                            .success(false)
                            .message("Comic id is required")
                            .build()
            );
        }
        if (request.getTargetLanguages() == null || request.getTargetLanguages().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    BaseResponse.<String>builder()
                            .success(false)
                            .message("Target languages cannot be empty")
                            .build()
            );
        }

        ComicEntity comic = comicRepository.findById(request.getComicId()).orElse(null);
        if (comic == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    BaseResponse.<String>builder()
                            .success(false)
                            .message("Comic not found")
                            .build()
            );
        }

        String comicTitle = comic.getTitle();
        String rawSourceLanguage = comic.getLanguage();
        if (rawSourceLanguage == null || rawSourceLanguage.isBlank()
                || "Unknown".equalsIgnoreCase(rawSourceLanguage.trim())) {
            return ResponseEntity.badRequest().body(
                    BaseResponse.<String>builder()
                            .success(false)
                            .message("Comic source language must be configured before requesting translation")
                            .build()
            );
        }
        final String sourceLanguage = rawSourceLanguage.trim();

        List<String> targetLanguages = request.getTargetLanguages().stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(language -> !language.isBlank())
                .filter(language -> !language.equalsIgnoreCase(sourceLanguage))
                .distinct()
                .toList();
        if (targetLanguages.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    BaseResponse.<String>builder()
                            .success(false)
                            .message("At least one target language different from the comic language is required")
                            .build()
            );
        }

        for (String targetLang : targetLanguages) {
            ProjectTeamEntity team = ProjectTeamEntity.builder()
                    .title(comicTitle + " - (" + targetLang + ")")
                    .comicName(comicTitle)
                    .status("UNCLAIMED")
                    .membersCount(0)
                    .chaptersCount(0)
                    .progress(0)
                    .leaderName(null)
                    .leaderInitials(null)
                    .deadline(request.getDeadline() != null && !request.getDeadline().isBlank() ? request.getDeadline() : "unspecified")
                    .sourceLang(sourceLanguage)
                    .targetLang(targetLang)
                    .priority(request.getPriority())
                    .notes(request.getNotes())
                    .cover("📚")
                    .description("Translation project for " + comicTitle + " from " + sourceLanguage + " to " + targetLang + ".")
                    .build();

            projectTeamRepository.save(team);
        }

        notificationService.notifyRoles(
                List.of("TRANSLATOR", "PROJECT_LEADER"),
                "New translation request",
                comicTitle + " needs translation from " + sourceLanguage
                        + " to " + String.join(", ", targetLanguages) + ".",
                "UPDATE"
        );

        return ResponseEntity.ok(
                BaseResponse.<String>builder()
                        .success(true)
                        .data("Successfully created translation requests for " + targetLanguages.size() + " languages")
                        .build()
        );
    }

    @GetMapping("/unclaimed")
    @Operation(summary = "Get unclaimed translation projects with pagination", description = "Returns paginated list of unclaimed projects")
    public ResponseEntity<PaginationResponse<List<ProjectTeamDTO>>> getUnclaimedProjects(
            @Valid @ParameterObject PaginationSearchDTO paginationDTO
    ) {
        org.springframework.data.domain.Pageable pageable = paginationDTO.toPageRequest();
        org.springframework.data.domain.Page<ProjectTeamEntity> pageResult;

        if (paginationDTO.getSearch() == null || paginationDTO.getSearch().isBlank()) {
            pageResult = projectTeamRepository.findByStatusAndDeletedFalse("UNCLAIMED", pageable);
        } else {
            org.springframework.data.jpa.domain.Specification<ProjectTeamEntity> spec =
                    (root, query, cb) -> cb.and(
                            cb.equal(cb.upper(root.get("status")), "UNCLAIMED"),
                            cb.equal(root.get("deleted"), false)
                    );
            spec = spec.and(projectTeamRepository.contains(List.of("title", "comicName", "sourceLang", "targetLang"), paginationDTO.getSearch()));
            pageResult = projectTeamRepository.findAll(spec, pageable);
        }

        List<ProjectTeamDTO> dtos = pageResult.getContent().stream()
                .map(projectTeamMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                PaginationResponse.<List<ProjectTeamDTO>>builder()
                        .metadata(
                                new PaginationMetadata(
                                        paginationDTO.getPage(),
                                        paginationDTO.getSize(),
                                        pageResult.getTotalElements(),
                                        pageResult.getTotalPages()
                                )
                        )
                        .success(true)
                        .data(dtos)
                        .build()
        );
    }

    @PutMapping("/{projectId}/claim")
    @Operation(summary = "Claim an unclaimed translation project", description = "Assigns the translator leader and sets status to ACTIVE")
    public ResponseEntity<BaseResponse<ProjectTeamDTO>> claimProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal com.sep.comiverse.security.UserPrincipal principal
    ) {
        String leaderName = "Anonymous Translator";
        String leaderInitials = "TR";
        if (principal != null && principal.user() != null) {
            var user = principal.user();
            leaderName = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
            if (leaderName != null && !leaderName.isBlank()) {
                String[] parts = leaderName.trim().split("\\s+");
                leaderInitials = java.util.Arrays.stream(parts)
                        .map(part -> part.substring(0, 1))
                        .collect(Collectors.joining())
                        .toUpperCase();
                if (leaderInitials.length() > 2) {
                    leaderInitials = leaderInitials.substring(0, 2);
                }
            }
        }

        final String finalLeaderName = leaderName;
        final String finalLeaderInitials = leaderInitials;

        var teamOpt = projectTeamRepository.findById(projectId);
        if (teamOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ProjectTeamEntity team = teamOpt.get();
        team.setStatus("ACTIVE");
        team.setLeaderName(finalLeaderName);
        team.setLeaderInitials(finalLeaderInitials);
        if (principal != null) {
            team.setLeaderId(principal.getId());
        }
        team.setMembersCount(1);
        ProjectTeamEntity saved = projectTeamRepository.save(team);

        if (principal != null) {
            notificationService.notifyUser(
                    principal.getId(),
                    "Translation project claimed",
                    "You are now leading " + saved.getTitle() + ".",
                    "UPDATE"
            );
        }

        return ResponseEntity.ok(
                BaseResponse.<ProjectTeamDTO>builder()
                        .success(true)
                        .data(projectTeamMapper.toDto(saved))
                        .build()
        );
    }
}
