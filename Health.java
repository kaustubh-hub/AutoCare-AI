package ai;

import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Vehicle health scoring, alerts, AI-style recommendations, pricing and invoice text. */
final class Health {
    static final String[] FUELS = {"Petrol (Turbocharged)", "Diesel (CRDi)", "Electric", "Plug-in Hybrid"};
    static final String[] ISSUES = {"Routine Maintenance", "Engine Overheating", "Brake Squealing", "AC Not Cooling", "Suspension Noise",
            "Battery / Starting Problem", "Tyre Wear / Vibration", "Check Engine Light", "Oil Leak"};
    static final String[] ADVISORS = {"Dr. Alex (AI Lead)", "Er. Rahul (Powertrain)", "Er. Priya (Chassis)"};
    static final double DIAG_FEE = 1500.00, GST_RATE = 0.18;
    private static final Random RND = new Random();

    static class Alert {
        final int level; // 0 info, 1 warning, 2 critical
        final String text;
        Alert(int level, String text) { this.level = level; this.text = text; }
    }

    // ---------- scoring ----------
    static String[] metricNames(Vehicle v) {
        return new String[]{v.isElectric() ? "Motor & drivetrain" : "Engine oil life", "Brake pads", "Tyre tread", "Battery", "Coolant & cooling"};
    }
    static int[] metrics(Vehicle v) { return new int[]{v.oil, v.brakes, v.tyres, v.battery, v.coolant}; }

    static int score(Vehicle v) {
        double s = v.oil * .25 + v.brakes * .25 + v.tyres * .20 + v.battery * .15 + v.coolant * .15;
        if (v.mileage > 80000) s -= 10; else if (v.mileage > 40000) s -= 5;
        long svc = days(nextServiceDue(v));
        if (svc != NONE && svc < 0) s -= Math.min(15, 3 + (-svc) / 10.0);
        long puc = days(v.pucExpiry);
        if (puc != NONE && puc < 0) s -= 8;
        long ins = days(v.insuranceExpiry);
        if (ins != NONE && ins < 0) s -= 8;
        return (int) Math.max(0, Math.min(100, Math.round(s)));
    }
    static String status(int s) { return s >= 80 ? "Excellent" : s >= 60 ? "Good" : s >= 40 ? "Needs attention" : "Critical"; }
    static Color color(int pct) { return pct >= 70 ? Theme.success : pct >= 40 ? Theme.warn : Theme.danger; }

    // ---------- dates ----------
    static final long NONE = Long.MIN_VALUE;
    static LocalDate nextServiceDue(Vehicle v) { return v.lastService == null ? null : v.lastService.plusMonths(6); }
    static long days(LocalDate d) { return d == null ? NONE : ChronoUnit.DAYS.between(LocalDate.now(), d); }
    static String relative(long d) {
        if (d == NONE) return "Not set";
        if (d == 0) return "Today";
        if (d > 0) return "in " + d + (d == 1 ? " day" : " days");
        return (-d) + (d == -1 ? " day" : " days") + " overdue";
    }

    static List<Alert> alerts(Vehicle v) {
        List<Alert> out = new ArrayList<>();
        dateAlert(out, "PUC certificate", v.pucExpiry, v.isElectric() ? "EVs are often exempt - add a date only if you have one." : "Add your PUC expiry date to get reminders.", true);
        dateAlert(out, "Insurance", v.insuranceExpiry, "Add your insurance expiry date to get reminders.", false);
        dateAlert(out, "Next service", nextServiceDue(v), "Add your last service date so we can schedule the next one.", true);
        String[] n = metricNames(v);
        int[] m = metrics(v);
        for (int i = 0; i < m.length; i++) {
            if (m[i] < 30) out.add(new Alert(2, n[i] + " is critically low (" + m[i] + "%). Get it checked soon."));
            else if (m[i] < 50) out.add(new Alert(1, n[i] + " is getting low (" + m[i] + "%)."));
        }
        if (v.mileage > 80000) out.add(new Alert(0, "High mileage (" + Fmt.km(v.mileage) + "): a comprehensive inspection is recommended."));
        out.sort((a, b) -> Integer.compare(b.level, a.level));
        return out;
    }
    private static void dateAlert(List<Alert> out, String what, LocalDate d, String missing, boolean showMissing) {
        long x = days(d);
        if (x == NONE) { if (showMissing) out.add(new Alert(0, missing)); }
        else if (x < 0) out.add(new Alert(2, what.equals("Next service") ? "Service is overdue by " + (-x) + " days - please book it soon." : what + " expired " + (-x) + " days ago - renew immediately."));
        else if (x <= 30) out.add(new Alert(1, what + " is due " + relative(x) + " (" + Fmt.date(d) + ")."));
    }

