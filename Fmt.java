package ai;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** Formatting helpers shared across the app. */
final class Fmt {
    static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter IN = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);

    static String date(LocalDate d) { return d == null ? "Not set" : D.format(d); }

    /** Blank -> null. Invalid -> IllegalArgumentException with a friendly message. */
    static LocalDate parse(String s) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) return null;
        try { return LocalDate.parse(s, IN); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Please type dates as dd/MM/yyyy (for example 25/12/2026)."); }
    }
    static String money(double v) { return new DecimalFormat("#,##0.00").format(v); }
    static String km(int v) { return new DecimalFormat("#,##0").format(v) + " km"; }
}
