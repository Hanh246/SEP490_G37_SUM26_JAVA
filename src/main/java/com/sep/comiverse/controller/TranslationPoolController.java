package com.sep.comiverse.controller;

import com.sep.comiverse.dto.TranslationRequestDTO;
import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.plugin.mapper.ProjectTeamMapperPlugin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    private final ProjectTeamMapperPlugin projectTeamMapper;

    @PostMapping("/request")
    @Operation(summary = "Submit translation requests", description = "Creates a separate unclaimed translation project for each target language")
    public ResponseEntity<BaseResponse<String>> requestTranslation(@RequestBody TranslationRequestDTO request) {
        if (request.getTargetLanguages() == null || request.getTargetLanguages().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    BaseResponse.<String>builder()
                            .success(false)
                            .message("Target languages cannot be empty")
                            .build()
            );
        }

        for (String targetLang : request.getTargetLanguages()) {
            ProjectTeamEntity team = ProjectTeamEntity.builder()
                    .title(request.getComicTitle() + " - (" + targetLang + ")")
                    .comicName(request.getComicTitle())
                    .status("UNCLAIMED")
                    .membersCount(0)
                    .chaptersCount(0)
                    .progress(0)
                    .leaderName(null)
                    .leaderInitials(null)
                    .deadline(request.getDeadline() != null && !request.getDeadline().isBlank() ? request.getDeadline() : "unspecified")
                    .sourceLang(request.getSourceLang())
                    .targetLang(targetLang)
                    .priority(request.getPriority())
                    .notes(request.getNotes())
                    .cover("📚")
                    .description("Translation project for " + request.getComicTitle() + " from " + request.getSourceLang() + " to " + targetLang + ".")
                    .assignedToMe(false)
                    .build();

            projectTeamRepository.save(team);
        }

        return ResponseEntity.ok(
                BaseResponse.<String>builder()
                        .success(true)
                        .data("Successfully created translation requests for " + request.getTargetLanguages().size() + " languages")
                        .build()
        );
    }

    @GetMapping("/unclaimed")
    @Operation(summary = "Get unclaimed translation projects", description = "Returns all projects with status UNCLAIMED")
    public ResponseEntity<BaseResponse<List<ProjectTeamDTO>>> getUnclaimedProjects() {
        List<ProjectTeamEntity> unclaimed = projectTeamRepository.findAll().stream()
                .filter(t -> "UNCLAIMED".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.toList());

        List<ProjectTeamDTO> dtos = unclaimed.stream()
                .map(projectTeamMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                BaseResponse.<List<ProjectTeamDTO>>builder()
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
        team.setMembersCount(1);
        ProjectTeamEntity saved = projectTeamRepository.save(team);

        return ResponseEntity.ok(
                BaseResponse.<ProjectTeamDTO>builder()
                        .success(true)
                        .data(projectTeamMapper.toDto(saved))
                        .build()
        );
    }
}
