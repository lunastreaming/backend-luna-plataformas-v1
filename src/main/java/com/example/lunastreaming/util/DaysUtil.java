package com.example.lunastreaming.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class DaysUtil {

    public static Integer daysRemainingFromTimestamp(Timestamp publishEndTimestamp, ZoneId zone) {
        if (publishEndTimestamp == null) return null;

        Instant publishEndInstant = publishEndTimestamp.toInstant();
        LocalDate today = LocalDate.now(zone);
        LocalDate endDate = publishEndInstant.atZone(zone).toLocalDate();

        long days = ChronoUnit.DAYS.between(today, endDate);
        return (int) Math.max(days, 0);
    }

    // Overload si usas Instant directamente
    public static Integer daysRemainingFromInstant(Instant publishEndInstant, ZoneId zone) {
        if (publishEndInstant == null) return null;
        LocalDate today = LocalDate.now(zone);
        LocalDate endDate = publishEndInstant.atZone(zone).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, endDate);
        return (int) Math.max(days, 0);
    }

}