    // ---------- loyalty ----------
    static int points(List<Report> rs) { int p = 0; for (Report r : rs) p += r.pointsEarned; return p; }
    static String tier(int p) { return p >= 600 ? "Platinum" : p >= 300 ? "Gold" : p >= 100 ? "Silver" : "Bronze"; }
    static int nextTierAt(int p) { return p >= 600 ? 600 : p >= 300 ? 600 : p >= 100 ? 300 : 100; }

    // ---------- pricing ----------
    static double partsCost(String issue) {
        switch (issue) {
            case "Engine Overheating": return 5200.00;
            case "Brake Squealing": return 3100.00;
            case "AC Not Cooling": return 2800.00;
            case "Suspension Noise": return 6400.00;
            case "Battery / Starting Problem": return 4800.00;
            case "Tyre Wear / Vibration": return 7200.00;
            case "Check Engine Light": return 2400.00;
            case "Oil Leak": return 3500.00;
            default: return 950.00;
        }
    }
    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    static double estimateTotal(String issue) { double sub = DIAG_FEE + partsCost(issue); return round2(sub + round2(sub * GST_RATE)); }

    // ---------- report generation ----------
    static Report makeReport(User u, Vehicle v, String issue, String advisor, LocalDate on) { return makeReport(u, v, issue, advisor, on, ""); }

    static Report makeReport(User u, Vehicle v, String issue, String advisor, LocalDate on, String notes) {
        Report r = new Report();
        r.notes = notes == null ? "" : notes;
        r.userId = u.id; r.vehicleId = v.id; r.vehicleName = v.displayName(); r.registration = v.registration;
        r.customer = u.username; r.issue = issue; r.advisor = advisor; r.mileage = v.mileage;
        r.healthScore = score(v); r.createdOn = on; r.nextServiceDue = nextServiceDue(v);
        r.diagnosticFee = DIAG_FEE; r.partsCost = partsCost(issue);
        r.subtotal = round2(r.diagnosticFee + r.partsCost);
        r.gst = round2(r.subtotal * GST_RATE);
        r.total = round2(r.subtotal + r.gst);
        r.pointsEarned = (int) (r.total / 100);
        r.invoiceNo = String.format("INV-%s-%04d", on.format(DateTimeFormatter.BASIC_ISO_DATE), RND.nextInt(10000));
        r.diagnosisText = diagnosis(u, v, issue, advisor, on, r.notes);
        r.invoiceText = invoice(u, v, r);
        return r;
    }

    private static String bar(int pct) {
        int f = Math.max(0, Math.min(10, Math.round(pct / 10f)));
        return "[" + "#".repeat(f) + "-".repeat(10 - f) + "]";
    }
    private static final String EQ = "=".repeat(70), DASH = "-".repeat(70);

    static String diagnosis(User u, Vehicle v, String issue, String advisor, LocalDate on, String notes) {
        StringBuilder sb = new StringBuilder();
        int score = score(v);
        sb.append(EQ).append("\n            AUTOCARE AI  -  DIAGNOSTIC REPORT\n").append(EQ).append("\n");
        sb.append("Report date : ").append(Fmt.date(on)).append("\n\n");
        sb.append("CUSTOMER\n").append(DASH).append("\n");
        sb.append(String.format("  Name              : %s%n  Contact           : %s%n%n", u.username, u.phone == null ? "-" : u.phone));
        sb.append("VEHICLE\n").append(DASH).append("\n");
        sb.append("  Vehicle           : ").append(((v.year > 0 ? v.year + " " : "") + v.make + " " + v.model).trim()).append("\n");
        sb.append(String.format("  Registration      : %s%n  Chassis (VIN)     : %s%n  Fuel type         : %s%n  Odometer          : %s%n",
                v.registration, v.vin.isEmpty() ? "-" : v.vin, v.fuelType, Fmt.km(v.mileage)));
        sb.append(String.format("  Last service      : %s%n  Next service due  : %s%n  PUC expiry        : %s%n  Insurance expiry  : %s%n%n",
                Fmt.date(v.lastService), Fmt.date(nextServiceDue(v)), Fmt.date(v.pucExpiry), Fmt.date(v.insuranceExpiry)));
        sb.append("HEALTH SNAPSHOT  -  ").append(score).append("/100 (").append(status(score)).append(")\n").append(DASH).append("\n");
        String[] n = metricNames(v);
        int[] m = metrics(v);
        for (int i = 0; i < n.length; i++) sb.append(String.format("  %-20s %s %3d%%%n", n[i], bar(m[i]), m[i]));
        sb.append("\nALERTS\n").append(DASH).append("\n");
        List<Alert> al = alerts(v);
        if (al.isEmpty()) sb.append("  [OK] No active alerts - everything looks on track.\n");
        for (Alert a : al) sb.append(a.level == 2 ? "  [!!] " : a.level == 1 ? "  [! ] " : "  [i ] ").append(a.text).append("\n");

        sb.append("\nAI RECOMMENDATIONS (based on ").append(Fmt.km(v.mileage)).append(")\n").append(DASH).append("\n");
        if (v.mileage > 80000) {
            sb.append("  CRITICAL SERVICE REQUIRED\n   * Timing chain / belt inspection or replacement\n   * Complete fluid renewal recommended\n   * Suspension inspection critical\n   * Brake system comprehensive check\n");
        } else if (v.mileage > 40000) {
            sb.append("  INTERMEDIATE SERVICE RECOMMENDED\n   * Spark plug inspection\n   * Filter replacement\n   * System diagnostics\n   * Fluid level check\n");
        } else {
            sb.append("  ROUTINE MAINTENANCE SUFFICIENT\n   * Oil change\n   * Filter replacement\n   * Standard checks\n   * Visual inspection\n");
        }
        sb.append("\nISSUE ANALYSIS: ").append(issue).append("\n").append(DASH).append("\n");
        for (String s : steps(issue)) sb.append("   * ").append(s).append("\n");
        sb.append("\nService advisor : ").append(advisor).append("\n");
        if (notes != null && !notes.isBlank()) sb.append("Customer notes  : ").append(notes).append("\n");
        sb.append("Estimated cost  : INR ").append(Fmt.money(estimateTotal(issue))).append(" (incl. 18% GST)\n");
        sb.append(EQ).append("\n");
        return sb.toString();
    }

