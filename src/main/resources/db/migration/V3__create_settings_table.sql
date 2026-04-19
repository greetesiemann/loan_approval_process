CREATE TABLE settings (
                          key VARCHAR(50) PRIMARY KEY,
                          value VARCHAR(255) NOT NULL,
                          description VARCHAR(255)
);

INSERT INTO settings (key, value, description) VALUES ('MAX_AGE', '70', 'Maksimaalne kliendi vanus');
INSERT INTO settings (key, value, description) VALUES ('MIN_AGE', '18', 'Minimaalne kliendi vanus');
INSERT INTO settings (key, value, description) VALUES ('EURIBOR_6M', '3.85', '6 kuu Euribori määr protsentides');