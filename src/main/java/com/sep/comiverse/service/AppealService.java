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

@Service
@RequiredArgsConstructor
@Slf4j
public class AppealService {

    private final IAppealTicketRepository appealTicketRepository;
    private final IUserRepository userRepository;
    private final IComicRepository comicRepository;
    private final com.sep.comiverse.repository.IChapterRepository chapterRepository;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

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
                    if (comic.getModerationStatus() == ComicModerationStatus.REJECTED || comic.getModerationStatus() == ComicModerationStatus.NEEDS_CHANGES) {
                        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
                    }
                    comic.setRejectionReason(null);

                    if (entity.getPreviousStateSnapshot() != null && !entity.getPreviousStateSnapshot().isEmpty()) {
                        try {
                            ComicEntity snapshot = objectMapper.readValue(entity.getPreviousStateSnapshot(), ComicEntity.class);
                            
                            // Revert editable fields
                            if (snapshot.getTitle() != null) comic.setTitle(snapshot.getTitle());
                            if (snapshot.getSummary() != null) comic.setSummary(snapshot.getSummary());
                            if (snapshot.getCover() != null) comic.setCover(snapshot.getCover());
                            if (snapshot.getGenres() != null) comic.setGenres(snapshot.getGenres());
                            if (snapshot.getPublicationStatus() != null) comic.setPublicationStatus(snapshot.getPublicationStatus());
                            if (snapshot.getMinimumAge() != null) comic.setMinimumAge(snapshot.getMinimumAge());
                            if (snapshot.getLanguage() != null) comic.setLanguage(snapshot.getLanguage());
                            
                            log.info("Successfully reverted comic {} from appeal ticket {}", comic.getId(), entity.getId());
                        } catch (Exception e) {
                            log.error("Failed to deserialize or revert comic state from appeal snapshot", e);
                        }
                    }
                }
                
                comicRepository.save(comic);
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
