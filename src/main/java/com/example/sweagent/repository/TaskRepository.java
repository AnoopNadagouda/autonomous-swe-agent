package com.example.sweagent.repository;

import com.example.sweagent.model.TaskRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<TaskRecord, String> {
}
