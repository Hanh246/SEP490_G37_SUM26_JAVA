package com.sep.comiverse.specification;

import com.sep.comiverse.dto.request.ComicExploreRequestDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ComicSpecification {

    public static Specification<ComicEntity> filterCursorComics(ComicExploreRequestDTO request, String targetProperty, boolean isTimeField) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("deleted"), false));
            predicates.add(criteriaBuilder.equal(root.get("moderationStatus"), ComicModerationStatus.PUBLISHED));

            if (request.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), request.getStatus()));
            }

            if (!CollectionUtils.isEmpty(request.getGenres()) && !request.getGenres().contains("All")) {
                for (UUID genreId : request.getGenres()) {
                    Join<ComicEntity, GenreEntity> genreJoin = root.join("genres");
                    predicates.add(criteriaBuilder.equal(genreJoin.get("id"), genreId));
                }
            }

            if (request.getCursor() != null && !request.getCursor().trim().isEmpty() && request.getReferenceId() != null) {
                Predicate cursorPredicate;

                if (isTimeField) {
                    Instant cursorTime = Instant.parse(request.getCursor().trim());

                    Predicate lessTime = criteriaBuilder.lessThan(root.get(targetProperty), cursorTime);
                    Predicate equalTime = criteriaBuilder.equal(root.get(targetProperty), cursorTime);
                    Predicate lessId = criteriaBuilder.lessThan(root.get("id"), request.getReferenceId());

                    cursorPredicate = criteriaBuilder.or(lessTime, criteriaBuilder.and(equalTime, lessId));
                } else {
                    Long cursorNumber = Long.parseLong(request.getCursor());

                    Predicate lessNum = criteriaBuilder.lessThan(root.get(targetProperty), cursorNumber);
                    Predicate equalNum = criteriaBuilder.equal(root.get(targetProperty), cursorNumber);
                    Predicate lessId = criteriaBuilder.lessThan(root.get("id"), request.getReferenceId());

                    cursorPredicate = criteriaBuilder.or(lessNum, criteriaBuilder.and(equalNum, lessId));
                }

                predicates.add(cursorPredicate);
            }

            query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
