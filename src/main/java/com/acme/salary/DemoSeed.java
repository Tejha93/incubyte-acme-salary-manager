package com.acme.salary;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "acme.seed.enabled", havingValue = "true")
class DemoSeed implements ApplicationRunner {
    private static final List<Location> LOCATIONS = List.of(
        new Location("India", "INR", 850_000),
        new Location("United States", "USD", 82_000),
        new Location("United Kingdom", "GBP", 56_000),
        new Location("Germany", "EUR", 62_000),
        new Location("Japan", "JPY", 6_200_000),
        new Location("Canada", "CAD", 78_000)
    );
    private static final List<String> DEPARTMENTS = List.of(
        "Engineering", "Finance", "Operations", "People", "Sales", "Support"
    );
    private static final List<String> FIRST = List.of(
        "Aisha", "Amelia", "Arjun", "Daniel", "Elena", "Hana", "Ishaan", "Jonas",
        "Leila", "Maya", "Noah", "Oliver", "Priya", "Sofia", "Theo", "Yuki"
    );
    private static final List<String> LAST = List.of(
        "Bennett", "Chen", "Desai", "Garcia", "Ito", "Khan", "Martin", "Mehta",
        "Patel", "Roy", "Sato", "Shah", "Singh", "Taylor", "Walker", "Weber"
    );

    private final JdbcTemplate jdbc;

    DemoSeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM employee", Long.class) > 0) {
            return;
        }

        var employeeNumbers = IntStream.range(0, 10_000).boxed().toList();
        jdbc.batchUpdate("""
            INSERT INTO employee(id, name, country, department, currency, salary_minor)
            VALUES (?, ?, ?, ?, ?, ?)
            """, employeeNumbers, 500, (statement, index) -> {
                var location = LOCATIONS.get(index % LOCATIONS.size());
                var name = FIRST.get(index % FIRST.size()) + " " + LAST.get(index / FIRST.size() % LAST.size());
                var department = DEPARTMENTS.get(index / LOCATIONS.size() % DEPARTMENTS.size());
                var annual = location.base() * (75 + (index * 53L % 91)) / 100;
                statement.setString(1, "AC-%05d".formatted(index + 1));
                statement.setString(2, name);
                statement.setString(3, location.country());
                statement.setString(4, department);
                statement.setString(5, location.currency());
                statement.setLong(6, annual * Money.factor(location.currency()));
            });
    }

    private record Location(String country, String currency, long base) {}
}
