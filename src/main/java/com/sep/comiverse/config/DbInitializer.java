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

    private final ITeamAnnouncementRepository teamAnnouncementRepository;
    private final ITeamMessageRepository teamMessageRepository;
    private final ITeamTaskRepository teamTaskRepository;
    private final ITeamJoinRequestRepository teamJoinRequestRepository;
    private final IChapterRepository chapterRepository;
    private final IChapterPageRepository chapterPageRepository;
    private final IComicMetricSnapshotRepository metricSnapshotRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Correct legacy lowercase/mixedcase enum values in existing comics table
        jdbcTemplate.execute("UPDATE comics SET status = 'ONGOING' WHERE status = 'Ongoing' OR status = 'ongoing'");
        jdbcTemplate.execute("UPDATE comics SET status = 'COMPLETED' WHERE status = 'Completed' OR status = 'completed'");
        jdbcTemplate.execute("UPDATE comics SET status = 'PAUSED' WHERE status = 'Paused' OR status = 'paused'");
        jdbcTemplate.execute("UPDATE comics SET moderation_status = 'PUBLISHED' WHERE moderation_status IS NULL");
        jdbcTemplate.execute("UPDATE chapters SET moderation_status = 'PUBLISHED' WHERE moderation_status IS NULL");

        createRoles();
        createAdmin();
        createStaffs();
        createGenres();
        createComics();
        createProjectTeams();
        createAuthorChapterPages();
        createAuthorMetricSnapshots();
        createSubmissions();
        createChatFlags();
        createForumThreads();
    }

    private void createRoles() {
        createRoleIfNotExist("ADMIN");
        createRoleIfNotExist("MODERATOR");
        createRoleIfNotExist("AUTHOR");
        createRoleIfNotExist("TRANSLATOR");
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
                    .slug("invincible-sword-god")
                    .summary("A legendary sword cultivator reincarnates and rebuilds his power from the lowest rank.")
                    .authorId(authorId)
                    .status(com.sep.comiverse.constants.ComicStatus.ONGOING)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres1)
                    .genreIds(toGenreIds(genres1))
                    .cover("⚔️")
                    .thumbnail("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg")
                    .viewCount(125000L)
                    .saveCount(4300)
                    .likeCount(9800)
                    .ratingAverage(4.6)
                    .ratingCount(1280)
                    .latestChapterNumber("45")
                    .build());

            comicRepository.save(ComicEntity.builder()
                    .title("Spirit Recovery")
                    .slug("spirit-recovery")
                    .summary("An urban student discovers that spiritual energy is returning to the modern world.")
                    .authorId(authorId)
                    .status(com.sep.comiverse.constants.ComicStatus.ONGOING)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres2)
                    .genreIds(toGenreIds(genres2))
                    .cover("🔮")
                    .thumbnail("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg")
                    .viewCount(89000L)
                    .saveCount(2100)
                    .likeCount(6200)
                    .ratingAverage(4.3)
                    .ratingCount(870)
                    .latestChapterNumber("32")
                    .build());

            comicRepository.save(ComicEntity.builder()
                    .title("Demon King Reborn")
                    .slug("demon-king-reborn")
                    .summary("The fallen Demon Monarch wakes up in a rival kingdom and plans a second rise.")
                    .authorId(authorId)
                    .status(com.sep.comiverse.constants.ComicStatus.PAUSED)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres3)
                    .genreIds(toGenreIds(genres3))
                    .cover("👑")
                    .thumbnail("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg")
                    .viewCount(54000L)
                    .saveCount(1200)
                    .likeCount(3900)
                    .ratingAverage(4.1)
                    .ratingCount(540)
                    .latestChapterNumber("18")
                    .build());

            comicRepository.save(ComicEntity.builder()
                    .title("Heavenly Dao")
                    .slug("heavenly-dao")
                    .summary("A young cultivator studies the rules of heaven and challenges the order of the realms.")
                    .authorId(authorId)
                    .status(com.sep.comiverse.constants.ComicStatus.COMPLETED)
                    .moderationStatus(com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED)
                    .genres(genres4)
                    .genreIds(toGenreIds(genres4))
                    .cover("☯️")
                    .thumbnail("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg")
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
        if (projectTeamRepository.findAll().isEmpty()) {
            // Query comics from DB
            ComicEntity comic1 = comicRepository.findByTitle("Invincible Sword God").orElse(null);
            ComicEntity comic2 = comicRepository.findByTitle("Spirit Recovery").orElse(null);
            ComicEntity comic3 = comicRepository.findByTitle("Demon King Reborn").orElse(null);

            // Team 1: Dragon Group
            ProjectTeamEntity team1 = ProjectTeamEntity.builder()
                    .title("Dragon Group")
                    .comicName("Invincible Sword God")
                    .status("Active")
                    .membersCount(7)
                    .chaptersCount(45)
                    .progress(68)
                    .leaderName("Translator One")
                    .leaderInitials("TO")
                    .deadline("Jul 15, 2026")
                    .sourceLang("Japanese")
                    .targetLang("English")
                    .priority("High")
                    .cover("⚔️")
                    .description("A legendary sword cultivator reincarnates in a waste body and climbs to the peak of martial arts.")
                    .assignedToMe(true)
                    .build();

            team1.getChaptersList().add(ChapterEntity.builder()
                    .chapterNumber("45")
                    .title("Chapter 45: Rebirth")
                    .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                    .comic(comic1)
                    .projectTeam(team1)
                    .moderationStatus(com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED)
                    .build());
            team1.getChaptersList().add(ChapterEntity.builder()
                    .chapterNumber("44")
                    .title("Chapter 44: Reincarnation")
                    .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                    .comic(comic1)
                    .projectTeam(team1)
                    .moderationStatus(com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED)
                    .build());
            team1.getChaptersList().add(ChapterEntity.builder()
                    .chapterNumber("43")
                    .title("Chapter 43: Ascending")
                    .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                    .comic(comic1)
                    .projectTeam(team1)
                    .moderationStatus(com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED)
                    .build());
            team1 = projectTeamRepository.save(team1);

            // Seed workspace items for team1
            teamAnnouncementRepository.save(TeamAnnouncementEntity.builder()
                    .projectTeamId(team1.getId())
                    .author("John Smith")
                    .role("Group Leader")
                    .avatar("JS")
                    .time("2 hours ago")
                    .content("📢 Announcement: Chapter 43 deadline is June 12th. QC members, please finish this ASAP! Keep up the great work everyone 🏆")
                    .likes(8)
                    .build());

            teamAnnouncementRepository.save(TeamAnnouncementEntity.builder()
                    .projectTeamId(team1.getId())
                    .author("Emily Brown")
                    .role("Member")
                    .avatar("EB")
                    .time("1 day ago")
                    .content("Welcome our new member @Robert Taylor to the group! Looking forward to your contributions 🎉")
                    .likes(12)
                    .build());

            teamMessageRepository.save(TeamMessageEntity.builder()
                    .projectTeamId(team1.getId())
                    .sender("Michael Chen")
                    .avatar("MC")
                    .time("12:30")
                    .text("Is Chapter 45 translation done yet?")
                    .build());

            teamMessageRepository.save(TeamMessageEntity.builder()
                    .projectTeamId(team1.getId())
                    .sender("Translator One")
                    .avatar("TO")
                    .time("12:32")
                    .text("Almost at page 18, should be done in about an hour")
                    .build());

            teamMessageRepository.save(TeamMessageEntity.builder()
                    .projectTeamId(team1.getId())
                    .sender("Sarah Davis")
                    .avatar("SD")
                    .time("12:33")
                    .text("OK I'm ready to proofread, ping me when you're done!")
                    .build());

            teamMessageRepository.save(TeamMessageEntity.builder()
                    .projectTeamId(team1.getId())
                    .sender("Translator One")
                    .avatar("TO")
                    .time("12:45")
                    .text("Good job everyone! We're on track for today's deadline 🔥")
                    .build());

            teamJoinRequestRepository.save(TeamJoinRequestEntity.builder()
                    .projectTeamId(team1.getId())
                    .name("Alex Johnson")
                    .time("2 hours ago")
                    .text("I have 2 years of Chinese translation experience and would love to contribute to the group.")
                    .roles("Translator,Proofreader")
                    .avatar("AJ")
                    .build());

            teamJoinRequestRepository.save(TeamJoinRequestEntity.builder()
                    .projectTeamId(team1.getId())
                    .name("Maria Garcia")
                    .time("5 hours ago")
                    .text("I specialize in typesetting and am proficient with image editing software.")
                    .roles("Typesetter")
                    .avatar("MG")
                    .build());

            teamJoinRequestRepository.save(TeamJoinRequestEntity.builder()
                    .projectTeamId(team1.getId())
                    .name("Kevin Lee")
                    .time("1 day ago")
                    .text("Looking to learn and contribute to the translation community.")
                    .roles("Quality Check")
                    .avatar("KL")
                    .build());

            teamTaskRepository.save(TeamTaskEntity.builder()
                    .projectTeamId(team1.getId())
                    .title("Chapter 46 - Translation")
                    .columnName("backlog")
                    .progress(0)
                    .assignees("MC")
                    .dueDate("06/20/2024")
                    .build());

            teamTaskRepository.save(TeamTaskEntity.builder()
                    .projectTeamId(team1.getId())
                    .title("Chapter 45 - Translation")
                    .columnName("in_progress")
                    .progress(65)
                    .assignees("MC,SD")
                    .dueDate("06/15/2024")
                    .build());

            teamTaskRepository.save(TeamTaskEntity.builder()
                    .projectTeamId(team1.getId())
                    .title("Chapter 44 - Typesetting")
                    .columnName("in_progress")
                    .progress(80)
                    .assignees("EB")
                    .dueDate("06/14/2024")
                    .build());

            teamTaskRepository.save(TeamTaskEntity.builder()
                    .projectTeamId(team1.getId())
                    .title("Chapter 43 - QC")
                    .columnName("under_review")
                    .progress(95)
                    .assignees("LM,SD")
                    .dueDate("06/07/2024")
                    .build());

            teamTaskRepository.save(TeamTaskEntity.builder()
                    .projectTeamId(team1.getId())
                    .title("Chapter 42")
                    .columnName("completed")
                    .progress(100)
                    .assignees("MC")
                    .dueDate("06/10/2024")
                    .build());

            teamTaskRepository.save(TeamTaskEntity.builder()
                    .projectTeamId(team1.getId())
                    .title("Chapter 10")
                    .columnName("paused")
                    .progress(0)
                    .assignees("")
                    .dueDate("05/01/2024")
                    .build());

            // Team 2: Jade Group
            ProjectTeamEntity team2 = ProjectTeamEntity.builder()
                    .title("Jade Group")
                    .comicName("Spirit Recovery")
                    .status("Active")
                    .membersCount(5)
                    .chaptersCount(32)
                    .progress(42)
                    .leaderName("Emily Brown")
                    .leaderInitials("EB")
                    .deadline("Aug 1, 2026")
                    .sourceLang("Chinese")
                    .targetLang("English")
                    .priority("Medium")
                    .cover("🔮")
                    .description("An urban student discovers ancient spiritual energy is recovering across the globe.")
                    .assignedToMe(true)
                    .build();

            team2.getChaptersList().add(ChapterEntity.builder()
                    .chapterNumber("32")
                    .title("Chapter 32: Energy Recovery")
                    .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                    .comic(comic2)
                    .projectTeam(team2)
                    .moderationStatus(com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED)
                    .build());
            team2.getChaptersList().add(ChapterEntity.builder()
                    .chapterNumber("31")
                    .title("Chapter 31: Spiritual Awakening")
                    .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                    .comic(comic2)
                    .projectTeam(team2)
                    .moderationStatus(com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED)
                    .build());
            team2.getChaptersList().add(ChapterEntity.builder()
                    .chapterNumber("30")
                    .title("Chapter 30: Discovery")
                    .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                    .comic(comic2)
                    .projectTeam(team2)
                    .moderationStatus(com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED)
                    .build());
            projectTeamRepository.save(team2);

            // Team 3: Phoenix Group
            ProjectTeamEntity team3 = ProjectTeamEntity.builder()
                    .title("Phoenix Group")
                    .comicName("Demon King Reborn")
                    .status("Paused")
                    .membersCount(4)
                    .chaptersCount(18)
                    .progress(25)
                    .leaderName("Li Ming")
                    .leaderInitials("LM")
                    .deadline("Sep 10, 2026")
                    .sourceLang("Korean")
                    .targetLang("English")
                    .priority("Low")
                    .cover("👑")
                    .description("The overthrown Demon Monarch wakes up as a low-level guard in a rival human kingdom.")
                    .assignedToMe(false)
                    .build();

            team3.getChaptersList().add(ChapterEntity.builder()
                    .chapterNumber("18")
                    .title("Chapter 18: The Awakening")
                    .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                    .comic(comic3)
                    .projectTeam(team3)
                    .moderationStatus(com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED)
                    .build());
            team3.getChaptersList().add(ChapterEntity.builder()
                    .chapterNumber("17")
                    .title("Chapter 17: Awakening Part 1")
                    .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                    .comic(comic3)
                    .projectTeam(team3)
                    .moderationStatus(com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED)
                    .build());
            projectTeamRepository.save(team3);

            System.out.println("✅ Sample project teams and workspace details initialized in DB.");
        }
    }

    private void createAuthorChapterPages() {
        for (ChapterEntity chapter : chapterRepository.findAll()) {
            if (chapter.getComic() == null || chapter.getImages() == null || chapter.getImages().isEmpty()) {
                continue;
            }
            if (!chapterPageRepository.findAllByChapterIdAndDeletedFalseOrderByPageNumberAsc(chapter.getId()).isEmpty()) {
                continue;
            }
            for (int index = 0; index < chapter.getImages().size(); index++) {
                chapterPageRepository.save(ChapterPageEntity.builder()
                        .comicId(chapter.getComic().getId())
                        .chapterId(chapter.getId())
                        .pageNumber(index + 1)
                        .imageUrl(chapter.getImages().get(index))
                        .originalFileName(String.format("sample-page-%03d.jpg", index + 1))
                        .fileSizeBytes(0L)
                        .build());
            }
        }
        System.out.println("✅ Author chapter page previews initialized in DB.");
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
                        .followCount(comic.getSaveCount() == null ? 0L : comic.getSaveCount().longValue())
                        .favoriteCount(comic.getSaveCount() == null ? 0L : comic.getSaveCount().longValue())
                        .likeCount(comic.getLikeCount() == null ? 0L : comic.getLikeCount().longValue())
                        .estimatedRevenue(BigDecimal.valueOf((comic.getViewCount() == null ? 0L : comic.getViewCount()) * 0.01))
                        .build());
            }
            System.out.println("✅ Sample author metric snapshots initialized in DB.");
        }
    }

    private void createSubmissions() {
        if (submissionRepository.findAll().isEmpty()) {
            submissionRepository.save(SubmissionEntity.builder()
                    .title("Invincible Sword God")
                    .chapter("Chapter 46")
                    .submittedBy("Dragon Group")
                    .queueType("translator")
                    .timeLabel("2 hours ago")
                    .timestamp(System.currentTimeMillis() - 2 * 60 * 60 * 1000)
                    .words(3200)
                    .priority("High")
                    .flags(0)
                    .status("pending")
                    .cover("⚔️")
                    .content("Chapter 46: The Sword Sect's Challenge\n\nIn the depths of the Sword Sect, the sword Qi raged like a tempest...")
                    .build());

            submissionRepository.save(SubmissionEntity.builder()
                    .title("Spirit Recovery")
                    .chapter("Chapter 33")
                    .submittedBy("Jade Group")
                    .queueType("translator")
                    .timeLabel("5 hours ago")
                    .timestamp(System.currentTimeMillis() - 5 * 60 * 60 * 1000)
                    .words(2800)
                    .priority("Medium")
                    .flags(0)
                    .status("pending")
                    .cover("🔮")
                    .content("Chapter 33: Unleashing the Seal\n\nThe ancient seal on the cavern wall began to crack...")
                    .build());

            submissionRepository.save(SubmissionEntity.builder()
                    .title("Demon King Reborn")
                    .chapter("Chapter 19")
                    .submittedBy("Phoenix Group")
                    .queueType("translator")
                    .timeLabel("1 day ago")
                    .timestamp(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
                    .words(3500)
                    .priority("Low")
                    .flags(2)
                    .status("pending")
                    .cover("👑")
                    .content("Chapter 19: Whispers of Treason\n\nLord Kael sat on the iron throne...")
                    .build());

            UserEntity author = userRepository.findByUsername("author1").orElse(null);
            ComicEntity heavenlyDao = comicRepository.findByTitle("Heavenly Dao").orElse(null);
            ComicEntity spiritRecovery = comicRepository.findByTitle("Spirit Recovery").orElse(null);
            java.util.UUID authorId = author == null ? null : author.getId();

            submissionRepository.save(SubmissionEntity.builder()
                    .comicId(heavenlyDao == null ? null : heavenlyDao.getId())
                    .authorId(authorId)
                    .title("Heavenly Dao")
                    .chapter("Chapter 61")
                    .submittedBy("Author: author1")
                    .queueType("author")
                    .timeLabel("1 hour ago")
                    .timestamp(System.currentTimeMillis() - 60 * 60 * 1000)
                    .words(0)
                    .priority("High")
                    .flags(0)
                    .status("pending")
                    .cover(heavenlyDao == null ? "☯️" : heavenlyDao.getCover())
                    .content("Chapter 61 image release is waiting for moderator review.")
                    .build());

            submissionRepository.save(SubmissionEntity.builder()
                    .comicId(spiritRecovery == null ? null : spiritRecovery.getId())
                    .authorId(authorId)
                    .title("Spirit Recovery")
                    .chapter("Chapter 34")
                    .submittedBy("Author: author1")
                    .queueType("author")
                    .timeLabel("3 hours ago")
                    .timestamp(System.currentTimeMillis() - 3 * 60 * 60 * 1000)
                    .words(0)
                    .priority("Medium")
                    .flags(0)
                    .status("pending")
                    .cover(spiritRecovery == null ? "🔮" : spiritRecovery.getCover())
                    .content("Chapter 34 image release is waiting for moderator review.")
                    .build());

            System.out.println("✅ Sample submissions initialized in DB.");
        }
    }

    private void createChatFlags() {
        if (chatFlagRepository.findAll().isEmpty()) {
            chatFlagRepository.save(ChatFlagEntity.builder()
                    .user("toxic_fan_99")
                    .message("\"This translation is unacceptable and the comment attacks other users.\"")
                    .reason("Toxicity / Harassment")
                    .status("flagged")
                    .build());

            chatFlagRepository.save(ChatFlagEntity.builder()
                    .user("spammer_bot")
                    .message("\"Visit cheapcoins.biz for free discount codes on web novels!\"")
                    .reason("Unsolicited Spam Link advertisement")
                    .status("flagged")
                    .build());

            System.out.println("✅ Chat flags initialized in DB.");
        }
    }

    private void createForumThreads() {
        if (forumThreadRepository.findAll().isEmpty()) {
            forumThreadRepository.save(ForumThreadEntity.builder()
                    .title("Spam Link Post")
                    .author("bot_account")
                    .content("\"Check out this site for free gift cards: bit.ly/spam-link\"")
                    .build());

            forumThreadRepository.save(ForumThreadEntity.builder()
                    .title("Off-topic Flame War")
                    .author("angry_user_12")
                    .content("\"You guys are all idiots, this series is trash and everyone who likes it has zero braincells!\"")
                    .build());

            System.out.println("✅ Forum threads initialized in DB.");
        }
    }
}
