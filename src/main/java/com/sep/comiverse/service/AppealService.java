package com.sep.comiverse.service;

import com.sep.comiverse.dto.AppealResolveRequestDTO;
import com.sep.comiverse.dto.AppealTicketRequestDTO;
import com.sep.comiverse.dto.AppealTicketResponseDTO;
import com.sep.comiverse.entity.AppealTicketEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.AppealStatus;
import com.sep.comiverse.repository.IAppealTicketRepository;
import com.sep.comiverse.repository.IUserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.enums.AppealTargetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.dto.ComicDTO;
import lombok.extern.slf4j.Slf4j;

import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppealService {

    private final IAppealTicketRepository appealTicketRepository;
    private final IUserRepository userRepository;
    private final IComicRepository comicRepository;
    private final com.sep.comiverse.repository.IChapterRepository chapterRepository;
    private final IGenreRepository genreRepository;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public AppealTicketResponseDTO createAppeal(UUID authorId, AppealTicketRequestDTO requestDTO) {
        // Check if there is already a pending or approved appeal for this target
        appealTicketRepository.findByTargetIdAndStatusIn(
                requestDTO.getTargetId(), 
                Arrays.asList(AppealStatus.PENDING)
        ).ifPresent(ticket -> {
            throw new IllegalArgumentException("An active appeal already exists for this item.");
        });

        AppealTicketEntity entity = new AppealTicketEntity();
        entity.setAuthorId(authorId);
        entity.setTargetId(requestDTO.getTargetId());
        entity.setTargetType(requestDTO.getTargetType());
        entity.setAppealReason(requestDTO.getAppealReason());
        entity.setEvidenceUrls(requestDTO.getEvidenceUrls());
        entity.setStatus(AppealStatus.PENDING);
        
        if (requestDTO.getTargetType() == AppealTargetType.COMIC_EDIT) {
            comicRepository.findById(requestDTO.getTargetId()).ifPresent(comic -> {
                if (comic.getPreviousStateSnapshot() != null) {
                    entity.setPreviousStateSnapshot(comic.getPreviousStateSnapshot());
                } else {
                    try {
                        ComicDTO comicDto = modelMapper.map(comic, ComicDTO.class);
                        entity.setPreviousStateSnapshot(objectMapper.writeValueAsString(comicDto));
                    } catch (Exception e) {
                        log.error("Failed to serialize comic state for appeal", e);
                    }
                }
                
                // Update comic state
                comic.setIsAppealed(true);
                comic.setAppealReason(requestDTO.getAppealReason());
                
                // Clear the mod edit notice flag since they have appealed it
                if (Boolean.TRUE.equals(comic.getIsModEdited())) {
                    comic.setIsModEdited(false);
                }
                comicRepository.save(comic);
            });
        }

        AppealTicketEntity savedEntity = appealTicketRepository.save(entity);

        // Notify Moderators
        String comicTitle = requestDTO.getTargetType() == AppealTargetType.COMIC_EDIT ? 
            comicRepository.findById(requestDTO.getTargetId()).map(com.sep.comiverse.entity.ComicEntity::getTitle).orElse("Comic") : "Item";
        
        notificationService.notifyRoles(
            java.util.Arrays.asList("MODERATOR", "ADMIN"),
            "New Comic Appeal",
            "Author appealed rejection for: " + comicTitle,
            "APPEAL_CREATED",
            NotificationPreferenceKey.REVIEW_QUEUE
        );

        return mapToResponseDTO(savedEntity);
    }



    public Page<AppealTicketResponseDTO> getAppealsByAuthor(UUID authorId, Pageable pageable) {
        return appealTicketRepository.findAllByAuthorId(authorId, pageable)
                .map(this::mapToResponseDTO);
    }

    @Transactional
    public void processSlaExpiredAppeals() {
        java.time.Instant cutoff = java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS);
        Page<AppealTicketEntity> pendingPage = appealTicketRepository.findAllByStatus(AppealStatus.PENDING, Pageable.unpaged());
        for (AppealTicketEntity entity : pendingPage.getContent()) {
            if (entity.getCreatedAt() != null && entity.getCreatedAt().isBefore(cutoff)) {
                try {
                    AppealResolveRequestDTO autoReq = new AppealResolveRequestDTO();
                    autoReq.setStatus(AppealStatus.APPROVED);
                    autoReq.setResolvedReason("Auto-approved by 3-Day SLA Author Protection Policy. Original content restored.");
                    resolveAppeal(entity.getId(), null, autoReq);
                    log.info("Auto-approved expired appeal ticket {}", entity.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-resolve SLA expired appeal ticket {}", entity.getId(), e);
                }
            }
        }
    }

    public Page<AppealTicketResponseDTO> getPendingAppeals(Pageable pageable) {
        try {
            processSlaExpiredAppeals();
        } catch (Exception e) {
            log.warn("SLA check execution encountered an issue:", e);
        }
        return appealTicketRepository.findAllByStatus(AppealStatus.PENDING, pageable)
                .map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public AppealTicketResponseDTO getPendingAppealByTargetId(UUID targetId) {
        AppealTicketEntity entity = appealTicketRepository.findByTargetIdAndStatusIn(
                targetId, 
                Arrays.asList(AppealStatus.PENDING)
        ).orElse(null);
        if (entity == null) {
            return null;
        }
        return mapToResponseDTO(entity);
    }



    @Transactional
    public AppealTicketResponseDTO resolveAppeal(UUID ticketId, UUID moderatorId, AppealResolveRequestDTO requestDTO) {
        AppealTicketEntity entity = appealTicketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Appeal ticket not found"));

        if (entity.getStatus() != AppealStatus.PENDING) {
            throw new IllegalStateException("Only pending appeals can be resolved");
        }

        entity.setStatus(requestDTO.getStatus());
        entity.setResolvedByModId(moderatorId);
        entity.setResolvedReason(requestDTO.getResolvedReason());

        // Revert logic for COMIC_EDIT
        if (entity.getTargetType() == AppealTargetType.COMIC_EDIT) {
            comicRepository.findById(entity.getTargetId()).ifPresent(comic -> {
                comic.setIsAppealed(false);
                comic.setAppealReason(null);
                
                if (requestDTO.getStatus() == AppealStatus.APPROVED) {
                    log.info("[APPEAL RESTORE] Starting restore for comic {} from ticket {}", comic.getId(), entity.getId());
                    
                    if (comic.getModerationStatus() == ComicModerationStatus.REJECTED || comic.getModerationStatus() == ComicModerationStatus.NEEDS_CHANGES) {
                        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
                    }
                    comic.setRejectionReason(null);

                    String snapshotJson = entity.getPreviousStateSnapshot();
                    log.info("[APPEAL RESTORE] Snapshot JSON (first 500 chars): {}", 
                        snapshotJson != null ? snapshotJson.substring(0, Math.min(500, snapshotJson.length())) : "NULL");

                    if (snapshotJson != null && !snapshotJson.isEmpty()) {
                        try {
                            JsonNode root = objectMapper.readTree(snapshotJson);
                            
                            // Revert editable fields
                            if (root.has("title")) {
                                String oldTitle = comic.getTitle();
                                String newTitle = root.get("title").isNull() ? null : root.get("title").asText();
                                comic.setTitle(newTitle);
                                log.info("[APPEAL RESTORE] Title: '{}' -> '{}'", oldTitle, newTitle);
                            }
                            if (root.has("summary")) comic.setSummary(root.get("summary").isNull() ? null : root.get("summary").asText());
                            if (root.has("cover")) comic.setCover(root.get("cover").isNull() ? null : root.get("cover").asText());
                            if (root.has("language")) {
                                String oldLang = comic.getLanguage();
                                String newLang = root.get("language").isNull() ? "Unknown" : root.get("language").asText();
                                comic.setLanguage(newLang);
                                log.info("[APPEAL RESTORE] Language: '{}' -> '{}'", oldLang, newLang);
                            }
                            if (root.has("minimumAge")) comic.setMinimumAge(root.get("minimumAge").isNull() ? null : root.get("minimumAge").asInt());
                            
                            if (root.has("publicationStatus")) {
                                try {
                                    String oldStatus = comic.getPublicationStatus() != null ? comic.getPublicationStatus().name() : "null";
                                    String newStatus = root.get("publicationStatus").isNull() ? null : root.get("publicationStatus").asText().toUpperCase();
                                    comic.setPublicationStatus(newStatus == null ? null : ComicPublicationStatus.valueOf(newStatus));
                                    log.info("[APPEAL RESTORE] PublicationStatus: '{}' -> '{}'", oldStatus, newStatus);
                                } catch (Exception e) {
                                    log.warn("[APPEAL RESTORE] Failed to parse publicationStatus", e);
                                }
                            } else if (root.has("publication_status")) {
                                try {
                                    comic.setPublicationStatus(root.get("publication_status").isNull() ? null : ComicPublicationStatus.valueOf(root.get("publication_status").asText().toUpperCase()));
                                } catch (Exception ignored) {}
                            }

                            // Revert genres
                            if (genreRepository != null) {
                                Set<UUID> targetGenreIds = new HashSet<>();
                                if (root.has("genres") && root.get("genres").isArray()) {
                                    log.info("[APPEAL RESTORE] Found 'genres' array with {} elements", root.get("genres").size());
                                    for (JsonNode gNode : root.get("genres")) {
                                        log.info("[APPEAL RESTORE] Genre node: {}", gNode.toString());
                                        if (gNode.isObject() && gNode.hasNonNull("id")) {
                                            try {
                                                targetGenreIds.add(UUID.fromString(gNode.get("id").asText()));
                                            } catch (Exception e) {
                                                log.warn("[APPEAL RESTORE] Failed to parse genre id from object: {}", gNode, e);
                                            }
                                        } else if (gNode.isTextual()) {
                                            try {
                                                targetGenreIds.add(UUID.fromString(gNode.asText()));
                                            } catch (Exception e) {
                                                log.warn("[APPEAL RESTORE] Failed to parse genre id from text: {}", gNode.asText(), e);
                                            }
                                        }
                                    }
                                } else if (root.has("genreIds") && root.get("genreIds").isArray()) {
                                    log.info("[APPEAL RESTORE] Found 'genreIds' array with {} elements", root.get("genreIds").size());
                                    for (JsonNode gNode : root.get("genreIds")) {
                                        try {
                                            targetGenreIds.add(UUID.fromString(gNode.asText()));
                                        } catch (Exception e) {
                                            log.warn("[APPEAL RESTORE] Failed to parse genreId: {}", gNode.asText(), e);
                                        }
                                    }
                                } else {
                                    log.warn("[APPEAL RESTORE] No 'genres' or 'genreIds' array found in snapshot. Keys: {}", root.fieldNames());
                                }
                                
                                log.info("[APPEAL RESTORE] Resolved {} genre UUIDs to restore: {}", targetGenreIds.size(), targetGenreIds);
                                
                                if (targetGenreIds.isEmpty()) {
                                    comic.setGenres(new HashSet<>());
                                } else {
                                    List<GenreEntity> genreEntities = genreRepository.findAllById(targetGenreIds);
                                    log.info("[APPEAL RESTORE] Found {} genre entities from DB for {} IDs", genreEntities.size(), targetGenreIds.size());
                                    comic.setGenres(new HashSet<>(genreEntities));
                                }
                            }
                            
                            log.info("[APPEAL RESTORE] Successfully prepared revert for comic {} from appeal ticket {}", comic.getId(), entity.getId());
                        } catch (Exception e) {
                            log.error("[APPEAL RESTORE] Failed to deserialize or revert comic state from appeal snapshot", e);
                        }
                    } else {
                        log.warn("[APPEAL RESTORE] No snapshot found on appeal ticket {} for comic {}", entity.getId(), comic.getId());
                    }

                    // Reset Mod Edit flags so comic is restored cleanly
                    comic.setIsModEdited(false);
                    comic.setPreviousStateSnapshot(null);
                }
                
                ComicEntity saved = comicRepository.save(comic);
                log.info("[APPEAL RESTORE] Saved comic. Language='{}', PublicationStatus='{}', Genres={}", 
                    saved.getLanguage(), saved.getPublicationStatus(), 
                    saved.getGenres() != null ? saved.getGenres().size() + " genres" : "null");
                try {
                    redisTemplate.delete("comic:detail:v2:" + comic.getId());
                } catch (Exception e) {
                    log.warn("Failed to evict comic cache after appeal resolution", e);
                }
            });
        } else if (entity.getTargetType() == AppealTargetType.CHAPTER_SUSPEND && requestDTO.getStatus() == AppealStatus.APPROVED) {
            if (chapterRepository != null) {
                chapterRepository.findById(entity.getTargetId()).ifPresent(chap -> {
                    chap.setModerationStatus(com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED);
                    chap.setApprovedById(moderatorId);
                    chap.setApprovedAt(java.time.Instant.now());
                    chap.setRejectionReason(null);
                    chapterRepository.save(chap);
                });
            }
        }
        
        AppealTicketEntity savedEntity = appealTicketRepository.save(entity);
        
        String notifTitle = "Appeal " + (requestDTO.getStatus() == AppealStatus.APPROVED ? "Approved" : "Rejected");
        String notifMsg = "Your appeal regarding " + entity.getTargetType() + " has been " + requestDTO.getStatus() + ".";
        if (requestDTO.getResolvedReason() != null && !requestDTO.getResolvedReason().isEmpty()) {
            notifMsg += " Reason: " + requestDTO.getResolvedReason();
        }
        notificationService.notifyUser(entity.getAuthorId(), notifTitle, notifMsg, "APPEAL_RESOLVED", NotificationPreferenceKey.SUBMISSION_STATUS);
        
        return mapToResponseDTO(savedEntity);
    }

    private AppealTicketResponseDTO mapToResponseDTO(AppealTicketEntity entity) {
        AppealTicketResponseDTO dto = modelMapper.map(entity, AppealTicketResponseDTO.class);
        
        userRepository.findById(entity.getAuthorId())
                .ifPresent(user -> dto.setAuthorName(user.getFullName()));
                
        if (entity.getResolvedByModId() != null) {
            userRepository.findById(entity.getResolvedByModId())
                    .ifPresent(mod -> dto.setResolvedByModName(mod.getFullName()));
        }
        
        if (entity.getTargetId() != null) {
            comicRepository.findById(entity.getTargetId())
                    .ifPresent(comic -> dto.setTargetName(comic.getTitle()));
            if (dto.getTargetName() == null && chapterRepository != null) {
                chapterRepository.findById(entity.getTargetId())
                        .ifPresent(chap -> dto.setTargetName(chap.getTitle() != null && !chap.getTitle().isBlank() 
                                ? chap.getTitle() 
                                : "Chapter " + chap.getChapterNumber()));
            }
        }
        
        return dto;
    }
}
