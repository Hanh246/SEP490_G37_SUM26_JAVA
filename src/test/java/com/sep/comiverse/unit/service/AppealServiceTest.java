package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.AppealResolveRequestDTO;
import com.sep.comiverse.dto.AppealTicketRequestDTO;
import com.sep.comiverse.dto.AppealTicketResponseDTO;
import com.sep.comiverse.entity.AppealTicketEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.AppealStatus;
import com.sep.comiverse.entity.enums.AppealTargetType;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.repository.IAppealTicketRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.AppealService;
import com.sep.comiverse.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppealServiceTest {

    @Mock private IAppealTicketRepository appealTicketRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IComicRepository comicRepository;
    @Mock private com.sep.comiverse.repository.IChapterRepository chapterRepository;
    @Mock private com.sep.comiverse.repository.IGenreRepository genreRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private NotificationService notificationService;
    @Mock private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    private AppealService service;

    @BeforeEach
    void setUp() {
        service = new AppealService(
                appealTicketRepository,
                userRepository,
                comicRepository,
                chapterRepository,
                genreRepository,
                modelMapper,
                new ObjectMapper(),
                notificationService,
                redisTemplate

        );
        lenient().when(comicRepository.save(any(ComicEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(modelMapper.map(any(AppealTicketEntity.class), eq(AppealTicketResponseDTO.class)))
                .thenAnswer(invocation -> {
                    AppealTicketEntity entity = invocation.getArgument(0);
                    AppealTicketResponseDTO dto = new AppealTicketResponseDTO();
                    dto.setId(entity.getId());
                    dto.setAuthorId(entity.getAuthorId());
                    dto.setTargetId(entity.getTargetId());
                    dto.setTargetType(entity.getTargetType());
                    dto.setStatus(entity.getStatus());
                    dto.setAppealReason(entity.getAppealReason());
                    dto.setResolvedByModId(entity.getResolvedByModId());
                    dto.setResolvedReason(entity.getResolvedReason());
                    return dto;
                });
    }

    @Test
    void createAppeal_rejectsExistingPendingOrApprovedAppeal() {
        AppealTicketRequestDTO request = request(AppealTargetType.COMIC_EDIT);
        when(appealTicketRepository.findByTargetIdAndStatusIn(eq(request.getTargetId()), anyList()))
                .thenReturn(Optional.of(new AppealTicketEntity()));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createAppeal(UUID.randomUUID(), request)
        );

        assertTrue(error.getMessage().contains("already exists"));
        verify(appealTicketRepository, never()).save(any());
    }

    @Test
    void createComicEditAppeal_marksComicAppealedAndNotifiesModerators() {
        UUID authorId = UUID.randomUUID();
        AppealTicketRequestDTO request = request(AppealTargetType.COMIC_EDIT);
        ComicEntity comic = ComicEntity.builder()
                .title("My Comic")
                .isModEdited(true)
                .previousStateSnapshot("{\"minimumAge\":16}")
                .build();
        comic.setId(request.getTargetId());
        when(appealTicketRepository.findByTargetIdAndStatusIn(eq(request.getTargetId()), anyList()))
                .thenReturn(Optional.empty());
        when(comicRepository.findById(request.getTargetId())).thenReturn(Optional.of(comic));
        when(appealTicketRepository.save(any(AppealTicketEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppealTicketResponseDTO response = service.createAppeal(authorId, request);

        ArgumentCaptor<AppealTicketEntity> ticketCaptor = ArgumentCaptor.forClass(AppealTicketEntity.class);
        verify(appealTicketRepository).save(ticketCaptor.capture());
        AppealTicketEntity saved = ticketCaptor.getValue();
        assertEquals(authorId, saved.getAuthorId());
        assertEquals(AppealStatus.PENDING, saved.getStatus());
        assertEquals("{\"minimumAge\":16}", saved.getPreviousStateSnapshot());
        assertTrue(comic.getIsAppealed());
        assertFalse(comic.getIsModEdited());
        assertEquals("Please restore the original", comic.getAppealReason());
        verify(comicRepository).save(comic);
        verify(notificationService).notifyRoles(
                anyList(), eq("New Comic Appeal"), contains("My Comic"),
                eq("APPEAL_CREATED"), any()
        );
        assertEquals(AppealStatus.PENDING, response.getStatus());
    }

    @Test
    void getPendingAppealByTargetId_returnsNullWhenMissing() {
        UUID targetId = UUID.randomUUID();
        when(appealTicketRepository.findByTargetIdAndStatusIn(eq(targetId), anyList()))
                .thenReturn(Optional.empty());

        assertNull(service.getPendingAppealByTargetId(targetId));
    }

    @Test
    void getAppealPages_mapEntitiesAndParticipantNames() {
        UUID authorId = UUID.randomUUID();
        UserEntity author = UserEntity.builder().fullName("Author A").email("a@b.com").build();
        AppealTicketEntity ticket = new AppealTicketEntity();
        ticket.setAuthorId(authorId);
        ticket.setStatus(AppealStatus.PENDING);
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(appealTicketRepository.findAllByAuthorId(eq(authorId), any()))
                .thenReturn(new PageImpl<>(List.of(ticket)));
        when(appealTicketRepository.findAllByStatus(eq(AppealStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(ticket)));

        assertEquals("Author A", service.getAppealsByAuthor(authorId, PageRequest.of(0, 10))
                .getContent().get(0).getAuthorName());
        assertEquals(1, service.getPendingAppeals(PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void resolveAppeal_rejectsMissingOrNonPendingTicket() {
        UUID id = UUID.randomUUID();
        AppealResolveRequestDTO request = resolve(AppealStatus.APPROVED, "ok");
        when(appealTicketRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveAppeal(id, UUID.randomUUID(), request));

        AppealTicketEntity resolved = new AppealTicketEntity();
        resolved.setStatus(AppealStatus.REJECTED);
        when(appealTicketRepository.findById(id)).thenReturn(Optional.of(resolved));
        assertThrows(IllegalStateException.class,
                () -> service.resolveAppeal(id, UUID.randomUUID(), request));
    }

    @Test
    void resolveApprovedComicEdit_revertsSnapshotAndNotifiesAuthor() {
        UUID ticketId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();

        AppealTicketEntity ticket = new AppealTicketEntity();
        ticket.setId(ticketId);
        ticket.setAuthorId(authorId);
        ticket.setTargetId(comicId);
        ticket.setTargetType(AppealTargetType.COMIC_EDIT);
        ticket.setStatus(AppealStatus.PENDING);
        ticket.setPreviousStateSnapshot("{\"minimumAge\":18,\"language\":\"ja\",\"publicationStatus\":\"ONGOING\"}");

        ComicEntity comic = ComicEntity.builder()
                .minimumAge(13)
                .language("en")
                .isAppealed(true)
                .appealReason("reason")
                .build();
        comic.setId(comicId);
        when(appealTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));
        when(appealTicketRepository.save(any(AppealTicketEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.resolveAppeal(ticketId, moderatorId, resolve(AppealStatus.APPROVED, "Accepted"));

        assertEquals(AppealStatus.APPROVED, ticket.getStatus());
        assertEquals(moderatorId, ticket.getResolvedByModId());
        assertEquals(18, comic.getMinimumAge());
        assertEquals("ja", comic.getLanguage());
        assertEquals(ComicPublicationStatus.ONGOING, comic.getPublicationStatus());
        assertFalse(comic.getIsAppealed());
        assertNull(comic.getAppealReason());
        verify(notificationService).notifyUser(
                eq(authorId), contains("Approved"), contains("Accepted"),
                eq("APPEAL_RESOLVED"), any()
        );
    }


    @Test
    void resolveRejectedComicEdit_movesPendingToRejectedWithoutRestoringPreviousSnapshot() {
        UUID ticketId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();

        AppealTicketEntity ticket = new AppealTicketEntity();
        ticket.setId(ticketId);
        ticket.setAuthorId(authorId);
        ticket.setTargetId(comicId);
        ticket.setTargetType(AppealTargetType.COMIC_EDIT);
        ticket.setStatus(AppealStatus.PENDING);
        ticket.setPreviousStateSnapshot("{\"title\":\"Original Title\"}");

        ComicEntity comic = ComicEntity.builder()
                .title("Moderator Edited Title")
                .isAppealed(true)
                .appealReason("restore")
                .isModEdited(true)
                .build();
        comic.setId(comicId);
        when(appealTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));
        when(appealTicketRepository.save(any(AppealTicketEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.resolveAppeal(ticketId, moderatorId, resolve(AppealStatus.REJECTED, "Moderation retained"));

        assertEquals(AppealStatus.REJECTED, ticket.getStatus());
        assertEquals(moderatorId, ticket.getResolvedByModId());
        assertEquals("Moderator Edited Title", comic.getTitle());
        assertFalse(comic.getIsAppealed());
        assertNull(comic.getAppealReason());
        assertTrue(comic.getIsModEdited());
        verify(notificationService).notifyUser(
                eq(authorId), contains("Rejected"), contains("Moderation retained"),
                eq("APPEAL_RESOLVED"), any());
    }

    private AppealTicketRequestDTO request(AppealTargetType type) {
        AppealTicketRequestDTO request = new AppealTicketRequestDTO();
        request.setTargetId(UUID.randomUUID());
        request.setTargetType(type);
        request.setAppealReason("Please restore the original");
        request.setEvidenceUrls("https://evidence.test/1");
        return request;
    }

    private AppealResolveRequestDTO resolve(AppealStatus status, String reason) {
        AppealResolveRequestDTO dto = new AppealResolveRequestDTO();
        dto.setStatus(status);
        dto.setResolvedReason(reason);
        return dto;
    }
}
