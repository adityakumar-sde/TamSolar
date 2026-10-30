package com.tamsolar.inquiry;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class InquiryRepository {

    private final JdbcTemplate jdbcTemplate;

    public InquiryRepository(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long create(String name, String email, String customerType, String enquiryType, String message) {
        return create(name, email, null, customerType, enquiryType, message);
    }

    public Long create(String name, String email, String phone, String customerType, String enquiryType, String message) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO inquiries (name, email, phone, customer_type, enquiry_type, message) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, customerType);
            ps.setString(5, enquiryType);
            ps.setString(6, message);
            return ps;
        }, keyHolder);

        return extractGeneratedKey(keyHolder);
    }

    private long extractGeneratedKey(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object val = keys.get("id");
            if (val == null) {
                val = keys.get("GENERATED_KEY");
            }
            if (val == null && !keys.values().isEmpty()) {
                val = keys.values().iterator().next();
            }
            if (val instanceof Number num) {
                return num.longValue();
            }
        }
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        throw new IllegalStateException("No generated key returned from database insert.");
    }

    public void checkConnection() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    public List<String> getTableNames() {
        try {
            return jdbcTemplate.queryForList("SHOW TABLES", String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    public void initializeWorkflow(Long inquiryId) {
        jdbcTemplate.update(
            "INSERT INTO inquiry_workflow (inquiry_id, status) VALUES (?, 'NEW')",
            inquiryId
        );
    }

    public Optional<Map<String, Object>> getForAdmin(long inquiryId) {
        List<Map<String, Object>> inquiries = jdbcTemplate.queryForList(
            """
            SELECT i.id, i.name, i.email, i.phone, i.customer_type, i.enquiry_type, i.message,
                   i.created_at, w.status, w.internal_notes, w.updated_at
            FROM inquiries i
            JOIN inquiry_workflow w ON w.inquiry_id = i.id
            WHERE i.id = ?
            """,
            inquiryId
        );
        return inquiries.stream().findFirst();
    }

    public Optional<Long> findBySubmissionKey(String key) {
        List<Long> ids = jdbcTemplate.query(
            "SELECT inquiry_id FROM inquiry_submission_idempotency WHERE idempotency_key = ?",
            (resultSet, rowNum) -> resultSet.getLong("inquiry_id"),
            key
        );
        return ids.stream().findFirst();
    }

    public void saveSubmissionKey(String key, long inquiryId) {
        jdbcTemplate.update(
            "INSERT INTO inquiry_submission_idempotency (idempotency_key, inquiry_id) VALUES (?, ?)",
            key,
            inquiryId
        );
    }

    public void deleteInquiry(long inquiryId) {
        jdbcTemplate.update("DELETE FROM inquiries WHERE id = ?", inquiryId);
    }
}