    static String[] steps(String issue) {
        switch (issue) {
            case "Engine Overheating": return new String[]{"Coolant system pressure test", "Thermostat verification", "Radiator inspection and cleaning", "Fan functionality check"};
            case "Brake Squealing": return new String[]{"Friction plate (pad) measurement", "Rotor resurfacing", "Brake fluid flush and replacement", "Caliper inspection"};
            case "AC Not Cooling": return new String[]{"Refrigerant level check", "Compressor diagnostics", "Cabin filter cleaning/replacement", "Evaporator inspection"};
            case "Suspension Noise": return new String[]{"Suspension component inspection", "Joint stability test", "Alignment check", "Shock absorber evaluation"};
            case "Battery / Starting Problem": return new String[]{"Battery load test", "Alternator output check", "Terminal cleaning and tightening", "Starter motor check"};
            case "Tyre Wear / Vibration": return new String[]{"Tread depth measurement", "Wheel balancing", "Wheel alignment check", "Pressure and sidewall inspection"};
            case "Check Engine Light": return new String[]{"OBD-II fault code scan", "Oxygen / MAF sensor checks", "Ignition and fuel system review", "Emission system check"};
            case "Oil Leak": return new String[]{"Leak source tracing", "Gasket and seal inspection", "Oil pan and drain plug check", "Top-up and re-test"};
            default: return new String[]{"OBD-II system scan", "General diagnostics", "Performance check", "Safety verification"};
        }
    }

    static String invoice(User u, Vehicle v, Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append(EQ).append("\n                 AUTOCARE AI  -  SERVICE INVOICE\n").append(EQ).append("\n");
        sb.append(String.format("Invoice no.      : %s%nDate             : %s%n", r.invoiceNo, Fmt.date(r.createdOn)));
        sb.append(String.format("Customer         : %s%nVehicle          : %s (%s)%nChassis (VIN)    : %s%nService advisor  : %s%nIssue reported   : %s%n",
                u.username, v.displayName(), v.registration, v.vin.isEmpty() ? "-" : v.vin, r.advisor, r.issue));
        sb.append(EQ).append("\n");
        sb.append(String.format("%-52s %16s%n", "DESCRIPTION", "AMOUNT (INR)"));
        sb.append(DASH).append("\n");
        sb.append(String.format("%-52s %16s%n", "1. Vehicle evaluation & diagnostic fee", Fmt.money(r.diagnosticFee)));
        sb.append(String.format("%-52s %16s%n", "2. Spares, lubricants & hardware", Fmt.money(r.partsCost)));
        sb.append(DASH).append("\n");
        sb.append(String.format("%-52s %16s%n", "Subtotal", Fmt.money(r.subtotal)));
        sb.append(String.format("%-52s %16s%n", "GST (18%)", Fmt.money(r.gst)));
        sb.append(EQ).append("\n");
        sb.append(String.format("%-52s %16s%n", "TOTAL PAYABLE", Fmt.money(r.total)));
        sb.append(EQ).append("\n\n");
        sb.append("Loyalty points earned with this visit: ").append(r.pointsEarned).append("\n");
        sb.append("Thank you for choosing AutoCare AI. Drive safe!\n");
        return sb.toString();
    }
}
