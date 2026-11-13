package com.acme.salary;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {
    @Bean
    UserDetailsService hrAccount(@Value("${acme.hr.username}") String username,
                                 @Value("${acme.hr.password}") String password) {
        if (username.isBlank() || username.length() > 100 || password.isBlank()) {
            throw new IllegalArgumentException("Set valid HR_USERNAME and HR_PASSWORD values.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
            .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password))
            .roles("HR").build());
    }

    @Bean
    SecurityFilterChain web(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(rules -> rules
                .requestMatchers("/api/health", "/api/csrf", "/api/login").permitAll()
                .requestMatchers("/api/**").hasRole("HR")
                .anyRequest().permitAll())
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .formLogin(login -> login.loginPage("/").loginProcessingUrl("/api/login")
                .successHandler((request, response, auth) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .failureHandler((request, response, error) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
            .logout(logout -> logout.logoutUrl("/api/logout")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
            .exceptionHandling(errors -> errors.authenticationEntryPoint(
                (request, response, error) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
            .requestCache(cache -> cache.disable())
            .build();
    }
}
