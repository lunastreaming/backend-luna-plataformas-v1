package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.DashboardIncomeDTO;
import com.example.lunastreaming.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/incomes")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<List<DashboardIncomeDTO>> getIncomes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Principal principal) {

        // "Hoy" en Perú
        LocalDate today = LocalDate.now(ZoneId.of("America/Lima"));

        LocalDate finalStart = (startDate != null) ? startDate : today.minusMonths(1);
        LocalDate finalEnd = (endDate != null) ? endDate : today;

        List<DashboardIncomeDTO> data = dashboardService.getDirectIncomes(finalStart, finalEnd);
        return ResponseEntity.ok(data);
    }

}
