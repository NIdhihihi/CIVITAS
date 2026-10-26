package com.civitas.platform.modules.onboarding;

import com.civitas.platform.modules.tenants.TenantDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/societies")
public class SocietyController {
    private final SocietyService societyService;

    public SocietyController(SocietyService societyService) {
        this.societyService = societyService;
    }

    @GetMapping
    public List<TenantDtos.TenantResponse> list() {
        return societyService.getAllSocieties();
    }
}
