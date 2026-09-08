package dev.portfolio.expenses;

import java.util.List;

public record ExpensePage(List<ExpenseResponse> content, int page, int size,
                          long totalElements, int totalPages) {}
