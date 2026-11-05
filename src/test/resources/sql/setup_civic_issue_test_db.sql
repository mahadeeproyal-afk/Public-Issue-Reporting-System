-- Test-only database setup. Never execute this script against civic_issue_db.
CREATE DATABASE IF NOT EXISTS civic_issue_test_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE civic_issue_test_db;

CREATE TABLE IF NOT EXISTS app_users (
    user_id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(15) DEFAULT NULL,
    role ENUM('CITIZEN','ADMIN') NOT NULL DEFAULT 'CITIZEN',
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS departments (
    department_id INT NOT NULL AUTO_INCREMENT,
    department_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (department_id),
    UNIQUE KEY department_name (department_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS issues (
    issue_id INT NOT NULL AUTO_INCREMENT,
    tracking_code VARCHAR(30) NOT NULL,
    citizen_id INT NOT NULL,
    department_id INT DEFAULT NULL,
    title VARCHAR(150) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    location VARCHAR(255) NOT NULL,
    status ENUM('SUBMITTED','UNDER_REVIEW','ASSIGNED','IN_PROGRESS','RESOLVED','REJECTED')
        NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (issue_id),
    UNIQUE KEY tracking_code (tracking_code),
    KEY fk_issue_citizen (citizen_id),
    KEY fk_issue_department (department_id),
    CONSTRAINT fk_issue_citizen FOREIGN KEY (citizen_id)
        REFERENCES app_users (user_id),
    CONSTRAINT fk_issue_department FOREIGN KEY (department_id)
        REFERENCES departments (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS issue_history (
    history_id INT NOT NULL AUTO_INCREMENT,
    issue_id INT NOT NULL,
    changed_by INT NOT NULL,
    old_status VARCHAR(30) DEFAULT NULL,
    new_status VARCHAR(30) NOT NULL,
    remarks TEXT,
    changed_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    KEY fk_history_issue (issue_id),
    KEY fk_history_user (changed_by),
    CONSTRAINT fk_history_issue FOREIGN KEY (issue_id)
        REFERENCES issues (issue_id),
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by)
        REFERENCES app_users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- These are synthetic test fixtures; no production rows are copied.
INSERT INTO app_users (name, email, password_hash, role)
VALUES ('Test Citizen', 'test.citizen@civic-issue.invalid',
        'TEST_FIXTURE_NO_PASSWORD_AUTHENTICATION', 'CITIZEN')
ON DUPLICATE KEY UPDATE name = VALUES(name),
                        password_hash = VALUES(password_hash), role = VALUES(role);

INSERT INTO app_users (name, email, password_hash, role)
VALUES ('Test Administrator', 'test.admin@civic-issue.invalid',
        'TEST_FIXTURE_NO_PASSWORD_AUTHENTICATION', 'ADMIN')
ON DUPLICATE KEY UPDATE name = VALUES(name),
                        password_hash = VALUES(password_hash), role = VALUES(role);

INSERT INTO departments (department_name)
VALUES ('TEST - Public Works'), ('TEST - Water Supply')
ON DUPLICATE KEY UPDATE department_name = VALUES(department_name);
