package com.nickspicks.api.cron;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CronJobRepository extends JpaRepository<CronJob, String> {

    List<CronJob> findAllByOrderByNameAsc();
}
