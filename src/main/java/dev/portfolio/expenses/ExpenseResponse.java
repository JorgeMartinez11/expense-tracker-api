package dev.portfolio.expenses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(UUID id, String description, BigDecimal amount,
                              String currency, Category category, LocalDate incurredOn) {}
