package dev.portfolio.expenses;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class ExpenseService {
    private final ExpenseRepository repository;

    ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    @Transactional
    ExpenseResponse create(ExpenseRequest request) {
        return repository.save(new Expense(request)).toResponse();
    }

    ExpenseResponse get(UUID id) {
        return find(id).toResponse();
    }

    ExpensePage list(Category category, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        Specification<Expense> filter = (root, query, cb) -> cb.conjunction();
        if (category != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("category"), category));
        if (from != null) filter = filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("incurredOn"), from));
        if (to != null) filter = filter.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("incurredOn"), to));
        var result = repository.findAll(filter,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("incurredOn"), Sort.Order.asc("id"))));
        return new ExpensePage(result.map(Expense::toResponse).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    ExpenseResponse update(UUID id, ExpenseRequest request) {
        var expense = find(id);
        expense.update(request);
        return expense.toResponse();
    }

    @Transactional
    void delete(UUID id) {
        repository.delete(find(id));
    }

    private Expense find(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }
}
