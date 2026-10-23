package com.smartinsurance.controller;

import com.smartinsurance.entity.SlaConfiguration;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.service.SlaService;
import com.smartinsurance.service.SlaService.SlaRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/admin/slas")
@PreAuthorize("hasRole('ADMIN')")
public class SlaController {
    private final SlaService slas;
    public SlaController(SlaService slas) { this.slas = slas; }
    @GetMapping
    public List<SlaConfiguration> list() { return slas.all(); }
    @PostMapping
    public SlaConfiguration save(@AuthenticationPrincipal AppUser actor, @Valid @RequestBody SlaRequest request) {
        return slas.save(actor, request.claimType(), request.durationDays(), request.active());
    }
    @DeleteMapping("/{id}")
    public void deactivate(@AuthenticationPrincipal AppUser actor, @PathVariable Long id) { slas.remove(actor, id); }
}
