package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:salary-tests;DB_CLOSE_DELAY=-1",
    "acme.hr.password=test-password", "acme.seed.enabled=true"
})
@AutoConfigureMockMvc
class SalaryWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoSeed seed;
    @Autowired SalaryService salaries;

    @BeforeEach
    void restoreFirstEmployee() {
        jdbc.update("DELETE FROM salary_change");
        jdbc.update("UPDATE employee SET salary_minor = 63750000, version = 0 WHERE id = 'AC-00001'");
    }

    @Test
    void demoSeedIsRepeatableAndDoesNotOverwriteExistingSalaries() {
        jdbc.update("UPDATE employee SET salary_minor = 999 WHERE id = 'AC-00001'");
        seed.run(new DefaultApplicationArguments());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee", Long.class)).isEqualTo(10_000);
        assertThat(salaries.details("AC-00001").employee().salaryMinor()).isEqualTo(999);
    }

    @Test
    void directoryFiltersAndPagesWithLiteralSearch() throws Exception {
        var session = login();
        mvc.perform(get("/api/employees").session(session).param("page", "1").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(10000))
            .andExpect(jsonPath("$.employees.length()").value(2))
            .andExpect(jsonPath("$.employees[0].id").value("AC-00003"));
        mvc.perform(get("/api/employees").session(session).param("search", "  aISHA ")
                .param("country", "India").param("department", "Engineering"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.employees[0].id").value("AC-00001"))
            .andExpect(jsonPath("$.employees[0].country").value("India"));
        mvc.perform(get("/api/employees").session(session).param("search", "%_"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/employees").session(session).param("size", "101"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void insightsCoverEntirePopulationAndSeparateCurrencies() throws Exception {
        var session = login();
        mvc.perform(get("/api/insights").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        assertThat(salaries.insights(new SalaryService.Filter("", "", "")))
            .extracting(PaySummary::count).satisfies(counts ->
                assertThat(counts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10_000));
        mvc.perform(get("/api/insights").session(session).param("country", "Japan").param("department", "Engineering"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].currency").value("JPY")).andExpect(jsonPath("$[0].count").value(278));
    }

    @Test
    void changePersistsWithHistoryAndRejectsStaleEdit() throws Exception {
        var session = login();
        mvc.perform(csrf(put("/api/employees/AC-00001/salary"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"725000.25\",\"reason\":\"  Annual review  \",\"version\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.employee.salaryMinor").value(72500025))
            .andExpect(jsonPath("$.employee.version").value(1))
            .andExpect(jsonPath("$.history[0].previousMinor").value(63750000))
            .andExpect(jsonPath("$.history[0].changedBy").value("hr"))
            .andExpect(jsonPath("$.history[0].changedAt").value("2025-01-02T03:04:05Z"))
            .andExpect(jsonPath("$.history[0].reason").value("Annual review"));
        mvc.perform(csrf(put("/api/employees/AC-00001/salary"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"750000\",\"reason\":\"Stale edit\",\"version\":0}"))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/employees/AC-00001").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(1))
            .andExpect(jsonPath("$.employee.salaryMinor").value(72500025));
    }

    @Test
    void rollsBackSalaryWhenHistoryInsertFails() {
        assertThatThrownBy(() -> salaries.revise("AC-00001", "700000", "x".repeat(241), 0, "hr"))
            .isInstanceOf(DataIntegrityViolationException.class);
        var details = salaries.details("AC-00001");
        assertThat(details.employee().salaryMinor()).isEqualTo(63_750_000);
        assertThat(details.employee().version()).isZero();
        assertThat(details.history()).isEmpty();
    }

    @Test
    void rejectsInvalidRevisionAndUnknownEmployee() throws Exception {
        var session = login();
        mvc.perform(csrf(put("/api/employees/AC-00001/salary"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"-5\",\"reason\":\"Review\",\"version\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/employees/AC-99999").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthenticationAndCsrfAndInvalidatesSessionOnLogout() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
        mvc.perform(csrf(post("/api/login"), null).param("username", "hr").param("password", "incorrect"))
            .andExpect(status().isUnauthorized());
        var session = login();
        mvc.perform(put("/api/employees/AC-00001/salary").session(session)).andExpect(status().isForbidden());
        mvc.perform(csrf(post("/api/logout"), session)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(csrf(post("/api/login"), null)
                .param("username", "hr").param("password", "test-password"))
            .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request, MockHttpSession session)
            throws Exception {
        Cookie cookie = mvc.perform(get("/api/csrf")).andExpect(status().isOk())
            .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        if (session != null) request.session(session);
        return request.cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue());
    }

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2025-01-02T03:04:05Z"), ZoneOffset.UTC);
        }
    }
}
