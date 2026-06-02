package com.example.lunastreaming.controller;


import com.example.lunastreaming.model.admin.InactiveUserDto;
import com.example.lunastreaming.service.InactiveUsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class InactiveUsersController {

    private final InactiveUsersService inactiveUsersService;

    @GetMapping("/inactive-users")
    public ResponseEntity<Page<InactiveUserDto>> getInactiveUsers(
            @RequestParam(name = "type", defaultValue = "purchase") String type,
            @RequestParam(name = "days", defaultValue = "15") int days,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        Page<InactiveUserDto> report = inactiveUsersService.getInactiveUsersReport(type, days, page, size);
        return ResponseEntity.ok(report);
    }

}
