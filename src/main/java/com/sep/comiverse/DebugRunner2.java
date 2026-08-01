package com.sep.comiverse;

import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class DebugRunner2 implements CommandLineRunner {
    private final IChapterRepository chapterRepository;
    private final ISubmissionRepository submissionRepository;

    public DebugRunner2(IChapterRepository chapterRepository, ISubmissionRepository submissionRepository) {
        this.chapterRepository = chapterRepository;
        this.submissionRepository = submissionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("DEBUG RUNNER 2 STARTING======================================");
        UUID id = UUID.fromString("019fb3ba-799b-7e5d-9b26-2fcc702fb565");
        
        ChapterEntity chapter = chapterRepository.findById(id).orElse(null);
        if (chapter != null) {
            System.out.println("IT IS A CHAPTER! Title: " + chapter.getTitle());
        } else {
            System.out.println("NOT A CHAPTER!");
        }
        
        SubmissionEntity submission = submissionRepository.findById(id).orElse(null);
        if (submission != null) {
            System.out.println("IT IS A SUBMISSION! Title: " + submission.getTitle() + ", chapterId: " + submission.getChapterId());
        } else {
            System.out.println("NOT A SUBMISSION!");
        }
        
        System.out.println("DEBUG RUNNER 2 ENDING======================================");
    }
}
