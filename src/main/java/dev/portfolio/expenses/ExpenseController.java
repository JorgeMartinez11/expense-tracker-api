package dev.portfolio.expenses;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
class ExpenseController {
    private final ExpenseService service;

    ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request) {
        var expense = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/expenses/" + expense.id())).body(expense);
    }

    @GetMapping("/{id}")
    ExpenseResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    ExpensePage list(@RequestParam(required = false) Category category,
                     @RequestParam(required = false) LocalDate from,
                     @RequestParam(required = false) LocalDate to,
                     @RequestParam(defaultValue = "0") @Min(0) int page,
                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(category, from, to, page, size);
    }

    @PutMapping("/{id}")
    ExpenseResponse update(@PathVariable UUID id, @Valid @RequestBody ExpenseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
