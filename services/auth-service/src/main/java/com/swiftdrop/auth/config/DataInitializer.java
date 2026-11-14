package com.swiftdrop.auth.config;

import com.swiftdrop.auth.entity.Role;
import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDemoUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            seedOrUpdateUser(userRepository, passwordEncoder, "customer@demo.com", "Amal Perera", "demo1234", "077-1234567", Role.customer);
            seedOrUpdateUser(userRepository, passwordEncoder, "merchant@demo.com", "Lanka Traders", "demo1234", "011-5678900", Role.merchant);
            seedOrUpdateUser(userRepository, passwordEncoder, "courier@demo.com", "Nimal Senanayake", "demo1234", "076-9876543", Role.courier);
            seedOrUpdateUser(userRepository, passwordEncoder, "dispatcher@demo.com", "Saman Kumarasinghe", "demo1234", "011-2345678", Role.dispatcher);
        };
    }

    private void seedOrUpdateUser(UserRepository repo, PasswordEncoder encoder, String email, String name, String rawPass, String phone, Role role) {
        repo.findByEmail(email).ifPresentOrElse(
            user -> {
                if (user.getPassword() == null || user.getPassword().isBlank()) {
                    user.setPassword(encoder.encode(rawPass));
                    repo.save(user);
                }
            },
            () -> {
                User newUser = new User(name, email, encoder.encode(rawPass), phone, role);
                repo.save(newUser);
            }
        );
    }
}
