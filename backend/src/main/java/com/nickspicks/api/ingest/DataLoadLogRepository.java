package com.nickspicks.api.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataLoadLogRepository extends JpaRepository<DataLoadLog, Long> {

    List<DataLoadLog> findTop200ByOrderByStartedAtDesc();
}
