package com.aivideo.pipeline.config;

import com.aivideo.pipeline.domain.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Keeps the PostgreSQL enum-like CHECK constraint aligned with the Java enum. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaMigration {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void updateVideoJobStatusConstraint() {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;

            String values = Arrays.stream(JobStatus.values())
                    .map(status -> "'" + status.name() + "'")
                    .collect(Collectors.joining(", "));
            jdbcTemplate.execute("ALTER TABLE video_jobs DROP CONSTRAINT IF EXISTS video_jobs_status_check");
            jdbcTemplate.execute("ALTER TABLE video_jobs ADD CONSTRAINT video_jobs_status_check CHECK (status IN (" + values + "))");
            log.info("Đã đồng bộ video_jobs_status_check với {} trạng thái", JobStatus.values().length);
        } catch (Exception e) {
            throw new IllegalStateException("Không cập nhật được video_jobs_status_check", e);
        }
    }
}
