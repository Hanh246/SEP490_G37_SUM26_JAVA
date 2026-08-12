package com.sep.comiverse.config;

import com.sep.comiverse.entity.*;
import com.sep.comiverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@org.springframework.context.annotation.Profile("!integration")
@RequiredArgsConstructor
@Order(1)
public class DbInitializer implements CommandLineRunner {

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Value("${payout.legacy-vnd-per-usd:25000}")
    private BigDecimal legacyVndPerUsd;

    private final IGenreRepository genreRepository;
    private final IComicRepository comicRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final ISubmissionRepository submissionRepository;
    private final IChatFlagRepository chatFlagRepository;
    private final IForumThreadRepository forumThreadRepository;
    private final IForumCategoryRepository forumCategoryRepository;
    private final IAuthorRepository authorRepository;

    private final ITeamAnnouncementRepository teamAnnouncementRepository;
    private final ITeamMessageRepository teamMessageRepository;
    private final ITeamTaskRepository teamTaskRepository;
    private final ITeamJoinRequestRepository teamJoinRequestRepository;
    private final IChapterRepository chapterRepository;
    private final IComicMetricSnapshotRepository metricSnapshotRepository;
    private final IReportCategoryRepository reportCategoryRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // ComicEntity no longer has the legacy `status` column.
        // Publication lifecycle is stored in `publication_status`.
        jdbcTemplate.execute("UPDATE comics SET moderation_status = 'PUBLISHED' WHERE moderation_status IS NULL");
        jdbcTemplate.execute("UPDATE chapters SET moderation_status = 'PUBLISHED' WHERE moderation_status IS NULL");
        migrateAuthorLanguageToComics();
        migrateLegacyChapterPagesIntoChapterImages();
        splitCommaJoinedChapterImageArrays();
        jdbcTemplate.execute("UPDATE chapters SET images = ARRAY[]::text[] WHERE images IS NULL");
        migrateRevenueAnalyticsSchema();
        migrateCreatorPayoutAmountsToUsd();
        migrateMergedPayoutSchema();
        migrateCompletedTeamTasks();
        migrateTranslatorPagePaymentSchema();

        createRoles();
        createAdmin();
        createStaffs();
        createAuthorProfiles();
        createGenres();
        createComics();
        createProjectTeams();
        createAuthorMetricSnapshots();
        createSubmissions();
        createChatFlags();
        createForumThreads();
        createForumCategories();
        createReportCategories();
        initReportDatabaseIndexes();

        repairMissingProjectTeamLeaders();

