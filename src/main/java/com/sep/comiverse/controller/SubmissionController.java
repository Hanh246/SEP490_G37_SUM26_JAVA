package com.sep.comiverse.controller;

import com.sep.comiverse.dto.SubmissionDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.SubmissionEntity;
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
    private com.sep.comiverse.repository.IUserRepository userRepository;

    @Autowired
    private com.sep.comiverse.repository.ITeamTaskRepository teamTaskRepository;

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
            // Approving an author submission creates/updates a comic
            String title = submission.getTitle();
            String slug = title != null ? title.toLowerCase().replaceAll("[^a-z0-9]+", "-") : "";
            ComicEntity comic = comicRepository.findAllByTitle(title).stream().findFirst()
                    .or(() -> comicRepository.findAllByTitleIgnoreCase(title).stream().findFirst())
                    .or(() -> comicRepository.findAllBySlug(slug).stream().findFirst())
                    .orElse(null);

            if (comic != null) {
                comic.setLatestChapterNumber(String.valueOf(Integer.parseInt(comic.getLatestChapterNumber() == null ? "0" : comic.getLatestChapterNumber()) + 1));
                comicRepository.save(comic);
            } else {
                String authorName = submission.getSubmittedBy();
                if (authorName != null && authorName.startsWith("Author: ")) {
                    authorName = authorName.substring(8);
                }
                final String searchName = authorName;
                com.sep.comiverse.entity.UserEntity authorUser = userRepository.findAll().stream()
                        .filter(u -> searchName != null && (
                                (u.getFullName() != null && u.getFullName().equalsIgnoreCase(searchName)) ||
                                (u.getUsername() != null && u.getUsername().equalsIgnoreCase(searchName))
                        ))
                        .findFirst()
                        .orElse(null);
                UUID authorId = authorUser != null ? authorUser.getId() : null;

                ComicEntity newComic = ComicEntity.builder()
                        .title(title)
                        .slug(slug)
                        .authorId(authorId)
                        .status(com.sep.comiverse.constants.ComicStatus.ONGOING)
                        .cover(submission.getCover() != null ? submission.getCover() : "📖")
                        .build();
                comicRepository.save(newComic);
            }

            // Automatically push new chapter translation tasks to backlog of active teams translating this comic
            List<ProjectTeamEntity> activeTeams = projectTeamRepository.findAll().stream()
                    .filter(t -> t.getComicName() != null && t.getComicName().equalsIgnoreCase(title))
                    .toList();

            for (ProjectTeamEntity team : activeTeams) {
                String chapterTitle = submission.getChapter();
                if (chapterTitle == null || chapterTitle.trim().isEmpty()) {
                    chapterTitle = "Chapter 1";
                }
                String taskTitle = chapterTitle.contains("Translation") ? chapterTitle : chapterTitle + " - Translation";

                // Avoid adding duplicate tasks
                boolean taskExists = teamTaskRepository.findByProjectTeamId(team.getId()).stream()
                        .anyMatch(t -> t.getTitle() != null && t.getTitle().equalsIgnoreCase(taskTitle));

                if (!taskExists) {
                    com.sep.comiverse.entity.TeamTaskEntity task = com.sep.comiverse.entity.TeamTaskEntity.builder()
                            .projectTeamId(team.getId())
                            .title(taskTitle)
                            .columnName("backlog")
                            .progress(0)
                            .assignees("")
                            .build();
                    teamTaskRepository.save(task);
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

            if (team == null) {
                team = projectTeamRepository.findAll().stream()
                        .filter(t -> t.getComicName().equalsIgnoreCase(comicTitle))
                        .findFirst()
                        .orElse(null);
            }

            ComicEntity comic = comicRepository.findAllByTitle(comicTitle).stream().findFirst()
                    .or(() -> comicRepository.findAllByTitleIgnoreCase(comicTitle).stream().findFirst())
                    .orElse(null);

            if (team != null) {
                team.setChaptersCount(team.getChaptersCount() + 1);

                if (comic != null) {
                    // Add to Chapters table
                    ChapterEntity chapter = ChapterEntity.builder()
                            .chapterNumber(submission.getChapter() != null ? submission.getChapter().replaceAll("[^0-9]+", "") : "1")
                            .title(submission.getChapter() != null ? submission.getChapter() : "Chapter 1")
                            .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                            .comic(comic)
                            .projectTeam(team)
                            .build();
                    chapterRepository.save(chapter);
                }
                projectTeamRepository.save(team);
            }

            if (comic != null) {
                comic.setLatestChapterNumber(submission.getChapter() != null ? submission.getChapter().replaceAll("[^0-9]+", "") : "1");
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

        SubmissionEntity savedSubmission = submissionRepository.save(submission);

        return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                .success(true)
                .data(crudPlugin.getPlugin().toDto(savedSubmission))
                .build());
    }
}
