package com.tamsolar.inquiry;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminInquiryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminInquiryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult list(
            String search,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {
        StringBuilder where = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            where.append(
                    " AND (CAST(i.id AS CHAR) LIKE ?"
                            + " OR CONCAT('TS-', LPAD(i.id, 6, '0')) LIKE ?"
                            + " OR i.name LIKE ? OR i.email LIKE ?)");

            String term = "%" + search.trim() + "%";
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
        }

        if (status != null && !status.isBlank()) {
            where.append(" AND w.status = ?");
            parameters.add(status);
        }

        if (fromDate != null) {
            where.append(" AND i.created_at >= ?");
            parameters.add(java.sql.Date.valueOf(fromDate));
        }

        if (toDate != null) {
            where.append(" AND i.created_at < ?");
            parameters.add(java.sql.Date.valueOf(toDate.plusDays(1)));
        }

        String from = " FROM inquiries i "
                + "JOIN inquiry_workflow w ON w.inquiry_id = i.id "
                + "WHERE 1 = 1 "
                + where;

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + from,
                Integer.class,
                parameters.toArray());

        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(page * size);

        String sql = "SELECT i.id, i.name, i.email, i.customer_type, "
                + "i.enquiry_type, i.created_at, "
                + "w.status, w.updated_at, "
                + "(SELECT COUNT(*) FROM email_outbox e "
                + "WHERE e.inquiry_id = i.id "
                + "AND e.kind = 'ADMIN_REPLY') AS reply_count"
                + from
                + " ORDER BY i.created_at DESC, i.id DESC LIMIT ? OFFSET ?";

        List<Map<String, Object>> inquiries = jdbcTemplate.queryForList(sql, pageParameters.toArray());

        return new PageResult(
                inquiries,
                total == null ? 0 : total,
                page,
                size);
    }

    public List<Map<String, Object>> conversation(long inquiryId) {
        String sql = "SELECT id, kind, sender, recipient, subject, text_body, "
                + "status, created_at, sent_at, last_error "
                + "FROM email_outbox "
                + "WHERE inquiry_id = ? "
                + "AND kind IN ('CUSTOMER_CONFIRMATION', 'ADMIN_REPLY') "
                + "ORDER BY created_at, id";

        return jdbcTemplate.queryForList(sql, inquiryId);
    }

    public void updateStatus(long inquiryId, String status) {
        jdbcTemplate.update(
                "UPDATE inquiry_workflow SET status = ? WHERE inquiry_id = ?",
                status,
                inquiryId);
    }

    public void updateNotes(long inquiryId, String notes) {
        jdbcTemplate.update(
                "UPDATE inquiry_workflow SET internal_notes = ? WHERE inquiry_id = ?",
                notes,
                inquiryId);
    }
}