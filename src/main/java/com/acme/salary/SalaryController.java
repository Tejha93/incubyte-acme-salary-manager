package com.acme.salary;

import com.acme.salary.SalaryService.Details;
import com.acme.salary.SalaryService.Filter;
import com.acme.salary.SalaryService.Options;
import com.acme.salary.SalaryService.Page;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class SalaryController {
    private final SalaryService salaries;

    SalaryController(SalaryService salaries) {
        this.salaries = salaries;
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "up");
    }

    @GetMapping("/csrf")
    Map<String, Boolean> csrf(CsrfToken token) {
        token.getToken();
        return Map.of("ready", true);
    }

    @GetMapping("/session")
    Map<String, String> session(Principal user) {
        return Map.of("username", user.getName());
    }

    @GetMapping("/options")
    Options options() {
        return salaries.options();
    }

    @GetMapping("/employees")
    Page employees(@RequestParam(defaultValue = "") String search,
                   @RequestParam(defaultValue = "") String country,
                   @RequestParam(defaultValue = "") String department,
                   @RequestParam(defaultValue = "0") int page,
                   @RequestParam(defaultValue = "25") int size) {
        if (search.length() > 100 || page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Use a search of at most 100 characters, a nonnegative page, and a page size of 1–100.");
        }
        return salaries.directory(filter(search, country, department), page, size);
    }

    @GetMapping("/insights")
    List<PaySummary> insights(@RequestParam(defaultValue = "") String country,
                              @RequestParam(defaultValue = "") String department) {
        return salaries.insights(filter("", country, department));
    }

    @GetMapping("/employees/{id}")
    Details details(@PathVariable String id) {
        return salaries.details(id);
    }

    @PutMapping("/employees/{id}/salary")
    Details revise(@PathVariable String id, @RequestBody Revision revision, Principal user) {
        if (revision.reason() == null || revision.reason().isBlank() || revision.reason().strip().length() > 240
                || revision.version() == null || revision.version() < 0) {
            throw new IllegalArgumentException("Enter a reason of up to 240 characters and a valid record version.");
        }
        return salaries.revise(id, revision.amount(), revision.reason(), revision.version(), user.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    private Filter filter(String search, String country, String department) {
        if (country.length() > 60 || department.length() > 60) {
            throw new IllegalArgumentException("Country and department must be at most 60 characters.");
        }
        return new Filter(search, country, department);
    }

    record Revision(String amount, String reason, Long version) {}
}
