package ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The assistant brain.
 *  - If an Anthropic API key is configured it asks Claude (with the user's vehicle as context).
 *  - Otherwise (or on any network error) it answers from a built-in car-care knowledge base.
 */
final class ChatEngine {
    private final AutoCareAI app;
    private final String apiKey = Config.apiKey();
    private final String model = Config.get("ai.model");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    ChatEngine(AutoCareAI app) { this.app = app; }

    boolean online() { return !apiKey.isEmpty(); }

    /** log = list of {role, text}; the last entry is the new user message. Runs off the UI thread. */
    String reply(List<String[]> log) {
        String question = log.get(log.size() - 1)[1];
        if (online()) {
            try { return askClaude(log); }
            catch (Exception e) { return offline(question) + "\n\n(Online AI is unreachable right now - answered from my built-in knowledge.)"; }
        }
        return offline(question);
    }

    // ------------------------------------------------------------------ Claude API
    private String systemPrompt() {
        StringBuilder sb = new StringBuilder("You are the friendly in-app assistant for AutoCare AI, a vehicle service management desktop app used in India (prices in INR). ")
                .append("Answer car-care and app questions in a concise, practical way (max ~150 words). Use short bullet points when helpful. ")
                .append("Never claim to have booked or changed anything - you can only advise; the user does actions in the app. ")
                .append("App sections: Dashboard (3D car, health score, alerts), My Garage (add/edit vehicles and condition sliders), Book Service (AI diagnosis + invoice), History (past invoices), loyalty points (1 point per INR 100 spent).\n");
        sb.append(context());
        return sb.toString();
    }

    private String context() {
        Vehicle v = app.active;
        StringBuilder sb = new StringBuilder();
        if (app.user != null) sb.append("User: ").append(app.user.username).append(". ");
        if (v == null) return sb.append("The user has not added a vehicle yet.").toString();
        sb.append("Active vehicle: ").append(v.displayName()).append(" (").append(v.make).append(' ').append(v.model).append(", ").append(v.fuelType)
                .append("), odometer ").append(Fmt.km(v.mileage)).append(", health score ").append(Health.score(v)).append("/100. ");
        String[] n = Health.metricNames(v); int[] m = Health.metrics(v);
        for (int i = 0; i < n.length; i++) sb.append(n[i]).append(' ').append(m[i]).append("%; ");
        sb.append("Next service ").append(Fmt.date(Health.nextServiceDue(v))).append("; PUC expiry ").append(Fmt.date(v.pucExpiry))
                .append("; insurance expiry ").append(Fmt.date(v.insuranceExpiry)).append('.');
        return sb.toString();
    }

    private String askClaude(List<String[]> log) throws Exception {
        List<String[]> turns = new ArrayList<>(log);
        while (turns.size() > 12) turns.remove(0);
        while (!turns.isEmpty() && !turns.get(0)[0].equals("user")) turns.remove(0);
        StringBuilder msgs = new StringBuilder("[");
        for (int i = 0; i < turns.size(); i++) {
            if (i > 0) msgs.append(',');
            msgs.append("{\"role\":\"").append(turns.get(i)[0]).append("\",\"content\":").append(jsonStr(turns.get(i)[1])).append('}');
        }
        msgs.append(']');
        String body = "{\"model\":" + jsonStr(model) + ",\"max_tokens\":700,\"system\":" + jsonStr(systemPrompt()) + ",\"messages\":" + msgs + "}";
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(45))
                .header("content-type", "application/json").header("x-api-key", apiKey).header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        String text = extractText(res.body());
        if (text == null || text.isBlank()) throw new IllegalStateException("empty reply");
        return text.trim();
    }

    // minimal JSON helpers (no external libraries needed)
    static String jsonStr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
    static String extractText(String json) {
        int t = json.indexOf("\"type\":\"text\"");
        int k = json.indexOf("\"text\":", t < 0 ? 0 : t);
        if (k < 0) return null;
        int i = json.indexOf('"', k + 7);
        if (i < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (i = i + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') break;
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': break;
                    case 'u': sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16)); i += 4; break;
                    default: sb.append(n);
                }
            } else sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ offline expert
    private static final class Entry { final String answer; final String[] keys; Entry(String a, String[] k) { answer = a; keys = k; } }
    private static final List<Entry> KB = new ArrayList<>();
    private static void kb(String answer, String... keys) { KB.add(new Entry(answer, keys)); }

    static {
        kb("Here's how the Health Score works:\n- Five condition sliders you set in My Garage (oil/drivetrain 25%, brakes 25%, tyres 20%, battery 15%, coolant 15%) form the base.\n- Points are deducted for high mileage (over 40,000 / 80,000 km), an overdue 6-month service, and an expired PUC or insurance.\n- 80+ is Excellent, 60+ Good, 40+ Needs attention, below 40 Critical.\nKeep your sliders up to date for the most accurate score!",
                "calculated", "calculate", "formula", "how is the score", "score work", "health score");
        kb("Engine oil: most cars need a change every 5,000-10,000 km or 6-12 months, whichever comes first (synthetic oil lasts longer). Diesels often sit at 7,500-10,000 km. Always follow your owner's manual. Dark gritty oil, a burning smell or an oil-pressure light means check it right away.",
                "oil change", "engine oil", "oil", "lubricant");
        kb("Squealing brakes usually mean worn pads (the built-in wear indicator is touching the disc), glazed pads, or dust/moisture. If it's a grinding sound, stop driving soon - metal-on-metal damages the discs. Book a service with issue 'Brake Squealing' and we'll check pad thickness, rotors, fluid and calipers.",
                "squeal", "squeak", "brake", "brakes", "grinding");
        kb("If the temperature gauge climbs: switch off the AC, turn the heater to full to shed heat, pull over safely and switch off. Never open the radiator cap while hot. Common causes are low coolant, a stuck thermostat, a failing fan or a leaking radiator. Please get it checked before driving further.",
                "overheat", "temperature", "coolant", "radiator", "boiling");
        kb("AC not cooling? The usual suspects are low refrigerant (a leak), a clogged cabin filter, a dirty condenser or a failing compressor. Run the AC for 10 minutes weekly even in winter, and replace the cabin filter every 10,000-15,000 km.",
                "ac not", "air condition", "a/c", "cooling", "aircon");
        kb("Car batteries typically last 3-5 years. Warning signs: slow cranking, dim lights, a clicking sound, or a battery warning light. Keep terminals clean, avoid short trips only, and don't leave lights on. If it won't start, a jump-start works - but get the battery and alternator tested afterwards.",
                "battery", "won't start", "wont start", "not starting", "jump start", "dead");
        kb("Tyre care: check pressure monthly when cold (the recommended value is on the door-jamb sticker), rotate every 8,000-10,000 km, and replace when tread is near 1.6 mm or the tyre is 5+ years old. Vibration at speed usually means balancing or alignment.",
                "tyre", "tire", "pressure", "tread", "alignment", "balancing", "vibration");
        kb("PUC (Pollution Under Control) is the emission-test certificate required for vehicles in India. Validity is typically 6-12 months depending on the vehicle's age and your state, so check with your local RTO. Driving without a valid PUC can attract a fine. AutoCare AI tracks your PUC expiry and warns you 30 days ahead.",
                "puc", "pollution", "emission");
        kb("Valid insurance is mandatory (at least third-party). Renew before expiry - a lapse can cost you your no-claim bonus and means an inspection on renewal. Add your expiry date in My Garage and the Dashboard will remind you 30 days ahead.",
                "insurance", "policy", "no claim");
        kb("A good rule: service every 6 months or 10,000 km, whichever comes first. AutoCare AI uses 6 months after your last service date to calculate the next due date, and adds mileage-based recommendations (intermediate service past 40,000 km, comprehensive past 80,000 km).",
                "how often", "interval", "maintenance", "when to service", "service schedule");
        kb("Suspension noises (clunks, knocks, creaks over bumps) often come from worn bushes, drop links, shock absorbers or ball joints. A quick inspection and alignment check is advisable - worn suspension also wears tyres faster. Choose 'Suspension Noise' when you book.",
                "suspension", "shock", "clunk", "knocking", "bumps");
        kb("A check-engine light means the ECU has logged a fault. Steady = get it scanned soon; flashing = reduce speed and stop driving as soon as it's safe (possible misfire that can damage the catalytic converter). An OBD-II scan reads the fault code. Choose 'Check Engine Light' when booking.",
                "check engine", "engine light", "warning light", "dashboard light", "obd", "mil");
        kb("Better fuel economy: keep tyres at the right pressure, drive smoothly (avoid hard acceleration/braking), remove unused roof racks and heavy cargo, don't idle for long, and keep up with air-filter and oil changes. Cruising at a steady 60-80 km/h is usually the sweet spot.",
                "fuel economy", "mileage", "kmpl", "save fuel", "efficiency", "petrol", "diesel");
        kb("EV tips: charge between 20-80% for daily use to preserve the battery, avoid frequent 100% fast-charging, keep tyres inflated (it affects range) and precondition the cabin while plugged in. EVs have no engine oil, so we show 'Motor & drivetrain' instead.",
                "electric", "charging", " ev ", "range", "hybrid");
        kb("Monsoon care: replace worn wiper blades, check tyre tread (hydroplaning risk), test brakes after driving through water, clean the AC drain, keep headlights/fog lamps working, and avoid deep waterlogged roads - never restart an engine that stalled in water. Rinse the underbody to prevent rust.",
                "monsoon", "rain", "flood", "waterlog", "wiper");
        kb("Summer care: check coolant level, test the AC, keep tyre pressure right (heat raises it), and use a sunshade. Winter care: test the battery and wipe condensation from the interior.",
                "summer", "winter", "season", "heat", "cold");
        kb("To add a vehicle: open 'My Garage' in the left menu -> click '+ Add vehicle', fill in the details and the condition sliders (you'll see the 3D car update live), then press 'Save vehicle'. Your Dashboard will immediately show its health score.",
                "add vehicle", "add a car", "add car", "new vehicle", "register vehicle", "add my car");
        kb("To book a service: choose 'Book Service', pick the issue and a service advisor, confirm the odometer reading and press 'Run AI diagnosis'. You get a detailed diagnostic report and a GST invoice instantly, saved to History. Tick 'Mark as serviced today' if the work is done so your next-due date resets.",
                "book", "appointment", "schedule", "diagnos");
        kb("Invoices are generated with every diagnosis (diagnostic fee + parts estimate + 18% GST). Find them in History - you can save an invoice as a .txt file or print it from there.",
                "invoice", "bill", "gst", "print", "download", "receipt");
        kb("Your estimates (including 18% GST): " + estimates() + "\nThese are indicative workshop estimates; the final bill depends on actual parts needed.",
                "cost", "price", "estimate", "how much", "charges", "fee", "expensive");
        kb("Loyalty points: you earn 1 point for every INR 100 spent on a service. Tiers: Bronze (0), Silver (100), Gold (300), Platinum (600). Your tier and progress are shown on the Dashboard.",
                "points", "loyalty", "reward", "tier", "badge", "platinum", "gold");
        kb("The Dashboard's 3D car is interactive: drag to rotate, hover any part to see its condition, and switch on 'Health X-ray' to colour each part green/amber/red by condition.",
                "3d", "rotate", "x-ray", "xray", "model");
        kb("Use the moon/sun button in the top bar to switch between light and dark themes - the app remembers your choice while it is open.",
                "dark mode", "theme", "light mode", "dark");
        kb("Your account, vehicles, service dates and invoices are stored in your MySQL database. Passwords are never stored in plain text - they are salted and hashed. The footer on the sign-in page shows the connection status.",
                "database", "mysql", "privacy", "my data", "secure", "safe", "where is");
        kb("Password reset isn't available yet in this desktop version. If you forgot your password, create a new account (or ask your administrator to reset the database entry).",
                "forgot password", "reset password", "password");
        kb("I can help with: your car's health & upcoming dates, explaining warning signs, maintenance tips, repair cost estimates, how to use any part of AutoCare AI, and seasonal care. Just ask in plain words!",
                "what can you do", "who are you", "help", "features");
    }
    private static String estimates() {
        StringBuilder sb = new StringBuilder();
        for (String i : Health.ISSUES) sb.append("\n  - ").append(i).append(": INR ").append(Fmt.money(Health.estimateTotal(i)));
        return sb.toString();
    }

    String offline(String raw) {
        String q = " " + raw.toLowerCase(Locale.ROOT).trim() + " ";
        Vehicle v = app.active;
        String name = app.user == null ? "there" : app.user.username;

        if (q.matches("(?s).*\\b(hi|hello|hey|namaste|good morning|good evening)\\b.*") && q.length() < 30)
            return "Hi " + name + "! \uD83D\uDC4B I'm your AutoCare assistant. Ask me about your car's health, service dates, warning lights, costs - or how to use the app.";
        if (q.matches("(?s).*\\b(thanks|thank you|thx)\\b.*")) return "You're welcome! Drive safe. \uD83D\uDE97";
        if (q.matches("(?s).*\\b(bye|goodbye)\\b.*")) return "Goodbye! Come back any time your car needs a check-up.";

        boolean mine = q.contains(" my ") || q.contains("when") || q.contains("status") || q.contains("how is") || q.contains("how healthy") || q.contains("condition");
        if (mine && (q.contains("next service") || q.contains("service due") || q.contains("service date") || q.contains("when is my service"))) {
            if (v == null) return noVehicle();
            long d = Health.days(Health.nextServiceDue(v));
            if (d == Health.NONE) return "I don't have a last-service date for " + v.displayName() + ". Add it in My Garage and I'll calculate the next due date (6 months later).";
            return v.displayName() + "'s next service is due on " + Fmt.date(Health.nextServiceDue(v)) + " (" + Health.relative(d) + ")." + (d < 0 ? " It's overdue - I'd book it soon!" : "");
        }
        if (mine && q.contains("puc")) {
            if (v == null) return noVehicle();
            return v.pucExpiry == null ? "No PUC date saved for " + v.displayName() + ". Add it in My Garage to get reminders."
                    : v.displayName() + "'s PUC expires on " + Fmt.date(v.pucExpiry) + " (" + Health.relative(Health.days(v.pucExpiry)) + ").";
        }
        if (mine && q.contains("insurance")) {
            if (v == null) return noVehicle();
            return v.insuranceExpiry == null ? "No insurance date saved for " + v.displayName() + ". Add it in My Garage to get reminders."
                    : v.displayName() + "'s insurance expires on " + Fmt.date(v.insuranceExpiry) + " (" + Health.relative(Health.days(v.insuranceExpiry)) + ").";
        }
        boolean statusQ = (q.contains("health") || q.contains("status") || q.contains("condition") || q.contains("how is my") || q.contains("my car") || q.contains("my vehicle"))
                && !q.contains("calculat") && !q.contains("formula") && !q.contains("score work");
        if (statusQ && (q.contains("my ") || q.contains("how healthy") || q.contains("status"))) {
            if (v == null) return noVehicle();
            int s = Health.score(v);
            StringBuilder sb = new StringBuilder(v.displayName() + " scores " + s + "/100 - " + Health.status(s) + ".\n");
            List<Health.Alert> al = Health.alerts(v);
            if (al.isEmpty()) sb.append("No active alerts - nicely done!");
            else { sb.append("Things to look at:"); for (int i = 0; i < Math.min(4, al.size()); i++) sb.append("\n- ").append(al.get(i).text); }
            return sb.toString();
        }

        Entry best = null; int bestScore = 0;
        for (Entry e : KB) {
            int sc = 0;
            for (String k : e.keys) if (q.contains(k.length() <= 3 ? " " + k.trim() + " " : k)) sc += k.length();
            if (sc > bestScore) { bestScore = sc; best = e; }
        }
        if (best != null) return best.answer;
        return "I'm not sure I understood that. Try asking things like:\n- \"How healthy is my car?\"\n- \"Why are my brakes squealing?\"\n- \"How much does a brake service cost?\"\n- \"How do I add a vehicle?\"\n\nFor open-ended questions, add an Anthropic API key (see README) and I'll answer anything.";
    }
    private String noVehicle() { return "You haven't added a vehicle yet. Open 'My Garage' -> '+ Add vehicle' and I'll be able to track its health, service dates and alerts."; }
}
