package com.acme.salary;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class SalaryService {
    private static final String COLUMNS = "id, name, country, department, currency, salary_minor, version";
    private static final RowMapper<Employee> EMPLOYEE = (row, index) -> new Employee(
        row.getString("id"), row.getString("name"), row.getString("country"), row.getString("department"),
        row.getString("currency"), row.getLong("salary_minor"), row.getLong("version")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    SalaryService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page directory(Filter filter, int page, int size) {
        var parameters = filter.parameters().addValue("size", size).addValue("offset", (long) page * size);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM employee" + Filter.WHERE, parameters, Long.class);
        var employees = jdbc.query("SELECT " + COLUMNS + " FROM employee" + Filter.WHERE
            + " ORDER BY id LIMIT :size OFFSET :offset", parameters, EMPLOYEE);
        return new Page(employees, total, page, size);
    }

    public Options options() {
        var direct = jdbc.getJdbcTemplate();
        return new Options(
            direct.queryForList("SELECT DISTINCT country FROM employee ORDER BY country", String.class),
            direct.queryForList("SELECT DISTINCT department FROM employee ORDER BY department", String.class)
        );
    }

    public List<PaySummary> insights(Filter filter) {
        var byCurrency = new LinkedHashMap<String, List<Long>>();
        jdbc.query("SELECT currency, salary_minor FROM employee" + Filter.WHERE + " ORDER BY currency, salary_minor",
            filter.parameters(), row -> {
                byCurrency.computeIfAbsent(row.getString(1), key -> new ArrayList<>()).add(row.getLong(2));
            });
        return byCurrency.entrySet().stream()
            .map(entry -> PaySummary.from(entry.getKey(), entry.getValue())).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Details details(String id) {
        var employee = employee(id);
        var history = jdbc.query("""
            SELECT version, previous_minor, salary_minor, reason, changed_by, changed_at
            FROM salary_change WHERE employee_id = :id ORDER BY version DESC
            """, new MapSqlParameterSource("id", id), (row, index) -> new Change(
                row.getLong("version"), row.getLong("previous_minor"), row.getLong("salary_minor"),
                row.getString("reason"), row.getString("changed_by"), row.getObject("changed_at", OffsetDateTime.class)
            ));
        return new Details(employee, history);
    }

    @Transactional
    public Details revise(String id, String amount, String reason, long expectedVersion, String editor) {
        var current = employee(id);
        if (current.version() != expectedVersion) {
            throw conflict();
        }
        long minor = Money.minorUnits(amount, current.currency());
        if (minor == current.salaryMinor()) {
            throw new IllegalArgumentException("Enter a salary different from the current amount.");
        }
        var parameters = new MapSqlParameterSource("id", id)
            .addValue("expected", expectedVersion).addValue("salary", minor)
            .addValue("next", expectedVersion + 1).addValue("previous", current.salaryMinor())
            .addValue("reason", reason.strip()).addValue("editor", editor)
            .addValue("timestamp", clock.instant().atOffset(ZoneOffset.UTC));

        int changed = jdbc.update("""
            UPDATE employee SET salary_minor = :salary, version = :next
            WHERE id = :id AND version = :expected
            """, parameters);
        if (changed != 1) {
            throw conflict();
        }
        jdbc.update("""
            INSERT INTO salary_change(employee_id, version, previous_minor, salary_minor, reason, changed_by, changed_at)
            VALUES (:id, :next, :previous, :salary, :reason, :editor, :timestamp)
            """, parameters);
        return details(id);
    }

    private Employee employee(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM employee WHERE id = :id",
                new MapSqlParameterSource("id", id), EMPLOYEE).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee was not found."));
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Salary changed. Reload the record and review the latest amount.");
    }

    record Employee(String id, String name, String country, String department, String currency,
                    long salaryMinor, long version) {}
    record Page(List<Employee> employees, long total, int page, int size) {}
    record Options(List<String> countries, List<String> departments) {}
    record Details(Employee employee, List<Change> history) {}
    record Change(long version, long previousMinor, long salaryMinor, String reason,
                  String changedBy, OffsetDateTime changedAt) {}

    record Filter(String search, String country, String department) {
        private static final String WHERE = """
             WHERE (:search = '' OR LOWER(name) LIKE :pattern ESCAPE '!' OR LOWER(id) LIKE :pattern ESCAPE '!')
               AND (:country = '' OR country = :country)
               AND (:department = '' OR department = :department)
            """;

        MapSqlParameterSource parameters() {
            var term = search.strip().toLowerCase(Locale.ROOT);
            var literal = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            return new MapSqlParameterSource("search", term).addValue("pattern", "%" + literal + "%")
                .addValue("country", country.strip()).addValue("department", department.strip());
        }
    }
}
