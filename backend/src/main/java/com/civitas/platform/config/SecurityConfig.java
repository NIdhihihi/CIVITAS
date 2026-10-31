package com.civitas.platform.config;

import com.civitas.platform.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.civitas.platform.modules.members.MemberRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean UserDetailsService userDetailsService(MemberRepository members){return email->members.findByEmailIgnoreCase(email).map(member->org.springframework.security.core.userdetails.User.withUsername(member.getEmail()).password(member.getPasswordHash()==null?"":member.getPasswordHash()).authorities("ROLE_"+member.getRole().name()).build()).orElseThrow(()->new UsernameNotFoundException("Member not found"));}
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwtFilter)throws Exception{
        return http.csrf(csrf->csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e->e.authenticationEntryPoint((request,response,error)->{
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Authentication required or access token expired. Sign in again.\"}");
                }).accessDeniedHandler((request,response,error)->{
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Access denied by backend authorization rules.\"}");
                }))
                .authorizeHttpRequests(a->a
                        .requestMatchers("/api/health","/h2-console/**","/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/verify-email","/api/auth/resend-otp","/api/auth/login","/api/auth/admin/login").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/voting/auth/biometric").authenticated()
                        .requestMatchers(HttpMethod.POST,"/api/voting/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/tenants").authenticated()
                        .requestMatchers(HttpMethod.POST,"/api/tenants").hasRole("PLATFORM_ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/voting/ballot","/api/voting/receipt/*","/api/voting/receipt/*/download").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/voting/confirm","/api/voting/cast","/api/voting/confirmation/cancel").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/candidates/admin").hasAnyRole("PLATFORM_ADMIN","TENANT_ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/candidates/*/status").hasAnyRole("PLATFORM_ADMIN","TENANT_ADMIN")
                        .anyRequest().authenticated())
                .headers(h->h.frameOptions(f->f.sameOrigin()))
                .addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${civitas.cors.allowed-origins}")String origins){
        var config=new CorsConfiguration();
        var originList = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(originList);
        config.setAllowedOriginPatterns(Arrays.asList("http://localhost:[*]", "http://127.0.0.1:[*]", "http://10.0.2.2:[*]"));
        config.setAllowedMethods(Arrays.asList("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization","Content-Type","X-Voting-Session","X-Device-Fingerprint","X-Requested-With"));
        config.setExposedHeaders(Arrays.asList("Content-Disposition","X-CIVITAS-SHA256","X-CIVITAS-SIGNATURE"));
        config.setAllowCredentials(true);
        var source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",config);
        return source;
    }
}
