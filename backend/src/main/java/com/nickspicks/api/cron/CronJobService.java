package com.nickspicks.api.cron;

import com.nickspicks.api.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reading and flipping the scheduled jobs.
 *
 * <p>Rows are seeded by migration rather than created here: a job exists
 * because there is code and a schedule for it, which is not something an API
 * call can bring into being.
 */
@Service
public class CronJobService {

    private final CronJobRepository jobs;

    public CronJobService(CronJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public List<CronJob> all() {
        return jobs.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public CronJob require(String name) {
        return jobs.findById(name)
                .orElseThrow(() -> new NotFoundException("No cron job called %s".formatted(name)));
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String name) {
        return jobs.findById(name).map(CronJob::isEnabled).orElse(false);
    }

    @Transactional
    public CronJob setEnabled(String name, boolean enabled) {
        CronJob job = require(name);
        job.setEnabled(enabled);
        return jobs.save(job);
    }

    /** The "stop everything" button, and its counterpart. */
    @Transactional
    public List<CronJob> setAllEnabled(boolean enabled) {
        List<CronJob> all = jobs.findAllByOrderByNameAsc();
        all.forEach(job -> job.setEnabled(enabled));
        return jobs.saveAll(all);
    }

    /**
     * Records the outcome of a run.
     *
     * <p>Its own transaction: a failed refresh rolls its own work back, and
     * the record that it failed must survive that rollback or the admin page
     * would show a job that had apparently never run.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String name, CronJob.Status status, String detail) {
        jobs.findById(name).ifPresent(job -> {
            job.record(status, detail);
            jobs.save(job);
        });
    }
}
