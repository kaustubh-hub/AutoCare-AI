package ai;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** MySQL store (the only storage). Creates the database, tables and any missing columns automatically. */
final class MySqlStore implements Store {
    private final String url = Config.dbUrl(), user = Config.get("db.user"), pass = Config.get("db.password");

    MySqlStore() throws StoreException {
        DriverManager.setLoginTimeout(5);
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS ac_users (user_id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(60) NOT NULL UNIQUE, " +
                    "phone VARCHAR(25), password_hash VARCHAR(200) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, last_login DATETIME NULL) " +
                    "CHARACTER SET utf8mb4");
            st.execute("CREATE TABLE IF NOT EXISTS ac_vehicles (vehicle_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, nickname VARCHAR(80), " +
                    "make VARCHAR(60), model VARCHAR(60), model_year INT, registration VARCHAR(30), vin VARCHAR(40), fuel_type VARCHAR(40), mileage INT, " +
                    "color_rgb INT, puc_expiry DATE NULL, insurance_expiry DATE NULL, last_service DATE NULL, service_notes TEXT, " +
                    "oil INT, brakes INT, tyres INT, battery INT, coolant INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES ac_users(user_id) ON DELETE CASCADE) CHARACTER SET utf8mb4");
            st.execute("CREATE TABLE IF NOT EXISTS ac_reports (report_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, vehicle_id INT NULL, " +
                    "invoice_no VARCHAR(30), vehicle_name VARCHAR(120), registration VARCHAR(30), customer VARCHAR(80), issue VARCHAR(80), advisor VARCHAR(80), " +
                    "mileage INT, health_score INT, created_on DATE, next_service_due DATE NULL, diagnostic_fee DOUBLE, parts_cost DOUBLE, subtotal DOUBLE, " +
                    "gst DOUBLE, total DOUBLE, points INT, diagnosis_text MEDIUMTEXT, invoice_text MEDIUMTEXT, notes TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES ac_users(user_id) ON DELETE CASCADE) CHARACTER SET utf8mb4");
            // upgrade tables created by an earlier version
            addColumn(c, "ac_users", "last_login", "DATETIME NULL");
            addColumn(c, "ac_vehicles", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            addColumn(c, "ac_vehicles", "updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            addColumn(c, "ac_reports", "notes", "TEXT");
            addColumn(c, "ac_reports", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        } catch (SQLException e) { throw err(e); }
    }

    private static void addColumn(Connection c, String table, String col, String ddl) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            ps.setString(1, table); ps.setString(2, col);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) == 0) try (Statement st = c.createStatement()) { st.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + ddl); }
            }
        }
    }

    public String name() { return "MySQL"; }
    private Connection connect() throws SQLException { return DriverManager.getConnection(url, user, pass); }

    private static Date sql(LocalDate d) { return d == null ? null : Date.valueOf(d); }
    private static LocalDate local(Date d) { return d == null ? null : d.toLocalDate(); }
    /** Turns raw JDBC errors into messages a beginner can act on. */
    static StoreException err(SQLException e) {
        String m = String.valueOf(e.getMessage());
        String low = m.toLowerCase();
        String host = Config.get("db.host") + ":" + Config.get("db.port");
        if (low.contains("no suitable driver") || low.contains("driver"))
            return new StoreException("MySQL driver not found. Download mysql-connector-j (a .jar file) and put it in the 'lib' folder, then restart.", e);
        if (e.getErrorCode() == 1045 || low.contains("access denied"))
            return new StoreException("MySQL rejected the username/password. Fix db.user and db.password in autocare.properties.", e);
        if (e.getErrorCode() == 1049 || low.contains("unknown database"))
            return new StoreException("Database '" + Config.get("db.name") + "' doesn't exist. Run schema.sql in MySQL (or check db.name).", e);
        if (low.contains("communications link") || low.contains("connection refused") || low.contains("connect timed out") || low.contains("unknown host") || e.getSQLState() != null && e.getSQLState().startsWith("08"))
            return new StoreException("Can't reach MySQL at " + host + ". Make sure the MySQL server is running and db.host / db.port are right.", e);
        return new StoreException("Database error: " + m, e);
    }

    public User register(String username, String phone, String password) throws StoreException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ac_users (username, phone, password_hash) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            String hash = Passwords.hash(password);
            ps.setString(1, username); ps.setString(2, phone); ps.setString(3, hash);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                User u = new User(); u.id = rs.getInt(1); u.username = username; u.phone = phone; u.passwordHash = hash;
                return u;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new StoreException("That username is already taken. Try another one.");
        } catch (SQLException e) { throw err(e); }
    }

    public User login(String username, String password) throws StoreException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT user_id, username, phone, password_hash FROM ac_users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && Passwords.verify(password, rs.getString("password_hash"))) {
                    try (PreparedStatement up = c.prepareStatement("UPDATE ac_users SET last_login = NOW() WHERE user_id = ?")) { up.setInt(1, rs.getInt("user_id")); up.executeUpdate(); }
                    User u = new User(); u.id = rs.getInt("user_id"); u.username = rs.getString("username");
                    u.phone = rs.getString("phone"); u.passwordHash = rs.getString("password_hash");
                    return u;
                }
            }
        } catch (SQLException e) { throw err(e); }
        return null;
    }

    public List<Vehicle> vehicles(int userId) throws StoreException {
        List<Vehicle> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT * FROM ac_vehicles WHERE user_id = ? ORDER BY vehicle_id")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vehicle v = new Vehicle();
                    v.id = rs.getInt("vehicle_id"); v.userId = userId; v.nickname = nz(rs.getString("nickname"));
                    v.make = nz(rs.getString("make")); v.model = nz(rs.getString("model")); v.year = rs.getInt("model_year");
                    v.registration = nz(rs.getString("registration")); v.vin = nz(rs.getString("vin")); v.fuelType = nz(rs.getString("fuel_type"));
                    v.mileage = rs.getInt("mileage"); v.colorRgb = rs.getInt("color_rgb");
                    v.pucExpiry = local(rs.getDate("puc_expiry")); v.insuranceExpiry = local(rs.getDate("insurance_expiry"));
                    v.lastService = local(rs.getDate("last_service")); v.serviceNotes = nz(rs.getString("service_notes"));
                    v.oil = rs.getInt("oil"); v.brakes = rs.getInt("brakes"); v.tyres = rs.getInt("tyres");
                    v.battery = rs.getInt("battery"); v.coolant = rs.getInt("coolant");
                    out.add(v);
                }
            }
        } catch (SQLException e) { throw err(e); }
        return out;
    }
    private static String nz(String s) { return s == null ? "" : s; }

    public Vehicle saveVehicle(Vehicle v) throws StoreException {
        boolean insert = v.id == 0;
        String sqlText = insert
                ? "INSERT INTO ac_vehicles (user_id,nickname,make,model,model_year,registration,vin,fuel_type,mileage,color_rgb,puc_expiry,insurance_expiry,last_service,service_notes,oil,brakes,tyres,battery,coolant) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                : "UPDATE ac_vehicles SET user_id=?,nickname=?,make=?,model=?,model_year=?,registration=?,vin=?,fuel_type=?,mileage=?,color_rgb=?,puc_expiry=?,insurance_expiry=?,last_service=?,service_notes=?,oil=?,brakes=?,tyres=?,battery=?,coolant=? WHERE vehicle_id=? AND user_id=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sqlText, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, v.userId); ps.setString(i++, v.nickname); ps.setString(i++, v.make); ps.setString(i++, v.model);
            ps.setInt(i++, v.year); ps.setString(i++, v.registration); ps.setString(i++, v.vin); ps.setString(i++, v.fuelType);
            ps.setInt(i++, v.mileage); ps.setInt(i++, v.colorRgb);
            ps.setDate(i++, sql(v.pucExpiry)); ps.setDate(i++, sql(v.insuranceExpiry)); ps.setDate(i++, sql(v.lastService));
            ps.setString(i++, v.serviceNotes);
            ps.setInt(i++, v.oil); ps.setInt(i++, v.brakes); ps.setInt(i++, v.tyres); ps.setInt(i++, v.battery); ps.setInt(i++, v.coolant);
            if (!insert) { ps.setInt(i++, v.id); ps.setInt(i, v.userId); }
            ps.executeUpdate();
            Vehicle out = v.copy();
            if (insert) try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) out.id = rs.getInt(1); }
            return out;
        } catch (SQLException e) { throw err(e); }
    }

    public void deleteVehicle(int vehicleId, int userId) throws StoreException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM ac_vehicles WHERE vehicle_id=? AND user_id=?")) {
            ps.setInt(1, vehicleId); ps.setInt(2, userId); ps.executeUpdate();
        } catch (SQLException e) { throw err(e); }
    }

    public Report saveReport(Report r) throws StoreException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ac_reports (user_id,vehicle_id,invoice_no,vehicle_name,registration,customer,issue,advisor,mileage,health_score,created_on,next_service_due,diagnostic_fee,parts_cost,subtotal,gst,total,points,diagnosis_text,invoice_text,notes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, r.userId);
            if (r.vehicleId > 0) ps.setInt(i++, r.vehicleId); else ps.setNull(i++, Types.INTEGER);
            ps.setString(i++, r.invoiceNo); ps.setString(i++, r.vehicleName); ps.setString(i++, r.registration);
            ps.setString(i++, r.customer); ps.setString(i++, r.issue); ps.setString(i++, r.advisor);
            ps.setInt(i++, r.mileage); ps.setInt(i++, r.healthScore); ps.setDate(i++, sql(r.createdOn)); ps.setDate(i++, sql(r.nextServiceDue));
            ps.setDouble(i++, r.diagnosticFee); ps.setDouble(i++, r.partsCost); ps.setDouble(i++, r.subtotal);
            ps.setDouble(i++, r.gst); ps.setDouble(i++, r.total); ps.setInt(i++, r.pointsEarned);
            ps.setString(i++, r.diagnosisText); ps.setString(i++, r.invoiceText); ps.setString(i, r.notes);
            ps.executeUpdate();
            Report out = r.copy();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) out.id = rs.getInt(1); }
            return out;
        } catch (SQLException e) { throw err(e); }
    }

    public List<Report> reports(int userId) throws StoreException {
        List<Report> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT * FROM ac_reports WHERE user_id=? ORDER BY created_on, report_id")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Report r = new Report();
                    r.id = rs.getInt("report_id"); r.userId = userId; r.vehicleId = rs.getInt("vehicle_id");
                    r.invoiceNo = nz(rs.getString("invoice_no")); r.vehicleName = nz(rs.getString("vehicle_name"));
                    r.registration = nz(rs.getString("registration")); r.customer = nz(rs.getString("customer"));
                    r.issue = nz(rs.getString("issue")); r.advisor = nz(rs.getString("advisor"));
                    r.mileage = rs.getInt("mileage"); r.healthScore = rs.getInt("health_score");
                    r.createdOn = local(rs.getDate("created_on")); r.nextServiceDue = local(rs.getDate("next_service_due"));
                    r.diagnosticFee = rs.getDouble("diagnostic_fee"); r.partsCost = rs.getDouble("parts_cost");
                    r.subtotal = rs.getDouble("subtotal"); r.gst = rs.getDouble("gst"); r.total = rs.getDouble("total");
                    r.pointsEarned = rs.getInt("points"); r.diagnosisText = nz(rs.getString("diagnosis_text"));
                    r.invoiceText = nz(rs.getString("invoice_text")); r.notes = nz(rs.getString("notes"));
                    out.add(r);
                }
            }
        } catch (SQLException e) { throw err(e); }
        return out;
    }
}
