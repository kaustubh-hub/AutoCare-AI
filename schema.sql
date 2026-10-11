-- AutoCare AI - MySQL schema (OPTIONAL: the app creates all of this automatically on first start).
-- Run it yourself only if you prefer to create the database manually:
--   mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS autocare_ai CHARACTER SET utf8mb4;
USE autocare_ai;

-- Sign-in / registration details (password is stored as a salted PBKDF2 hash, never plain text)
CREATE TABLE IF NOT EXISTS ac_users (
  user_id       INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(60) NOT NULL UNIQUE,
  phone         VARCHAR(25),
  password_hash VARCHAR(200) NOT NULL,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_login    DATETIME NULL
) CHARACTER SET utf8mb4;

-- Everything entered in "My Garage"
CREATE TABLE IF NOT EXISTS ac_vehicles (
  vehicle_id       INT AUTO_INCREMENT PRIMARY KEY,
  user_id          INT NOT NULL,
  nickname         VARCHAR(80),
  make             VARCHAR(60),
  model            VARCHAR(60),
  model_year       INT,
  registration     VARCHAR(30),   -- number plate
  vin              VARCHAR(40),   -- chassis number
  fuel_type        VARCHAR(40),
  mileage          INT,
  color_rgb        INT,
  puc_expiry       DATE NULL,
  insurance_expiry DATE NULL,
  last_service     DATE NULL,
  service_notes    TEXT,
  oil INT, brakes INT, tyres INT, battery INT, coolant INT,   -- condition sliders (0-100)
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES ac_users(user_id) ON DELETE CASCADE
) CHARACTER SET utf8mb4;

-- Everything entered in "Book Service" + the generated diagnosis and invoice
CREATE TABLE IF NOT EXISTS ac_reports (
  report_id        INT AUTO_INCREMENT PRIMARY KEY,
  user_id          INT NOT NULL,
  vehicle_id       INT NULL,
  invoice_no       VARCHAR(30),
  vehicle_name     VARCHAR(120),
  registration     VARCHAR(30),
  customer         VARCHAR(80),
  issue            VARCHAR(80),
  advisor          VARCHAR(80),   -- technician / service advisor chosen by the user
  mileage          INT,
  health_score     INT,
  created_on       DATE,
  next_service_due DATE NULL,
  diagnostic_fee DOUBLE, parts_cost DOUBLE, subtotal DOUBLE, gst DOUBLE, total DOUBLE,
  points           INT,
  diagnosis_text   MEDIUMTEXT,
  invoice_text     MEDIUMTEXT,
  notes            TEXT,          -- "notes for the technician"
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES ac_users(user_id) ON DELETE CASCADE
) CHARACTER SET utf8mb4;
