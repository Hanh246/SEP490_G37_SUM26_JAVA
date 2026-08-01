package com.sep.comiverse.repository;

import com.sep.comiverse.entity.BaseEntity;
import com.sep.comiverse.specification.FuzzySearchSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface AbstractCrudRepository <T extends BaseEntity, ID>
        extends JpaRepository<T, ID>, JpaSpecificationExecutor<T>, FuzzySearchSpecification<T> {
    @Query("SELECT e FROM #{#entityName} e WHERE e.deleted = false")
    List<T> findAll();

    @Query("SELECT e FROM #{#entityName} e WHERE e.deleted = false")
    Page<T> findAll(Pageable pageable);

    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.deleted = false")
    Optional<T> findById(@Param("id") ID id);

    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.deleted = false")
    long count();

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM #{#entityName} e WHERE e.id = :id AND e.deleted = false")
    boolean existsById(@Param("id") ID id);

    @Query("UPDATE #{#entityName} e SET e.deleted = true, e.updatedAt = CURRENT_INSTANT WHERE e.id = :id")
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    void softDeleteById(@Param("id") ID id);
}
