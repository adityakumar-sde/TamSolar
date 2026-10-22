package com.tamsolar.inquiry;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class InquiryRepository {

    private final SimpleJdbcInsert insertInquiry;
    private final JdbcTemplate jdbcTemplate;

    public InquiryRepository(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.insertInquiry = new SimpleJdbcInsert(dataSource)
            .withTableName("inquiries")
            .usingColumns("name", "email", "customer_type", "enquiry_type", "message")
            .usingGeneratedKeyColumns("id");
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long create(String name, String email, String customerType, String enquiryType, String message) {
        Number generatedId = insertInquiry.executeAndReturnKey(Map.of(
            "name", name,
            "email", email,
            "customer_type", customerType,
            "enquiry_type", enquiryType,
            "message", message
        ));
        return generatedId.longValue();
    }

    public void checkConnection() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
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
            SELECT i.id, i.name, i.email, i.customer_type, i.enquiry_type, i.message,
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
