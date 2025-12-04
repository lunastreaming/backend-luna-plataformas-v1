package com.example.lunastreaming.model;


import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicketDTO {

    private Long id;
    private Long stockId;
    private String issueType;
    private String description;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;
    private String resolutionNote;

}
