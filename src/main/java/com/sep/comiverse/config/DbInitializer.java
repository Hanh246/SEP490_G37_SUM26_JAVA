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
        createRoles();
        createAdmin();
        createStaffs();
    }

    private void createRoles() {
        if (roleRepository.count() == 0) {
            RoleEntity adminRole = RoleEntity.builder()
                    .roleName("ADMIN")
                    .build();
            roleRepository.save(adminRole);

            RoleEntity staffRole = RoleEntity.builder()
                    .roleName("STAFF")
                    .build();
            roleRepository.save(staffRole);

            RoleEntity userRole = RoleEntity.builder()
                    .roleName("USER")
                    .build();
            roleRepository.save(userRole);

            System.out.println("✅ Created roles: ADMIN, STAFF, USER");
        }
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
            RoleEntity staffRole = roleRepository.findByRoleName("STAFF")
                    .orElseThrow(() -> new RuntimeException("Staff role not found"));

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
