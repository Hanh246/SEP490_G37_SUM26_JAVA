package com.sep.comiverse.common.specification;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface FuzzySearchSpecification<T> {

    default Specification<T> contains(List<String> fields, String value) {
        return (root, query, cb) -> {
            String pattern = "%" + value.toLowerCase() + "%";
            return cb.or(fields.stream()
                    .map(field -> cb.like(cb.lower(root.get(field)), pattern))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    // Optional: overload for convenience
    default Specification<T> contains(String value, String... fields) {
        return contains(List.of(fields), value);
    }
}