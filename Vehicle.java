package ai;

import java.io.Serializable;
import java.time.LocalDate;

class Vehicle implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    int id, userId, year, mileage;
    String nickname = "", make = "", model = "", registration = "", vin = "", fuelType = "Petrol (Turbocharged)";
    int colorRgb = 0x3B82F6;
    LocalDate pucExpiry, insuranceExpiry, lastService;
    String serviceNotes = "";
    // Condition percentages (100 = like new)
    int oil = 80, brakes = 80, tyres = 80, battery = 80, coolant = 80;

    boolean isElectric() { return fuelType != null && fuelType.startsWith("Electric"); }
    String displayName() {
        if (nickname != null && !nickname.isBlank()) return nickname;
        String n = (make + " " + model).trim();
        return n.isEmpty() ? "Unnamed vehicle" : n;
    }
    Vehicle copy() { try { return (Vehicle) clone(); } catch (CloneNotSupportedException e) { throw new IllegalStateException(e); } }
    @Override public String toString() { return displayName(); }
}
