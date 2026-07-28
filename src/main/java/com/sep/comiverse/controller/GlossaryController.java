package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.GlossarySuggestRequest;
import com.sep.comiverse.dto.response.GlossarySuggestionResponse;
import com.sep.comiverse.entity.GlossaryTermEntity;
import com.sep.comiverse.repository.IGlossaryTermRepository;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.GlossarySuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/glossary")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GlossaryController {

    private final IGlossaryTermRepository glossaryTermRepository;
    private final GlossarySuggestionService glossarySuggestionService;

    @GetMapping("/comic/{comicId}")
    public ResponseEntity<List<GlossaryTermEntity>> getTerms(@PathVariable UUID comicId) {
        return ResponseEntity.ok(glossaryTermRepository.findByComicIdOrderByCreatedAtDesc(comicId));
    }

    @PostMapping("/comic/{comicId}")
    public ResponseEntity<?> createTerm(
            @PathVariable UUID comicId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String source = body.getOrDefault("source", "").trim();
        String target = body.getOrDefault("target", "").trim();
        if (source.isEmpty() || target.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "source and target are required"));
        }

        GlossaryTermEntity term = GlossaryTermEntity.builder()
                .comicId(comicId)
                .source(source)
                .target(target)
                .note(body.getOrDefault("note", ""))
                .createdBy(principal != null ? principal.getId() : null)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(glossaryTermRepository.save(term));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTerm(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        GlossaryTermEntity term = glossaryTermRepository.findById(id).orElse(null);
        if (term == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Term not found"));
        }
        if (body.containsKey("source")) {
            term.setSource(body.get("source").trim());
        }
        if (body.containsKey("target")) {
            term.setTarget(body.get("target").trim());
        }
        if (body.containsKey("note")) {
            term.setNote(body.get("note"));
        }
        return ResponseEntity.ok(glossaryTermRepository.save(term));
    }

    @PostMapping("/comic/{comicId}/suggest")
    public ResponseEntity<?> suggestTerms(
            @PathVariable UUID comicId,
            @RequestBody GlossarySuggestRequest request
    ) {
        try {
            GlossarySuggestionResponse response = glossarySuggestionService.suggestFromPage(comicId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTerm(@PathVariable UUID id) {
        if (!glossaryTermRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Term not found"));
        }
        glossaryTermRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}