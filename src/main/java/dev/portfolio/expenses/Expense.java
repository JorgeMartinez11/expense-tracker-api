package dev.portfolio.expenses;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expenses")
class Expense {
    @Id
    private UUID id;
    @Column(nullable = false, length = 120)
    private String description;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;
    @Column(nullable = false)
    private LocalDate incurredOn;

    protected Expense() {}

    Expense(ExpenseRequest request) {
        id = UUID.randomUUID();
        update(request);
    }

    void update(ExpenseRequest request) {
        description = request.description().strip();
        amount = request.amount();
        category = request.category();
        incurredOn = request.incurredOn();
    }

    ExpenseResponse toResponse() {
        return new ExpenseResponse(id, description, amount, "EUR", category, incurredOn);
    }
}
