package com.sep.comiverse.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.repository.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DbInitializer implements CommandLineRunner {

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final IGenreRepository genreRepository;
    private final IComicRepository comicRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final ISubmissionRepository submissionRepository;
    private final IChatFlagRepository chatFlagRepository;
    private final IForumThreadRepository forumThreadRepository;
    private final IAuthorRepository authorRepository;

    private final ITeamAnnouncementRepository teamAnnouncementRepository;
    private final ITeamMessageRepository teamMessageRepository;
    private final ITeamTaskRepository teamTaskRepository;
    private final ITeamJoinRequestRepository teamJoinRequestRepository;
    private final IChapterRepository chapterRepository;
    private final IComicMetricSnapshotRepository metricSnapshotRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // ComicEntity no longer has the legacy `status` column.
        // Publication lifecycle is stored in `publication_status`.
        jdbcTemplate.execute("UPDATE comics SET moderation_status = 'PUBLISHED' WHERE moderation_status IS NULL");
        jdbcTemplate.execute("UPDATE chapters SET moderation_status = 'PUBLISHED' WHERE moderation_status IS NULL");
        migrateLegacyChapterPagesIntoChapterImages();
        splitCommaJoinedChapterImageArrays();
        jdbcTemplate.execute("UPDATE chapters SET images = ARRAY[]::text[] WHERE images IS NULL");

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

        // Self-healing check: restore empty leaderNames to 'trantest56787' for orphaned teams
        try {
            List<ProjectTeamEntity> emptyLeaderTeams = projectTeamRepository.findAll().stream()
                    .filter(t -> t.getLeaderName() == null || t.getLeaderName().isBlank() || "No Leader".equalsIgnoreCase(t.getLeaderName()))
                    .toList();
            for (ProjectTeamEntity team : emptyLeaderTeams) {
                if (team.getTitle() != null && (team.getTitle().equals("trantest56787") || team.getTitle().equals("TransTest123455") || team.getTitle().contains("trantest"))) {
                    team.setLeaderName(team.getTitle());
                } else {
                    team.setLeaderName("trantest56787");
                }
                team.setLeaderInitials("TL");
                projectTeamRepository.save(team);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create HNSW index for cosine similarity on comics
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_comics_summary_vector_hnsw ON comics USING hnsw (summary_vector vector_cosine_ops)");
            System.out.println("✅ Database Setup: HNSW Index created/verified on comics table");
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Failed to create HNSW index: " + e.getMessage());
        }

        // Create HNSW index for cosine similarity on users
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_users_user_vector_hnsw ON users USING hnsw (user_vector vector_cosine_ops)");
            System.out.println("✅ Database Setup: HNSW Index created/verified on users table");
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Failed to create HNSW index on users table: " + e.getMessage());
        }

        // Create standard indexes for project_team_id foreign keys to optimize workspace lookups
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chapters_project_team_id ON chapters (project_team_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_team_tasks_project_team_id ON team_tasks (project_team_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_team_join_requests_project_team_id ON team_join_requests (project_team_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_team_announcements_project_team_id ON team_announcements (project_team_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_team_messages_project_team_id ON team_messages (project_team_id)");
            System.out.println("✅ Database Setup: Foreign key indexes created/verified");
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Failed to create foreign key indexes: " + e.getMessage());
        }
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
                    .authorId(authorId)
                    .publicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.ONGOING)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres1)
                    .genreIds(toGenreIds(genres1))
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
                    .authorId(authorId)
                    .publicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.ONGOING)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres2)
                    .genreIds(toGenreIds(genres2))
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
                    .authorId(authorId)
                    .publicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.HIATUS)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres3)
                    .genreIds(toGenreIds(genres3))
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
                    .authorId(authorId)
                    .publicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.COMPLETED)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres4)
                    .genreIds(toGenreIds(genres4))
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

    private List<java.util.UUID> toGenreIds(Set<GenreEntity> genres) {
        return genres.stream().map(GenreEntity::getId).toList();
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
}
