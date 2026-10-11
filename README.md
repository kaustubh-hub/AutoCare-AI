# AutoCare AI - Vehicle Service Manager (MySQL edition)

All data is stored in **MySQL only**: sign-up/login details, vehicles (number plate, VIN, PUC / insurance / last-service dates, fuel,
odometer, condition levels), and every service booking (issue, technician/advisor, notes, diagnosis, invoice).

## Setup in 5 steps
1. **Install MySQL Server** (8.x) and note your root password. Make sure it is running.
2. **Edit `autocare.properties`**: set `db.user` and `db.password` (and `db.host` / `db.port` if not default).
3. **Get the JDBC driver**: run `download-driver.sh` (Mac/Linux) or `download-driver.bat` (Windows).
   If that fails, download "Platform Independent" from https://dev.mysql.com/downloads/connector/j/ , unzip it and copy
   `mysql-connector-j-*.jar` into the `lib` folder.
4. **Build**: `./build.sh` (Mac/Linux) or `build.bat` (Windows).
5. **Run**: `./run.sh` or `run.bat`. The sign-in page footer should say "Connected to MySQL".

The database `autocare_ai` and its tables are created automatically. (`schema.sql` is there if you want to create them by hand.)

## Tables
`ac_users` (login) -> `ac_vehicles` (garage) and `ac_reports` (service bookings + invoices). Deleting a user removes their rows.

## Check the stored data
```
mysql -u root -p autocare_ai
SELECT user_id, username, phone, last_login FROM ac_users;
SELECT make, model, registration, puc_expiry, insurance_expiry, last_service FROM ac_vehicles;
SELECT invoice_no, issue, advisor, notes, total FROM ac_reports;
```

## Troubleshooting (the sign-in page shows the reason in red, with a Retry button)
| Message | Fix |
|---|---|
| MySQL driver not found | Step 3 - jar must be inside `lib/` |
| MySQL rejected the username/password | Fix `db.user` / `db.password` |
| Can't reach MySQL | Start the MySQL server; check `db.host` / `db.port` |

## Optional online AI assistant
Set `ai.api.key` (or the `ANTHROPIC_API_KEY` env var). Without it the built-in assistant answers.
