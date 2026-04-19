CREATE TABLE payment_schedule (
                                id UUID PRIMARY KEY,
                                loan_application_id UUID REFERENCES loan_application(id),
                                payment_nr INT NOT NULL,
                                payment_date DATE NOT NULL,
                                total_payment DECIMAL(19, 2) NOT NULL,
                                principal DECIMAL(19, 2) NOT NULL,
                                interest DECIMAL(19, 2) NOT NULL,
                                remaining_balance DECIMAL(19, 2) NOT NULL
);