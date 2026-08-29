package com.nickspicks.api.cfbd;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per outbound CFBD request. The free tier allows 1,000 calls a month,
 * so consumption needs to be a query rather than a guess.
 */
@Entity
@Table(name = "cfbd_call_log")
public class CfbdCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String endpoint;

    private Integer status;

    @Column(name = "called_at", nullable = false)
    private Instant calledAt = Instant.now();

    protected CfbdCallLog() {
    }

    public CfbdCallLog(String endpoint, Integer status) {
        this.endpoint = endpoint;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Integer getStatus() {
        return status;
    }

    public Instant getCalledAt() {
        return calledAt;
    }
}
