CREATE TABLE IF NOT EXISTS employee (
    id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(60) NOT NULL,
    department VARCHAR(60) NOT NULL,
    currency CHAR(3) NOT NULL,
    salary_minor BIGINT NOT NULL CHECK (salary_minor BETWEEN 1 AND 1000000000000),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE INDEX IF NOT EXISTS employee_country_department ON employee(country, department, id);
CREATE INDEX IF NOT EXISTS employee_department ON employee(department, id);

CREATE TABLE IF NOT EXISTS salary_change (
    employee_id VARCHAR(10) NOT NULL REFERENCES employee(id),
    version BIGINT NOT NULL,
    previous_minor BIGINT NOT NULL,
    salary_minor BIGINT NOT NULL,
    reason VARCHAR(240) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (employee_id, version)
);
