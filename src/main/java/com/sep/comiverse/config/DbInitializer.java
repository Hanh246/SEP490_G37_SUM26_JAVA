package com.sep.comiverse.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;

@Component
@RequiredArgsConstructor
public class DbInitializer implements CommandLineRunner {

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        updateRolesAndUsers();
        createAdmin();
        createStaffs();
    }

    private void updateRolesAndUsers() {
        // Ensure new roles exist
        RoleEntity adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("ADMIN").build()));

        RoleEntity moderatorRole = roleRepository.findByRoleName("MODERATOR")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("MODERATOR").build()));

        RoleEntity authorRole = roleRepository.findByRoleName("AUTHOR")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("AUTHOR").build()));

        RoleEntity translatorRole = roleRepository.findByRoleName("TRANSLATOR")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("TRANSLATOR").build()));

        RoleEntity readerRole = roleRepository.findByRoleName("READER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().roleName("READER").build()));

        // Map and clean up legacy STAFF role if present
        java.util.Optional<RoleEntity> staffRoleOpt = roleRepository.findByRoleName("STAFF");
        if (staffRoleOpt.isPresent()) {
            RoleEntity staffRole = staffRoleOpt.get();
            java.util.List<UserEntity> staffUsers = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null && u.getRole().getId().equals(staffRole.getId()))
                    .collect(java.util.stream.Collectors.toList());
            for (UserEntity u : staffUsers) {
                u.setRole(moderatorRole);
                userRepository.save(u);
            }
            roleRepository.delete(staffRole);
            System.out.println("✅ Migrated STAFF users to MODERATOR and deleted legacy STAFF role.");
        }

        // Map and clean up legacy USER role if present
        java.util.Optional<RoleEntity> userRoleOpt = roleRepository.findByRoleName("USER");
        if (userRoleOpt.isPresent()) {
            RoleEntity userRole = userRoleOpt.get();
            java.util.List<UserEntity> userUsers = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null && u.getRole().getId().equals(userRole.getId()))
                    .collect(java.util.stream.Collectors.toList());
            for (UserEntity u : userUsers) {
                u.setRole(readerRole);
                userRepository.save(u);
            }
            roleRepository.delete(userRole);
            System.out.println("✅ Migrated USER users to READER and deleted legacy USER role.");
        }

        System.out.println("✅ Database roles verification complete: ADMIN, MODERATOR, AUTHOR, TRANSLATOR, READER exist.");
    }

    private void createAdmin() {
        if (!userRepository.existsByUsername("admin")) {
            RoleEntity adminRole = roleRepository.findByRoleName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("Admin role not found"));

            UserEntity admin = UserEntity.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("System Administrator")
                    .email("admin@comiverse.com")
                    .phone("0123456789")
                    .role(adminRole)
                    .build();

            userRepository.save(admin);
            System.out.println("Created admin: admin / admin123");
        }
    }

    private void createStaffs() {
        createStaff("staff1", "Staff Member 1", "staff1@comiverse.com", "0987654321");
        createStaff("staff2", "Staff Member 2", "staff2@comiverse.com", "0987654322");
    }

    private void createStaff(String username, String fullName, String email, String phone) {
        if (!userRepository.existsByUsername(username)) {
            RoleEntity staffRole = roleRepository.findByRoleName("MODERATOR")
                    .orElseThrow(() -> new RuntimeException("Moderator role not found"));

            UserEntity staff = UserEntity.builder()
                    .username(username)
                    .password(passwordEncoder.encode("staff123"))
                    .fullName(fullName)
                    .email(email)
                    .phone(phone)
                    .role(staffRole)
                    .status("ACTIVE")
                    .build();

            userRepository.save(staff);
            System.out.println("Created " + username + ": " + username + " / staff123");
        }
    }
}