        System.out.println("✅ Database seed and migrations completed");
    }

    /**
     * Moves the legacy author-level language to each owned comic once, then
     * removes the obsolete authors.language column. New comics own this value.
     */
    private void migrateAuthorLanguageToComics() {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'authors' AND column_name = 'language'
                    ) AND EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'comics' AND column_name = 'language'
                    ) THEN
                        EXECUTE $sql$
                            UPDATE comics c
                            SET language = CASE
                                WHEN c.language IS NULL
                                  OR BTRIM(c.language) = ''
                                  OR LOWER(BTRIM(c.language)) = 'unknown'
                                THEN COALESCE(NULLIF(BTRIM(a.language), ''), 'Unknown')
                                ELSE BTRIM(c.language)
                            END
                            FROM authors a
                            WHERE a.id = c.author_id OR a.user_id = c.author_id
                        $sql$;
                        ALTER TABLE authors DROP COLUMN IF EXISTS language;
                    END IF;
                END $$;
                """);
        jdbcTemplate.execute("UPDATE comics SET language = 'Unknown' WHERE language IS NULL OR BTRIM(language) = ''");
        jdbcTemplate.execute("ALTER TABLE comics ALTER COLUMN language SET DEFAULT 'Unknown'");
        jdbcTemplate.execute("ALTER TABLE comics ALTER COLUMN language SET NOT NULL");
    }

    private void migrateLegacyChapterPagesIntoChapterImages() {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF to_regclass('public.chapter_pages') IS NOT NULL THEN
                        UPDATE chapters c
                        SET images = COALESCE(p.urls, ARRAY[]::text[])
                        FROM (
                            SELECT chapter_id, array_agg(image_url ORDER BY page_number) AS urls
                            FROM chapter_pages
                            WHERE deleted = false OR deleted IS NULL
                            GROUP BY chapter_id
                        ) p
                        WHERE c.id = p.chapter_id
                          AND (c.images IS NULL OR cardinality(c.images) = 0);
                    END IF;
                END $$;
                """);
    }

    /**
     * Normalizes legacy revenue analytics columns used by Author monthly payout.
     * Older databases may contain comicId/comicid or created_at while the current
     * SQL and entity mappings use comic_id and create_at.
     */
    private void migrateRevenueAnalyticsSchema() {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF to_regclass('public.comic_daily_views') IS NOT NULL THEN
                        IF NOT EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'comic_daily_views'
                              AND column_name = 'comic_id'
                        ) THEN
                            IF EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_schema = 'public'
                                  AND table_name = 'comic_daily_views'
                                  AND column_name = 'comicId'
                            ) THEN
                                EXECUTE 'ALTER TABLE public.comic_daily_views RENAME COLUMN "comicId" TO comic_id';
                            ELSIF EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_schema = 'public'
                                  AND table_name = 'comic_daily_views'
                                  AND column_name = 'comicid'
                            ) THEN
                                EXECUTE 'ALTER TABLE public.comic_daily_views RENAME COLUMN comicid TO comic_id';
                            END IF;
                        END IF;

                        IF NOT EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'comic_daily_views'
                              AND column_name = 'log_date'
                        ) AND EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'comic_daily_views'
                              AND column_name = 'view_date'
                        ) THEN
                            EXECUTE 'ALTER TABLE public.comic_daily_views RENAME COLUMN view_date TO log_date';
                        END IF;
                    END IF;

                    IF to_regclass('public.user_saves') IS NOT NULL THEN
                        IF NOT EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'user_saves'
                              AND column_name = 'create_at'
                        ) AND EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'user_saves'
                              AND column_name = 'created_at'
                        ) THEN
                            EXECUTE 'ALTER TABLE public.user_saves RENAME COLUMN created_at TO create_at';
                        END IF;
                    END IF;
                END $$;
                """);
    }

    /**
     * Converts legacy creator payout values from VND to USD exactly once.
     *
     * Existing database column names ending in _vnd are intentionally retained
     * to avoid destructive schema changes. The creator_payout_settings.currency
     * marker makes the conversion idempotent.
     */
    private void migrateCreatorPayoutAmountsToUsd() {
        BigDecimal rate = legacyVndPerUsd == null || legacyVndPerUsd.signum() <= 0
                ? new BigDecimal("25000")
                : legacyVndPerUsd;

        jdbcTemplate.execute("ALTER TABLE creator_payout_settings ADD COLUMN IF NOT EXISTS currency varchar(3)");
        jdbcTemplate.execute("ALTER TABLE creator_payout_settings ALTER COLUMN currency SET DEFAULT 'USD'");

        jdbcTemplate.update("""
                UPDATE creator_payout_settings
                SET minimum_payout_vnd = ROUND(minimum_payout_vnd / ?, 2),
                    translator_task_rate_vnd = ROUND(translator_task_rate_vnd / ?, 2),
                    translator_monthly_limit_vnd = ROUND(translator_monthly_limit_vnd / ?, 2),
                    author_view_unit_rate_vnd = ROUND(author_view_unit_rate_vnd / ?, 2),
                    author_follow_unit_rate_vnd = ROUND(author_follow_unit_rate_vnd / ?, 2),
                    author_monthly_limit_vnd = ROUND(author_monthly_limit_vnd / ?, 2),
                    currency = 'USD',
                    update_at = CURRENT_TIMESTAMP
                WHERE UPPER(COALESCE(NULLIF(BTRIM(currency), ''), 'VND')) = 'VND'
                """, rate, rate, rate, rate, rate, rate);

        jdbcTemplate.update("""
                UPDATE creator_payout_requests
                SET amount = ROUND(
                        CASE
                            WHEN base_amount_vnd IS NOT NULL THEN base_amount_vnd / ?
                            WHEN UPPER(COALESCE(currency, 'VND')) = 'VND' THEN amount / ?
                            ELSE amount
                        END,
                        2
                    ),
                    gross_amount_vnd = CASE
                        WHEN gross_amount_vnd IS NULL THEN NULL
                        ELSE ROUND(gross_amount_vnd / ?, 2)
                    END,
                    base_amount_vnd = CASE
                        WHEN base_amount_vnd IS NULL THEN ROUND(amount / ?, 2)
                        ELSE ROUND(base_amount_vnd / ?, 2)
                    END,
                    monthly_limit_vnd = CASE
                        WHEN monthly_limit_vnd IS NULL THEN NULL
                        ELSE ROUND(monthly_limit_vnd / ?, 2)
                    END,
                    exchange_rate_vnd_per_unit = 1,
                    currency = 'USD',
                    calculation_details = CONCAT(
                        COALESCE(calculation_details, ''),
                        '; legacy payout migrated to USD at 1 USD = ',
                        ?,
                        ' VND'
                    ),
                    update_at = CURRENT_TIMESTAMP
                WHERE UPPER(COALESCE(NULLIF(BTRIM(currency), ''), 'VND')) = 'VND'
                """, rate, rate, rate, rate, rate, rate, rate.toPlainString());

        // Support both old and already-merged databases.
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF to_regclass('public.creator_stripe_payout_profiles') IS NOT NULL THEN
                        UPDATE creator_stripe_payout_profiles
                        SET currency = 'USD',
                            update_at = CURRENT_TIMESTAMP
                        WHERE UPPER(COALESCE(NULLIF(BTRIM(currency), ''), 'VND')) = 'VND';
                    END IF;

                    IF to_regclass('public.creator_payout_accounts') IS NOT NULL THEN
                        UPDATE creator_payout_accounts
                        SET currency = 'USD',
                            update_at = CURRENT_TIMESTAMP
                        WHERE UPPER(COALESCE(NULLIF(BTRIM(currency), ''), 'VND')) = 'VND';
                    END IF;
                END $$;
                """);
    }

    /**
     * Consolidates the creator payout schema to six core tables.
     *
     * Legacy tables are copied into their merged replacements:
     * - creator_stripe_payout_profiles -> creator_payout_accounts
     * - creator_payout_supported_currencies -> creator_payout_currencies
     * - translator_page_earnings + translator_earning_adjustments
     *   -> translator_earning_entries
     *
     * Hibernate creates the destination tables before CommandLineRunner executes.
     * ON CONFLICT makes the copy idempotent for partially migrated databases.
     */
    private void migrateMergedPayoutSchema() {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    -- Supported currency + current exchange rate become one table.
                    IF to_regclass('public.creator_payout_supported_currencies') IS NOT NULL
                       AND to_regclass('public.creator_payout_currencies') IS NOT NULL THEN
                        INSERT INTO creator_payout_currencies (
                            id, currency_code, display_name, symbol, units_per_usd,
                            active, deleted, create_at, update_at
                        )
                        SELECT id, currency_code, display_name, symbol, units_per_usd,
                               active, deleted, create_at, update_at
                        FROM creator_payout_supported_currencies
                        ON CONFLICT DO NOTHING;
                    END IF;

                    -- Stripe is the only payout provider, so its profile lives in payout_accounts.
                    IF to_regclass('public.creator_stripe_payout_profiles') IS NOT NULL
                       AND to_regclass('public.creator_payout_accounts') IS NOT NULL THEN
                        INSERT INTO creator_payout_accounts (
                            id, user_id, role, stripe_connected_account_id,
                            account_country, currency, details_submitted,
                            charges_enabled, payouts_enabled, transfers_capability,
                            requirements_currently_due, requirements_disabled_reason,
                            external_account_type, external_account_last4,
                            external_account_display_name, onboarding_status, active,
                            last_synced_at, onboarding_completed_at,
                            deleted, create_at, update_at
                        )
                        SELECT id, user_id, role, stripe_connected_account_id,
                               account_country, currency, details_submitted,
                               charges_enabled, payouts_enabled, transfers_capability,
                               requirements_currently_due, requirements_disabled_reason,
                               external_account_type, external_account_last4,
                               external_account_display_name, onboarding_status, active,
                               last_synced_at, onboarding_completed_at,
                               deleted, create_at, update_at
                        FROM creator_stripe_payout_profiles
                        ON CONFLICT DO NOTHING;
                    END IF;

                    -- Page earnings become positive PAGE_EARNING ledger entries.
                    IF to_regclass('public.translator_page_earnings') IS NOT NULL
                       AND to_regclass('public.translator_earning_entries') IS NOT NULL THEN
                        INSERT INTO translator_earning_entries (
                            id, entry_type, translator_id, task_id, settlement_id,
                            chapter_id, page_id, page_number, entry_month,
                            responsibility_factor, gross_amount_usd, amount_usd, reason,
                            deleted, create_at, update_at
                        )
                        SELECT id, 'PAGE_EARNING', translator_id, task_id, settlement_id,
                               chapter_id, page_id, page_number, settlement_month,
                               responsibility_factor, gross_amount_usd, net_amount_usd, NULL,
                               deleted, create_at, update_at
                        FROM translator_page_earnings
                        ON CONFLICT DO NOTHING;
                    END IF;

                    -- Old adjustments become signed ledger entries.
                    IF to_regclass('public.translator_earning_adjustments') IS NOT NULL
                       AND to_regclass('public.translator_earning_entries') IS NOT NULL THEN
                        INSERT INTO translator_earning_entries (
                            id, entry_type, translator_id, task_id, settlement_id,
                            chapter_id, page_id, page_number, entry_month,
                            responsibility_factor, gross_amount_usd, amount_usd, reason,
                            deleted, create_at, update_at
                        )
                        SELECT a.id,
                               CASE WHEN a.amount_usd < 0 THEN 'REVERSAL_ADJUSTMENT' ELSE 'MANUAL_ADJUSTMENT' END,
                               a.translator_id, a.task_id, a.settlement_id,
                               s.chapter_id, NULL, NULL, a.adjustment_month,
                               NULL, NULL, a.amount_usd, a.reason,
                               a.deleted, a.create_at, a.update_at
                        FROM translator_earning_adjustments a
                        LEFT JOIN translator_chapter_settlements s
                          ON s.id = a.settlement_id
                        ON CONFLICT DO NOTHING;
                    END IF;

                    -- Drop only after the data-copy steps above have run.
                    DROP TABLE IF EXISTS translator_earning_adjustments;
                    DROP TABLE IF EXISTS translator_page_earnings;
                    DROP TABLE IF EXISTS creator_stripe_payout_profiles;
                    DROP TABLE IF EXISTS creator_payout_supported_currencies;
                END $$;
                """);
    }

    private void migrateCompletedTeamTasks() {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'team_tasks'
                          AND column_name = 'completed_at'
                    ) THEN
                        -- Backfill legacy completed tasks when audit timestamps exist.
                        -- New completions are written through TeamTaskEntity.completedAt.
                        IF EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'team_tasks'
                              AND column_name = 'update_at'
                        ) AND EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'team_tasks'
                              AND column_name = 'create_at'
                        ) THEN
                            EXECUTE $sql$
                                UPDATE team_tasks
                                SET completed_at = COALESCE(update_at, create_at, CURRENT_TIMESTAMP)
                                WHERE completed_at IS NULL
                                  AND LOWER(COALESCE(status, '')) IN ('completed', 'complete', 'done')
                            $sql$;
                        ELSIF EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'team_tasks'
                              AND column_name = 'update_at'
                        ) THEN
                            EXECUTE $sql$
                                UPDATE team_tasks
                                SET completed_at = COALESCE(update_at, CURRENT_TIMESTAMP)
                                WHERE completed_at IS NULL
                                  AND LOWER(COALESCE(status, '')) IN ('completed', 'complete', 'done')
                            $sql$;
                        ELSIF EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'team_tasks'
                              AND column_name = 'create_at'
                        ) THEN
                            EXECUTE $sql$
                                UPDATE team_tasks
                                SET completed_at = COALESCE(create_at, CURRENT_TIMESTAMP)
                                WHERE completed_at IS NULL
                                  AND LOWER(COALESCE(status, '')) IN ('completed', 'complete', 'done')
                            $sql$;
                        ELSE
                            -- There is no trustworthy historical timestamp to migrate.
                            -- Leave completed_at NULL so legacy tasks cannot generate
                            -- incorrect payout revenue. Set it manually when needed.
                            RAISE NOTICE 'Skipping completed_at backfill: team_tasks has no create_at/update_at column';
                        END IF;
                    END IF;

                    -- One-time compatibility migration from an abandoned multi-assignee
                    -- branch. Current task ownership uses only assignee_id.
                    IF EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'team_tasks'
                          AND column_name = 'assignee_id'
                    ) AND EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'team_tasks'
                          AND column_name = 'assignee_ids'
                          AND data_type = 'ARRAY'
                    ) THEN
                        EXECUTE $sql$
                            UPDATE team_tasks
                            SET assignee_id = assignee_ids[1]
                            WHERE assignee_id IS NULL
                              AND assignee_ids IS NOT NULL
                              AND cardinality(assignee_ids) > 0
                        $sql$;
                    END IF;
                END $$;
                """);
    }

    private void migrateTranslatorPagePaymentSchema() {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF to_regclass('public.creator_payout_requests') IS NOT NULL THEN
                        ALTER TABLE creator_payout_requests
                            DROP CONSTRAINT IF EXISTS uk_creator_payout_user_month;
                    END IF;

                    IF to_regclass('public.page_translation') IS NOT NULL THEN
                        IF EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'page_translation'
                              AND column_name = 'responsibility_factor'
                        ) THEN
                            UPDATE page_translation
                            SET responsibility_factor = 1.00
                            WHERE responsibility_factor IS NULL;
                        END IF;

                        IF EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'page_translation'
                              AND column_name = 'assigned_translator_id'
                        ) THEN
                            UPDATE page_translation p
                            SET assigned_translator_id = t.assignee_id
                            FROM team_tasks t
                            WHERE p.task_id = t.id
                              AND p.assigned_translator_id IS NULL;
                        END IF;
                    END IF;

                    IF to_regclass('public.team_tasks') IS NOT NULL
                       AND EXISTS (
                           SELECT 1 FROM information_schema.columns
                           WHERE table_schema = 'public'
                             AND table_name = 'team_tasks'
                             AND column_name = 'chapter_reward_usd'
                       ) THEN
                        -- Legacy column name; the value is USD per translated page.
                        UPDATE team_tasks t
                        SET chapter_reward_usd = ROUND(
                            COALESCE(s.translator_task_rate_vnd, 3.50)
                            * COALESCE(p.page_count, 0),
                            2
                        )
                        FROM (
                            SELECT task_id, COUNT(*)::numeric AS page_count
                            FROM page_translation
                            GROUP BY task_id
                        ) p
                        LEFT JOIN creator_payout_settings s
                          ON s.config_key = 'DEFAULT'
                         AND COALESCE(s.deleted, false) = false
                        WHERE t.id = p.task_id
                          AND t.chapter_reward_usd IS NULL
                          AND p.page_count > 0;
                    END IF;
                END $$;
                """);
    }

    private void splitCommaJoinedChapterImageArrays() {
        jdbcTemplate.execute("""
                UPDATE chapters
                SET images = string_to_array(
                    replace(replace(images[1], ',https://', '|https://'), ',http://', '|http://'),
                    '|'
                )
                WHERE images IS NOT NULL
                  AND cardinality(images) = 1
                  AND (images[1] LIKE '%,https://%' OR images[1] LIKE '%,http://%');
                """);
    }

    /**
     * Repairs legacy project teams that do not have a usable leader display name.
     * Do not swallow database exceptions here: this method runs inside the seed
     * transaction, so any database error must roll the transaction back cleanly.
     */
    private void repairMissingProjectTeamLeaders() {
        List<ProjectTeamEntity> emptyLeaderTeams = projectTeamRepository.findAll().stream()
                .filter(team -> team.getLeaderName() == null
                        || team.getLeaderName().isBlank()
                        || "No Leader".equalsIgnoreCase(team.getLeaderName()))
                .toList();

        for (ProjectTeamEntity team : emptyLeaderTeams) {
            String title = team.getTitle();
            if (title != null && (title.equals("trantest56787")
                    || title.equals("TransTest123455")
                    || title.toLowerCase().contains("trantest"))) {
                team.setLeaderName(title);
            } else {
                team.setLeaderName("trantest56787");
            }
            team.setLeaderInitials("TL");
            projectTeamRepository.save(team);
        }
    }

    private void createRoles() {
        createRoleIfNotExist("ADMIN");
        createRoleIfNotExist("MODERATOR");
        createRoleIfNotExist("AUTHOR");
        createRoleIfNotExist("TRANSLATOR");
        createRoleIfNotExist("PROJECT_LEADER");
        createRoleIfNotExist("READER");
    }

    private void createRoleIfNotExist(String roleName) {
        if (!roleRepository.findByRoleName(roleName).isPresent()) {
            roleRepository.save(RoleEntity.builder().roleName(roleName).build());
            System.out.println("✅ Created role: " + roleName);
        }
    }

    private void createAdmin() {
        RoleEntity adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseThrow(() -> new RuntimeException("ADMIN role not found"));

        java.util.Optional<UserEntity> adminOpt = userRepository.findByUsername("admin");
        if (adminOpt.isEmpty()) {
            UserEntity admin = UserEntity.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("System Administrator")
                    .email("admin@comiverse.com")
                    .phone("0123456789")
                    .role(adminRole)
                    .status("ACTIVE")
                    .build();

            userRepository.save(admin);
            System.out.println("Created admin: admin / admin123");
        } else {
            UserEntity admin = adminOpt.get();
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setStatus("ACTIVE");
            admin.setRole(adminRole);
            userRepository.save(admin);
            System.out.println("Reset admin password and status to ACTIVE / admin123");
        }
    }

    private void createStaffs() {
        createSampleUser("moderator1", "Moderator One", "moderator1@comiverse.com", "0987654321", "MODERATOR", "staff123");
        createSampleUser("author1", "Author One", "author1@comiverse.com", "0987654322", "AUTHOR", "staff123");
        createSampleUser("translator1", "Translator One", "translator1@comiverse.com", "0987654323", "TRANSLATOR", "staff123");
        createSampleUser("projectleader1", "Project Leader One", "projectleader1@comiverse.com", "0987654325", "PROJECT_LEADER", "staff123");
        createSampleUser("reader1", "Reader One", "reader1@comiverse.com", "0987654324", "READER", "reader123");
    }

    private void createSampleUser(String username, String fullName, String email, String phone, String roleName, String password) {
        RoleEntity targetRole = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RuntimeException(roleName + " role not found"));

        java.util.Optional<UserEntity> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            UserEntity user = UserEntity.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .fullName(fullName)
                    .email(email)
                    .phone(phone)
                    .role(targetRole)
                    .status("ACTIVE")
                    .build();

            userRepository.save(user);
            System.out.println("Created " + username + " (" + roleName + "): " + username + " / " + password);
        } else {
            UserEntity user = userOpt.get();
            user.setPassword(passwordEncoder.encode(password));
            user.setStatus("ACTIVE");
            user.setRole(targetRole);
            userRepository.save(user);
            System.out.println("Reset sample user " + username + " to ACTIVE / " + password);
        }
    }


    private void createAuthorProfiles() {
        userRepository.findByUsername("author1").ifPresent(authorUser -> {
            if (!authorRepository.existsByUserIdAndDeletedFalse(authorUser.getId())) {
                AuthorEntity author = AuthorEntity.builder()
                        .user(authorUser)
                        .authorType(com.sep.comiverse.entity.enums.AuthorType.INDIVIDUAL)
                        .displayName(authorUser.getFullName() != null ? authorUser.getFullName() : authorUser.getUsername())
                        .legalName(authorUser.getFullName())
                        .contactEmail(authorUser.getEmail())
                        .avatarUrl(authorUser.getAvatarUrl())
                        .bio("Sample author profile for seeded comics.")
                        .build();
                authorRepository.save(author);
                System.out.println("Created author profile for author1");
            }
        });
    }

    private void createGenres() {
        String[] genreNames = {"Action", "Adventure", "Fantasy", "Romance", "Mystery", "Cultivation", "Drama", "Comedy"};
        for (String name : genreNames) {
            if (genreRepository.findAll().stream().noneMatch(g -> g.getName().equalsIgnoreCase(name))) {
                GenreEntity genre = new GenreEntity();
                genre.setName(name);
                genre.setSlug(name.toLowerCase());
                genreRepository.save(genre);
                System.out.println("Created genre: " + name);
            }
        }
    }

    private void createComics() {
        if (comicRepository.findAll().isEmpty()) {
            UserEntity author = userRepository.findByUsername("author1")
                    .orElseThrow(() -> new RuntimeException("author1 not found"));
            java.util.UUID authorId = author.getId();

            java.util.List<GenreEntity> allGenres = genreRepository.findAll();
            java.util.Set<GenreEntity> genres1 = pickGenres(allGenres, "Action", "Fantasy");
            java.util.Set<GenreEntity> genres2 = pickGenres(allGenres, "Adventure", "Mystery");
            java.util.Set<GenreEntity> genres3 = pickGenres(allGenres, "Fantasy", "Drama");
            java.util.Set<GenreEntity> genres4 = pickGenres(allGenres, "Cultivation", "Action");

            comicRepository.save(ComicEntity.builder()
                    .title("Invincible Sword God")
                    .summary("A legendary sword cultivator reincarnates and rebuilds his power from the lowest rank.")
                    .language("Chinese")
                    .authorId(authorId)
                    .publicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.ONGOING)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres1)
                    .cover("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg")
                    .viewCount(125000L)
                    .saveCount(4300)
                    .likeCount(9800)
                    .ratingAverage(4.6)
                    .ratingCount(1280)
                    .latestChapterNumber("45")
                    .build());

            comicRepository.save(ComicEntity.builder()
                    .title("Spirit Recovery")
                    .summary("An urban student discovers that spiritual energy is returning to the modern world.")
                    .language("Chinese")
                    .authorId(authorId)
                    .publicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.ONGOING)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres2)
                    .cover("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg")
                    .viewCount(89000L)
                    .saveCount(2100)
                    .likeCount(6200)
                    .ratingAverage(4.3)
                    .ratingCount(870)
                    .latestChapterNumber("32")
                    .build());

            comicRepository.save(ComicEntity.builder()
                    .title("Demon King Reborn")
                    .summary("The fallen Demon Monarch wakes up in a rival kingdom and plans a second rise.")
                    .language("Korean")
                    .authorId(authorId)
                    .publicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.HIATUS)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres3)
                    .cover("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg")
                    .viewCount(54000L)
                    .saveCount(1200)
                    .likeCount(3900)
                    .ratingAverage(4.1)
                    .ratingCount(540)
                    .latestChapterNumber("18")
                    .build());

            comicRepository.save(ComicEntity.builder()
                    .title("Heavenly Dao")
                    .summary("A young cultivator studies the rules of heaven and challenges the order of the realms.")
                    .language("Chinese")
                    .authorId(authorId)
                    .publicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.COMPLETED)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres4)
                    .cover("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg")
                    .viewCount(210000L)
                    .saveCount(7600)
                    .likeCount(14500)
                    .ratingAverage(4.8)
                    .ratingCount(2100)
                    .latestChapterNumber("60")
                    .build());

            System.out.println("✅ Sample author comics initialized in DB.");
        }
    }

    private Set<GenreEntity> pickGenres(List<GenreEntity> allGenres, String... names) {
        java.util.Set<String> selectedNames = java.util.Arrays.stream(names)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toSet());
        return allGenres.stream()
                .filter(g -> g.getName() != null && selectedNames.contains(g.getName().toLowerCase()))
                .collect(java.util.stream.Collectors.toSet());
    }


    private void createProjectTeams() {
        // Disabled mock seeding
    }

    private void createAuthorMetricSnapshots() {
        if (metricSnapshotRepository.findAll().isEmpty()) {
            for (ComicEntity comic : comicRepository.findAll()) {
                if (comic.getAuthorId() == null) {
                    continue;
                }
                metricSnapshotRepository.save(ComicMetricSnapshotEntity.builder()
                        .comicId(comic.getId())
                        .authorId(comic.getAuthorId())
                        .viewCount(comic.getViewCount() == null ? 0L : comic.getViewCount())
                        .savedCount(comic.getSaveCount() == null ? 0L : comic.getSaveCount().longValue())
                        .likeCount(comic.getLikeCount() == null ? 0L : comic.getLikeCount().longValue())
                        .estimatedRevenue(BigDecimal.valueOf((comic.getViewCount() == null ? 0L : comic.getViewCount()) * 0.01))
                        .build());
            }
            System.out.println("✅ Sample author metric snapshots initialized in DB.");
        }
    }

    private void createSubmissions() {
        // Disabled mock seeding
    }

    private void createChatFlags() {
        // Disabled mock seeding
    }

    private void createForumThreads() {
        // Disabled mock seeding
    }

    private void createForumCategories() {
        Map<String, String> categories = new LinkedHashMap<>();
        categories.put("General", "#94a3b8");
        categories.put("Announcements", "#8b5cf6");
        categories.put("Suggestions", "#3b82f6");
        categories.put("Support", "#10b981");
        categories.put("Spoilers", "#ef4444");
        categories.put("Off-topic", "#f59e0b");

        forumThreadRepository.findAll().stream()
                .map(ForumThreadEntity::getCategory)
                .filter(name -> name != null && !name.isBlank())
                .forEach(name -> categories.putIfAbsent(name.trim(), "#a855f7"));

        categories.forEach((name, color) -> {
            if (!forumCategoryRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
                forumCategoryRepository.save(ForumCategoryEntity.builder()
                        .name(name)
                        .color(color)
                        .isActive(true)
                        .build());
            }
        });
    }

    private void createReportCategories() {
        if (reportCategoryRepository.count() == 0) {
            UserEntity adminUser = userRepository.findByEmail("admin@gmail.com").orElse(null);

            reportCategoryRepository.save(ReportCategoryEntity.builder()
                    .name("Image & Page Issue")
                    .description("Chapter images are blurry, broken, fail to load, or out of reading order")
                    .assignedRole(com.sep.comiverse.entity.enums.ReportAssignedRole.MODERATOR)
                    .targetTypes(java.util.List.of(com.sep.comiverse.entity.enums.ReportTargetType.CHAPTER))
                    .isActive(true)
                    .createdBy(adminUser)
                    .build());

            reportCategoryRepository.save(ReportCategoryEntity.builder()
                    .name("Translation Error")
                    .description("Inaccurate translations, unnatural phrasing, missing dialogue, or typesetting mistakes")
                    .assignedRole(com.sep.comiverse.entity.enums.ReportAssignedRole.PROJECT_LEADER)
                    .targetTypes(java.util.List.of(
                            com.sep.comiverse.entity.enums.ReportTargetType.CHAPTER_TRANSLATIONS
                    ))
                    .isActive(true)
                    .createdBy(adminUser)
                    .build());

            reportCategoryRepository.save(ReportCategoryEntity.builder()
                    .name("Duplicate Content")
                    .description("Duplicate comic title, duplicate chapter uploads, or repeated pages")
                    .assignedRole(com.sep.comiverse.entity.enums.ReportAssignedRole.MODERATOR)
                    .targetTypes(java.util.List.of(
                            com.sep.comiverse.entity.enums.ReportTargetType.COMIC,
                            com.sep.comiverse.entity.enums.ReportTargetType.CHAPTER,
                            com.sep.comiverse.entity.enums.ReportTargetType.CHAPTER_TRANSLATIONS
                    ))
                    .isActive(true)
                    .createdBy(adminUser)
                    .build());

            reportCategoryRepository.save(ReportCategoryEntity.builder()
                    .name("Spam & Malicious Ads")
                    .description("Content contains spam comments, phishing links, or unauthorized external ads")
                    .assignedRole(com.sep.comiverse.entity.enums.ReportAssignedRole.MODERATOR)
                    .targetTypes(java.util.List.of(
                            com.sep.comiverse.entity.enums.ReportTargetType.COMIC,
                            com.sep.comiverse.entity.enums.ReportTargetType.CHAPTER,
                            com.sep.comiverse.entity.enums.ReportTargetType.CHAPTER_TRANSLATIONS
                    ))
                    .isActive(true)
                    .createdBy(adminUser)
                    .build());

            reportCategoryRepository.save(ReportCategoryEntity.builder()
                    .name("Inappropriate Content")
                    .description("Content violates community guidelines, inappropriate age rating, or copyright violation")
                    .assignedRole(com.sep.comiverse.entity.enums.ReportAssignedRole.MODERATOR)
                    .targetTypes(java.util.List.of(
                            com.sep.comiverse.entity.enums.ReportTargetType.COMIC,
                            com.sep.comiverse.entity.enums.ReportTargetType.CHAPTER,
                            com.sep.comiverse.entity.enums.ReportTargetType.CHAPTER_TRANSLATIONS
                    ))
                    .isActive(true)
                    .createdBy(adminUser)
                    .build());

            reportCategoryRepository.save(ReportCategoryEntity.builder()
                    .name("Translation Project Delay")
                    .description("Significant release schedule delays or abandoned translation group chapters")
                    .assignedRole(com.sep.comiverse.entity.enums.ReportAssignedRole.PROJECT_LEADER)
                    .targetTypes(java.util.List.of(
                            com.sep.comiverse.entity.enums.ReportTargetType.COMIC,
                            com.sep.comiverse.entity.enums.ReportTargetType.CHAPTER_TRANSLATIONS
                    ))
                    .isActive(true)
                    .createdBy(adminUser)
                    .build());

            System.out.println("✅ Default report categories initialized in DB.");
        }
    }

    private void initReportDatabaseIndexes() {
        try {
            jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                        IF to_regclass('public.reports') IS NOT NULL THEN
                            IF NOT EXISTS (
                                SELECT 1 FROM pg_indexes
                                WHERE tablename = 'reports' AND indexname = 'uidx_reports_active_per_target'
                            ) THEN
                                CREATE UNIQUE INDEX uidx_reports_active_per_target
                                ON public.reports (reporter_id, target_type, target_id)
                                WHERE status IN ('PENDING', 'IN_PROGRESS') AND (deleted IS NULL OR deleted = false);
                            END IF;
                        END IF;
                    END $$;
                    """);
            System.out.println("✅ Report partial unique index verified in PostgreSQL.");
        } catch (Exception e) {
            System.out.println("⚠️ Could not create partial unique index on reports table (DB might still be initializing schema): " + e.getMessage());
        }
    }
}
