package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.ApproveRequest;
import com.example.lunastreaming.model.StockResolveRequest;
import com.example.lunastreaming.model.StockResponse;
import com.example.lunastreaming.model.SupportTicketDTO;
import com.example.lunastreaming.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;


    @PostMapping
    public ResponseEntity<SupportTicketDTO> create(@RequestBody SupportTicketDTO dto) {
        return ResponseEntity.ok(supportTicketService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<SupportTicketDTO>> listAll() {
        return ResponseEntity.ok(supportTicketService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(supportTicketService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SupportTicketDTO> update(@PathVariable Long id,
                                                   @RequestBody SupportTicketDTO dto) {
        return ResponseEntity.ok(supportTicketService.update(id, dto));
    }

    // Resolver ticket con actualización de stock
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<SupportTicketDTO> resolve(@PathVariable Long id,
                                                    @RequestBody StockResolveRequest request) {
        return ResponseEntity.ok(supportTicketService.resolve(id, request));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        supportTicketService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Cliente: devuelve stocks con tickets OPEN
    @GetMapping("/client/me")
    public ResponseEntity<List<StockResponse>> listClientOpenTickets(Principal principal) {
        return ResponseEntity.ok(supportTicketService.listClientOpenAsStocks(principal));
    }

    // Proveedor: devuelve stocks con tickets IN_PROGRESS
    @GetMapping("/provider/me")
    public ResponseEntity<List<StockResponse>> listProviderInProgressTickets(Principal principal) {
        return ResponseEntity.ok(supportTicketService.listProviderInProgressAsStocks(principal));
    }

    // Cliente: devuelve stocks con tickets IN_PROCESS
    @GetMapping("/client/in-process")
    public ResponseEntity<List<StockResponse>> listClientInProcessTickets(Principal principal) {
        return ResponseEntity.ok(supportTicketService.listClientInProcessAsStocks(principal));
    }

    // Cliente: aprueba ticket (cambia de IN_PROCESS a RESOLVED)
    @PatchMapping("/{id}/approve")
    public ResponseEntity<SupportTicketDTO> approve(@PathVariable Long id,
                                                    @RequestBody(required = false) ApproveRequest request) {
        return ResponseEntity.ok(supportTicketService.approve(id, request));
    }

}
