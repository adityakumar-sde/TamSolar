package com.tamsolar.inquiry;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            logger.info("Verifying and initializing database schema from schema.sql...");
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema.sql"));
            populator.setContinueOnError(true);
            populator.execute(dataSource);
            logger.info("Database schema verification and initialization completed successfully.");
        } catch (Exception e) {
            logger.error("Database schema initialization encountered an issue: {}", e.getMessage(), e);
        }

        try {
            jdbcTemplate.execute("ALTER TABLE inquiries ADD COLUMN phone VARCHAR(25) NULL AFTER email");
            logger.info("Added missing 'phone' column to 'inquiries' table.");
        } catch (Exception e) {
            // Ignored if column already exists
        }
    }
}
