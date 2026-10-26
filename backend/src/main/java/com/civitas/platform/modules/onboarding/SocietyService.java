package com.civitas.platform.modules.onboarding;

import com.civitas.platform.modules.tenants.TenantDtos;
import com.civitas.platform.modules.tenants.TenantService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SocietyService {
    private final TenantService tenantService;

    public SocietyService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public List<TenantDtos.TenantResponse> getAllSocieties() {
        return tenantService.all();
    }
}
