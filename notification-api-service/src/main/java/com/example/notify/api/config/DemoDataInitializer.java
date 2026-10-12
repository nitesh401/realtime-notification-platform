package com.example.notify.api.config;

import com.example.notify.api.domain.Role;
import com.example.notify.api.domain.User;
import com.example.notify.api.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Local demo accounts, created only when SEED_DEMO_USERS=true; the password comes from DEMO_PASSWORD. */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DemoDataInitializer(AppProperties props, UserRepository users, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            return;
        }
        String password = props.seed().password();
        if (password == null || password.length() < 10) {
            log.warn("SEED_DEMO_USERS is true but DEMO_PASSWORD is missing or shorter than 10 characters; skipping seed");
            return;
        }
        seed("alice", "alice@example.test", Role.USER, password);
        seed("bob", "bob@example.test", Role.USER, password);
        seed("admin", "admin@example.test", Role.ADMIN, password);
        // Demonstrates a permanently failing email address (see docs/failure-scenarios.md).
        seed("bounce", "bounce@fail.example.test", Role.USER, password);
    }

    private void seed(String username, String email, Role role, String password) {
        if (!users.existsByUsername(username)) {
            users.save(new User(username, email, encoder.encode(password), role));
            log.info("Seeded demo user '{}'", username);
        }
    }
}
