package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v2/comics")
@RequiredArgsConstructor
public class ComicV2Controller {

    private final ComicCrudPlugin comicCrudPlugin;

    /**
     * Public comic detail by slug (v2 SEO API).
     */
    @GetMapping("/{slug}")
    @Operation(summary = "Get public comic detail by slug (v2 SEO API)")
    public ResponseEntity<BaseResponse<ComicDTO>> findBySlug(
            @PathVariable
            @Parameter(required = true)
            String slug
    ) {
        ComicDTO data = comicCrudPlugin.getComicDetailBySlug(slug);

        return ResponseEntity.ok(BaseResponse.<ComicDTO>builder()
                .success(true)
                .data(data)
                .build());
    }

    /**
     * Generate / backfill slugs for existing comics.
     */
    @PostMapping("/generate-slugs")
    @Operation(summary = "Generate slugs for existing comics (v2 API)")
    public ResponseEntity<BaseResponse<Map<String, Object>>> generateSlugs(
            @RequestParam(defaultValue = "false") boolean force
    ) {
        Map<String, Object> result = comicCrudPlugin.generateSlugsForExistingComics(force);

        return ResponseEntity.ok(BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .build());
    }
}
