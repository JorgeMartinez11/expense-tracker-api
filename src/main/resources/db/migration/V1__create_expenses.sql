CREATE TABLE expenses (
    id UUID PRIMARY KEY,
    description VARCHAR(120) NOT NULL CHECK (length(trim(description)) > 0),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    category VARCHAR(20) NOT NULL CHECK (category IN ('FOOD', 'TRANSPORT', 'HOUSING', 'ENTERTAINMENT', 'HEALTH', 'OTHER')),
    incurred_on DATE NOT NULL
);

CREATE INDEX idx_expenses_date_id ON expenses (incurred_on DESC, id ASC);
CREATE INDEX idx_expenses_category_date_id ON expenses (category, incurred_on DESC, id ASC);
