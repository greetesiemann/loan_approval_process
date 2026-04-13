CREATE TABLE loan_application (
                                  id UUID PRIMARY KEY,
                                  first_name VARCHAR(32) NOT NULL,
                                  last_name VARCHAR(32) NOT NULL,
                                  personal_code VARCHAR(11) NOT NULL,
                                  loan_amount DECIMAL(19, 2) NOT NULL,
                                  loan_period_months INT NOT NULL,
                                  interest_margin DECIMAL(5, 3) NOT NULL,
                                  base_interest_rate DECIMAL(5, 3) NOT NULL,
                                  status VARCHAR(20) NOT NULL,
                                  rejection_reason VARCHAR(255),
                                  created_at TIMESTAMP NOT NULL
);