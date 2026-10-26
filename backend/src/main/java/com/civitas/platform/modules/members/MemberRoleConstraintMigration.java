package com.civitas.platform.modules.members;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;

@Component
public class MemberRoleConstraintMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MemberRoleConstraintMigration.class);
    private final JdbcTemplate jdbc;

    public MemberRoleConstraintMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String database = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        if (!"PostgreSQL".equalsIgnoreCase(database)) return;

        jdbc.execute("""
            DO $$
            BEGIN
              IF NOT EXISTS (
                SELECT 1 FROM pg_constraint
                WHERE conname = 'members_role_check'
                  AND conrelid = 'members'::regclass
                  AND pg_get_constraintdef(oid) ILIKE '%CANDIDATE%'
              ) THEN
                ALTER TABLE members DROP CONSTRAINT IF EXISTS members_role_check;
                ALTER TABLE members
                  ADD CONSTRAINT members_role_check
                  CHECK (role IN ('USER', 'CANDIDATE', 'TENANT_ADMIN', 'PLATFORM_ADMIN'));
              END IF;
            END $$;
            """);
        log.info("Verified the members role constraint includes CANDIDATE.");
    }
}
