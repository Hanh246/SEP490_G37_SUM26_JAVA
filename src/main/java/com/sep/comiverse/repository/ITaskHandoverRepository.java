package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TaskHandoverEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ITaskHandoverRepository extends JpaRepository<TaskHandoverEntity, UUID> {
    List<TaskHandoverEntity> findAllByTaskIdOrderByHandedOverAtAsc(UUID taskId);
}
