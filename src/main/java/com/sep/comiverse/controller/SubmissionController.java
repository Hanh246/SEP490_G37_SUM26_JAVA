package com.sep.comiverse.controller;

import com.sep.comiverse.dto.SubmissionDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicStatus;
import com.sep.comiverse.plugin.crud.SubmissionCrudPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/submissions")
public class SubmissionController extends BaseController<SubmissionEntity, SubmissionDTO, UUID, PaginationSearchDTO> {

    @Autowired
    private ISubmissionRepository submissionRepository;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    private IProjectTeamRepository projectTeamRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    public SubmissionController(SubmissionCrudPlugin crud) {
        super(crud, SubmissionEntity.class);
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<SubmissionDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<SubmissionDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }

    @PutMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<BaseResponse<SubmissionDTO>> approve(@PathVariable UUID id) {
        SubmissionEntity submission = submissionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Submission with id " + id + " not found"));

        submission.setStatus("approved");
        SubmissionEntity savedSubmission = submissionRepository.save(submission);

        // Process side effects of approval
        if ("author".equalsIgnoreCase(submission.getQueueType())) {
            if (submission.getChapterId() != null) {
                ChapterEntity chapter = chapterRepository.findById(submission.getChapterId())
                        .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chapter with id " + submission.getChapterId() + " not found"));
                chapter.setStatus(ChapterStatus.APPROVED);
                chapter.setApprovedAt(new java.util.Date());
                chapterRepository.save(chapter);

                comicRepository.findById(submission.getComicId()).ifPresent(comic -> {
                    comic.setModerationStatus(ComicStatus.PUBLISHED.name());
                    comic.setChapters((comic.getChapters() == null ? 0 : comic.getChapters()) + 1);
                    if (comic.getPublishedAt() == null) {
                        comic.setPublishedAt(new java.util.Date());
                    }
                    comicRepository.save(comic);
                });
            } else {
                // Backward-compatible approval for old author submissions without chapter linkage.
                String title = submission.getTitle();
                ComicEntity comic = comicRepository.findByTitle(title).orElse(null);
                if (comic != null) {
                    comic.setChapters((comic.getChapters() == null ? 0 : comic.getChapters()) + 1);
                    comicRepository.save(comic);
                } else {
                    String authorName = submission.getSubmittedBy();
                    if (authorName != null && authorName.startsWith("Author: ")) {
                        authorName = authorName.substring(8);
                    }
                    ComicEntity newComic = ComicEntity.builder()
                            .title(title)
                            .author(authorName)
                            .projectTeam("-")
                            .chapters(1)
                            .views("0")
                            .status("Ongoing")
                            .moderationStatus(ComicStatus.PUBLISHED.name())
                            .genres("Action, Fantasy")
                            .cover(submission.getCover() != null ? submission.getCover() : "📖")
                            .coverImageUrl(submission.getCover())
                            .publishedAt(new java.util.Date())
                            .build();
                    comicRepository.save(newComic);
                }
            }
        } else if ("translator".equalsIgnoreCase(submission.getQueueType())) {
            // Approving a translator submission increments project chapters & adds a chapter record
            String teamName = submission.getSubmittedBy();
            String comicTitle = submission.getTitle();

            ProjectTeamEntity team = projectTeamRepository.findAll().stream()
                    .filter(t -> t.getTitle().equalsIgnoreCase(teamName))
                    .findFirst()
                    .orElse(null);

            if (team != null) {
                team.setChaptersCount(team.getChaptersCount() + 1);

                // Add to Chapters table
                ChapterEntity chapter = ChapterEntity.builder()
                        .num(submission.getChapter())
                        .date("Just now")
                        .words(submission.getWords() != null ? submission.getWords() : 3000)
                        .content(submission.getContent())
                        .projectTeam(team)
                        .build();
                chapterRepository.save(chapter);
                projectTeamRepository.save(team);
            }

            ComicEntity comic = comicRepository.findByTitle(comicTitle).orElse(null);
            if (comic != null) {
                comic.setChapters(comic.getChapters() + 1);
                comicRepository.save(comic);
            }
        }

        return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                .success(true)
                .data(crudPlugin.getPlugin().toDto(savedSubmission))
                .build());
    }

    @PutMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<BaseResponse<SubmissionDTO>> reject(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        SubmissionEntity submission = submissionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Submission with id " + id + " not found"));

        String reason = body != null ? body.getOrDefault("reason", "No reason provided.") : "No reason provided.";
        submission.setStatus("rejected");
        submission.setRejectionReason(reason);

        if ("author".equalsIgnoreCase(submission.getQueueType()) && submission.getChapterId() != null) {
            chapterRepository.findById(submission.getChapterId()).ifPresent(chapter -> {
                chapter.setStatus(ChapterStatus.REJECTED);
                chapter.setRejectedAt(new java.util.Date());
                chapter.setModerationNote(reason);
                chapterRepository.save(chapter);
            });
        }

        SubmissionEntity savedSubmission = submissionRepository.save(submission);

        return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                .success(true)
                .data(crudPlugin.getPlugin().toDto(savedSubmission))
                .build());
    }
}
