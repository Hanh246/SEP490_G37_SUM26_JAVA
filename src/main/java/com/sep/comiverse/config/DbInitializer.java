package com.sep.comiverse.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.repository.*;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DbInitializer implements CommandLineRunner {

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private final IGenreRepository genreRepository;
    private final IComicRepository comicRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final ISubmissionRepository submissionRepository;
    private final IChatFlagRepository chatFlagRepository;
    private final IForumThreadRepository forumThreadRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        createRoles();
        createAdmin();
        createStaffs();
        createGenres();
        createComics();
        createProjectTeams();
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
        if (!userRepository.existsByUsername("admin")) {
            RoleEntity adminRole = roleRepository.findByRoleName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("ADMIN role not found"));

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
        }
    }

    private void createStaffs() {
        createSampleUser("moderator1", "Moderator One", "moderator1@comiverse.com", "0987654321", "MODERATOR", "staff123");
        createSampleUser("author1", "Author One", "author1@comiverse.com", "0987654322", "AUTHOR", "staff123");
        createSampleUser("translator1", "Translator One", "translator1@comiverse.com", "0987654323", "TRANSLATOR", "staff123");
        createSampleUser("reader1", "Reader One", "reader1@comiverse.com", "0987654324", "READER", "reader123");
    }

    private void createSampleUser(String username, String fullName, String email, String phone, String roleName, String password) {
        if (!userRepository.existsByUsername(username)) {
            RoleEntity targetRole = roleRepository.findByRoleName(roleName)
                    .orElseThrow(() -> new RuntimeException(roleName + " role not found"));

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
            comicRepository.save(ComicEntity.builder()
                    .title("Invincible Sword God")
                    .author("Wu Xing")
                    .projectTeam("Dragon Group")
                    .chapters(45)
                    .views("1.2M")
                    .status("Ongoing")
                    .genres("Action, Fantasy")
                    .cover("⚔️")
                    .build());

            comicRepository.save(ComicEntity.builder()
                    .title("Spirit Recovery")
                    .author("Chen Wei")
                    .projectTeam("Jade Group")
                    .chapters(32)
                    .views("890K")
                    .status("Ongoing")
                    .genres("Adventure, Mystery")
                    .cover("🔮")
                    .build());

            comicRepository.save(ComicEntity.builder()
                    .title("Demon King Reborn")
                    .author("Li Ming")
                    .projectTeam("Phoenix Group")
                    .chapters(18)
                    .views("654K")
                    .status("Paused")
                    .genres("Fantasy, Drama")
                    .cover("👑")
                    .build());

            comicRepository.save(ComicEntity.builder()
                    .title("Heavenly Dao")
                    .author("Zhang Yu")
                    .projectTeam("Dragon Group")
                    .chapters(120)
                    .views("2.5M")
                    .status("Completed")
                    .genres("Cultivation, Action")
                    .cover("☯️")
                    .build());

            System.out.println("✅ Sample comics initialized in DB.");
        }
    }

    private void createProjectTeams() {
        if (projectTeamRepository.findAll().isEmpty()) {
            // Team 1: Dragon Group
            ProjectTeamEntity team1 = ProjectTeamEntity.builder()
                    .title("Dragon Group")
                    .comicName("Invincible Sword God")
                    .status("Active")
                    .membersCount(7)
                    .chaptersCount(45)
                    .progress(68)
                    .leaderName("Translator One") // Map to seeded translator username or display name
                    .leaderInitials("TO")
                    .deadline("Jul 15, 2026")
                    .sourceLang("Japanese")
                    .targetLang("English")
                    .priority("High")
                    .cover("⚔️")
                    .description("A legendary sword cultivator reincarnates in a waste body and climbs to the peak of martial arts.")
                    .assignedToMe(true)
                    .build();

            team1.getChaptersList().add(ChapterEntity.builder().num("Chapter 45").date("2 hours ago").words(3200).content("Content of Chapter 45...").projectTeam(team1).build());
            team1.getChaptersList().add(ChapterEntity.builder().num("Chapter 44").date("1 day ago").words(2900).content("Content of Chapter 44...").projectTeam(team1).build());
            team1.getChaptersList().add(ChapterEntity.builder().num("Chapter 43").date("3 days ago").words(3100).content("Content of Chapter 43...").projectTeam(team1).build());
            projectTeamRepository.save(team1);

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

            team2.getChaptersList().add(ChapterEntity.builder().num("Chapter 32").date("1 day ago").words(2800).content("Content of Chapter 32...").projectTeam(team2).build());
            team2.getChaptersList().add(ChapterEntity.builder().num("Chapter 31").date("3 days ago").words(2600).content("Content of Chapter 31...").projectTeam(team2).build());
            team2.getChaptersList().add(ChapterEntity.builder().num("Chapter 30").date("5 days ago").words(3000).content("Content of Chapter 30...").projectTeam(team2).build());
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

            team3.getChaptersList().add(ChapterEntity.builder().num("Chapter 18").date("1 week ago").words(3500).content("Content of Chapter 18...").projectTeam(team3).build());
            team3.getChaptersList().add(ChapterEntity.builder().num("Chapter 17").date("2 weeks ago").words(3300).content("Content of Chapter 17...").projectTeam(team3).build());
            projectTeamRepository.save(team3);

            System.out.println("✅ Sample project teams initialized in DB.");
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

            submissionRepository.save(SubmissionEntity.builder()
                    .title("Martial Emperor")
                    .chapter("Chapter 110")
                    .submittedBy("Author: SwordMaster")
                    .queueType("author")
                    .timeLabel("1 hour ago")
                    .timestamp(System.currentTimeMillis() - 60 * 60 * 1000)
                    .words(4200)
                    .priority("High")
                    .flags(0)
                    .status("pending")
                    .cover("☯️")
                    .content("Chapter 110: Grand Cultivation Stage\n\nThe sky split open, revealing a celestial gate...")
                    .build());

            submissionRepository.save(SubmissionEntity.builder()
                    .title("Rebirth of the Urban Immortal")
                    .chapter("Chapter 14")
                    .submittedBy("Author: CultivatorFan")
                    .queueType("author")
                    .timeLabel("3 hours ago")
                    .timestamp(System.currentTimeMillis() - 3 * 60 * 60 * 1000)
                    .words(3100)
                    .priority("Medium")
                    .flags(0)
                    .status("pending")
                    .cover("🏢")
                    .content("Chapter 14: Confronting the Young Master\n\nIn the luxury VIP room...")
                    .build());

            System.out.println("✅ Sample submissions initialized in DB.");
        }
    }

    private void createChatFlags() {
        if (chatFlagRepository.findAll().isEmpty()) {
            chatFlagRepository.save(ChatFlagEntity.builder()
                    .user("toxic_fan_99")
                    .message("\"This translation is pure garbage, go jump off a cliff!\"")
                    .reason("Extreme Toxicity / Harassment")
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
