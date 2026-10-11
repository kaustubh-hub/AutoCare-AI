package ai;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

/** Home screen: 3D car, health gauge, component bars, key dates, alerts, spending and loyalty. */
class DashboardPage extends JPanel implements Page {
    private static final String[] TIPS = {
            "Check tyre pressure once a month - correct pressure saves fuel and extends tyre life.",
            "Let the engine idle 20-30 seconds in cold weather; avoid revving a cold engine.",
            "Replace wiper blades every 6-12 months, especially before the monsoon.",
            "A dirty air filter can reduce mileage. Replace it every 10,000-15,000 km.",
            "Keep at least a quarter tank of fuel - it protects the fuel pump.",
            "Brake fluid absorbs moisture. Replace it every 2 years.",
            "Run the AC for 10 minutes weekly, even in cooler months, to keep seals healthy.",
            "Rotate your tyres every 8,000-10,000 km for even wear.",
            "Wash the underbody after driving through salt or flood water to prevent rust.",
            "Smooth acceleration and braking can improve fuel economy by up to 20%.",
            "Don't ignore a flashing check-engine light - slow down and get it scanned.",
            "Park in the shade or use a sunshade - heat ages dashboards and batteries.",
            "Check coolant level only when the engine is cold.",
            "Keep your PUC, insurance and RC documents in one place - or right here in AutoCare AI."};

    private final AutoCareAI app;

    DashboardPage(AutoCareAI app) { this.app = app; setLayout(new BorderLayout()); setBackground(Theme.bg); }

    @Override public void refresh() {
        removeAll();
        UI.ScrollPanel content = new UI.ScrollPanel();
        content.setLayout(new GridBagLayout());
        content.setBorder(new EmptyBorder(22, 28, 28, 28));
        Vehicle v = app.active;
        int row = 0;

        content.add(greeting(), cons(row++, 0, 1, new Insets(0, 0, 16, 0)));

        if (v == null) {
            content.add(emptyState(), cons(row++, 0, 1, new Insets(0, 0, 0, 0)));
            addFiller(content, row);
            add(UI.scroll(content), BorderLayout.CENTER);
            revalidate(); repaint();
            return;
        }

        // Row 1: 3D car (left) + health gauge & bars (right)
        JPanel top = new JPanel(new GridBagLayout()); top.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH; g.weighty = 1; g.gridy = 0;
        g.gridx = 0; g.weightx = 0.58; g.insets = new Insets(0, 0, 0, 8);
        JComponent cc = carCard(v); cc.setMinimumSize(new Dimension(300, 440)); top.add(cc, g);
        g.gridx = 1; g.weightx = 0.42; g.insets = new Insets(0, 8, 0, 0);
        JComponent hc = healthCard(v); hc.setMinimumSize(new Dimension(260, 440)); hc.setPreferredSize(new Dimension(300, 440)); top.add(hc, g);
        content.add(top, cons(row++, 0, 1, new Insets(0, 0, 16, 0)));

        // Row 2: key date stats
        JPanel stats = new JPanel(new GridLayout(1, 4, 16, 0)); stats.setOpaque(false);
        int toOil = 10000 - (v.mileage % 10000);
        stats.add(stat("ODOMETER", Fmt.km(v.mileage), "Oil service in ~" + Fmt.km(toOil), Theme.primary));
        stats.add(dateStat("NEXT SERVICE", Health.nextServiceDue(v), true));
        stats.add(dateStat("PUC CERTIFICATE", v.pucExpiry, false));
        stats.add(dateStat("INSURANCE", v.insuranceExpiry, false));
        content.add(stats, cons(row++, 0, 1, new Insets(0, 0, 16, 0)));

        // Row 3: alerts | spending | loyalty + tip
        JPanel bottom = new JPanel(new GridLayout(1, 3, 16, 0)); bottom.setOpaque(false);
        bottom.add(alertsCard(v));
        bottom.add(spendCard());
        bottom.add(loyaltyCard());
        content.add(bottom, cons(row++, 0, 1, new Insets(0, 0, 0, 0)));

        addFiller(content, row);
        JScrollPane sp = UI.scroll(content);
        add(sp, BorderLayout.CENTER);
        revalidate(); repaint();
        SwingUtilities.invokeLater(() -> sp.getVerticalScrollBar().setValue(0));
    }

    // ------------------------------------------------------------------ pieces
    private JPanel greeting() {
        JPanel p = new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        int h = java.time.LocalTime.now().getHour();
        String hello = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        JLabel a = UI.label(hello + ", " + app.user.username + " \uD83D\uDC4B", Font.BOLD, 26, Theme.text);
        a.setAlignmentX(LEFT_ALIGNMENT);
        JLabel b = UI.label(app.active == null ? "Let's add your first vehicle to get started."
                : "Here's how " + app.active.displayName() + " is doing today.", Font.PLAIN, 14, Theme.dim);
        b.setAlignmentX(LEFT_ALIGNMENT);
        p.add(a); p.add(Box.createVerticalStrut(2)); p.add(b);
        return p;
    }

    private JComponent emptyState() {
        UI.Card c = new UI.Card();
        c.setLayout(new BorderLayout(20, 0));
        Car3D car = new Car3D();
        car.setState(0x94A3B8, 90, 90, 90, 90, 90, false);
        car.setPreferredSize(new Dimension(460, 340));
        c.add(car, BorderLayout.CENTER);
        JPanel r = new JPanel(); r.setOpaque(false); r.setLayout(new BoxLayout(r, BoxLayout.Y_AXIS)); r.setBorder(new EmptyBorder(30, 20, 30, 30));
        r.setPreferredSize(new Dimension(340, 300));
        r.add(UI.label("Your garage is empty", Font.BOLD, 22, Theme.text));
        r.add(Box.createVerticalStrut(8));
        JTextArea t = new JTextArea("Add your car to see its live health score, 3D model, service reminders and personalised tips.");
        t.setEditable(false); t.setOpaque(false); t.setLineWrap(true); t.setWrapStyleWord(true); t.setFont(Theme.font(Font.PLAIN, 14)); t.setForeground(Theme.dim);
        t.setAlignmentX(LEFT_ALIGNMENT); t.setMaximumSize(new Dimension(320, 90));
        r.add(t);
        r.add(Box.createVerticalStrut(18));
        UI.Btn add = new UI.Btn("+  Add my first vehicle", UI.Kind.PRIMARY);
        add.setAlignmentX(LEFT_ALIGNMENT);
        add.addActionListener(e -> app.showPage("garage"));
        r.add(add);
        c.add(r, BorderLayout.EAST);
        return c;
    }

    private JComponent carCard(Vehicle v) {
        UI.Card c = new UI.Card();
        c.setLayout(new BorderLayout(0, 10));
        c.setBorder(new EmptyBorder(16, 18, 14, 18));
        JPanel head = new JPanel(new BorderLayout()); head.setOpaque(false);
        JPanel t = new JPanel(); t.setOpaque(false); t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(UI.label(v.displayName(), Font.BOLD, 18, Theme.text));
        t.add(UI.label(((v.year > 0 ? v.year + "  |  " : "") + v.make + " " + v.model + "  |  " + v.registration + "  |  " + v.fuelType).replace("  |    |  ", "  |  "), Font.PLAIN, 12, Theme.dim));
        head.add(t, BorderLayout.CENTER);
        Car3D car = new Car3D();
        car.setVehicle(v);
        JCheckBox x = new JCheckBox("Health X-ray");
        x.setOpaque(false); x.setForeground(Theme.text); x.setFont(Theme.font(Font.BOLD, 12)); x.setFocusPainted(false);
        x.setToolTipText("Colour each part by its condition: green = good, amber = watch, red = needs attention");
        x.addActionListener(e -> car.setXray(x.isSelected()));
        head.add(x, BorderLayout.EAST);
        c.add(head, BorderLayout.NORTH);
        car.setPreferredSize(new Dimension(440, 330));
        c.add(car, BorderLayout.CENTER);
        return c;
    }

    private JComponent healthCard(Vehicle v) {
        UI.Card c = new UI.Card();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        int score = Health.score(v);
        JLabel t = UI.label("Vehicle health", Font.BOLD, 16, Theme.text); t.setAlignmentX(LEFT_ALIGNMENT);
        c.add(t);
        UI.Gauge gauge = new UI.Gauge(); gauge.setAlignmentX(LEFT_ALIGNMENT);
        gauge.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        gauge.setValue(score);
        c.add(gauge);
        JPanel pr = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)); pr.setOpaque(false); pr.setAlignmentX(LEFT_ALIGNMENT);
        pr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        pr.add(new UI.Pill(Health.status(score).toUpperCase(), Health.color(score)));
        c.add(pr);
        c.add(Box.createVerticalStrut(14));
        UI.MetricBars bars = new UI.MetricBars(); bars.setAlignmentX(LEFT_ALIGNMENT);
        bars.set(Health.metricNames(v), Health.metrics(v));
        bars.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36 * 5 + 4));
        c.add(bars);
        c.add(Box.createVerticalGlue());
        return c;
    }

    private JComponent stat(String title, String value, String sub, Color tint) {
        UI.Card c = new UI.Card();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new EmptyBorder(16, 18, 16, 18));
        JLabel a = UI.label(title, Font.BOLD, 11, Theme.dim);
        JLabel b = UI.label(value, Font.BOLD, 22, Theme.text);
        JLabel d = UI.label(sub, Font.BOLD, 12, tint);
        a.setAlignmentX(LEFT_ALIGNMENT); b.setAlignmentX(LEFT_ALIGNMENT); d.setAlignmentX(LEFT_ALIGNMENT);
        c.add(a); c.add(Box.createVerticalStrut(6)); c.add(b); c.add(Box.createVerticalStrut(4)); c.add(d);
        return c;
    }

    private JComponent dateStat(String title, LocalDate date, boolean service) {
        long d = Health.days(date);
        Color tint = d == Health.NONE ? Theme.dim : d < 0 ? Theme.danger : d <= 30 ? Theme.warn : Theme.success;
        String sub = d == Health.NONE ? (service ? "Add last service date" : "Add in My Garage") : Health.relative(d);
        return stat(title, date == null ? "Not set" : Fmt.date(date), sub, tint);
    }

    private JComponent alertsCard(Vehicle v) {
        UI.Card c = new UI.Card();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        JLabel t = UI.label("Alerts & reminders", Font.BOLD, 16, Theme.text); t.setAlignmentX(LEFT_ALIGNMENT);
        c.add(t); c.add(Box.createVerticalStrut(10));
        List<Health.Alert> al = Health.alerts(v);
        if (al.isEmpty()) {
            JLabel ok = UI.label("\u2705  All clear - nothing needs attention.", Font.PLAIN, 13, Theme.success); ok.setAlignmentX(LEFT_ALIGNMENT);
            c.add(ok);
        }
        for (int i = 0; i < Math.min(5, al.size()); i++) {
            Health.Alert a = al.get(i);
            Color col = a.level == 2 ? Theme.danger : a.level == 1 ? Theme.warn : Theme.primary;
            JPanel r = new JPanel(new BorderLayout(10, 0)); r.setOpaque(false); r.setAlignmentX(LEFT_ALIGNMENT);
            JLabel dot = UI.label("\u25CF", Font.PLAIN, 14, col);
            dot.setVerticalAlignment(SwingConstants.TOP);
            r.add(dot, BorderLayout.WEST);
            JTextArea ta = new JTextArea(a.text);
            ta.setEditable(false); ta.setOpaque(false); ta.setLineWrap(true); ta.setWrapStyleWord(true);
            ta.setFont(Theme.font(Font.PLAIN, 12)); ta.setForeground(Theme.text); ta.setBorder(null);
            r.add(ta, BorderLayout.CENTER);
            r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
            c.add(r); c.add(Box.createVerticalStrut(8));
        }
        c.add(Box.createVerticalGlue());
        UI.Btn book = new UI.Btn("Book a service", UI.Kind.PRIMARY); book.setAlignmentX(LEFT_ALIGNMENT);
        book.addActionListener(e -> app.showPage("service"));
        c.add(book);
        c.setPreferredSize(new Dimension(300, 300));
        return c;
    }

    private JComponent spendCard() {
        UI.Card c = new UI.Card();
        c.setLayout(new BorderLayout(0, 10));
        c.add(UI.label("Service spending (INR)", Font.BOLD, 16, Theme.text), BorderLayout.NORTH);
        UI.SpendChart ch = new UI.SpendChart();
        ch.set(app.reports);
        c.add(ch, BorderLayout.CENTER);
        double total = 0; for (Report r : app.reports) total += r.total;
        c.add(UI.label("Total spent: INR " + Fmt.money(total) + "  |  " + app.reports.size() + (app.reports.size() == 1 ? " visit" : " visits"), Font.PLAIN, 12, Theme.dim), BorderLayout.SOUTH);
        c.setPreferredSize(new Dimension(300, 300));
        return c;
    }

    private JComponent loyaltyCard() {
        UI.Card c = new UI.Card();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        int pts = Health.points(app.reports);
        String tier = Health.tier(pts);
        int next = Health.nextTierAt(pts);
        JLabel t = UI.label("Loyalty rewards", Font.BOLD, 16, Theme.text); t.setAlignmentX(LEFT_ALIGNMENT);
        c.add(t); c.add(Box.createVerticalStrut(10));
        JPanel pr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); pr.setOpaque(false); pr.setAlignmentX(LEFT_ALIGNMENT);
        pr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        pr.add(UI.label(pts + " pts  ", Font.BOLD, 22, Theme.text));
        pr.add(new UI.Pill(tier.toUpperCase(), Theme.accent));
        c.add(pr); c.add(Box.createVerticalStrut(8));
        UI.Progress p = new UI.Progress(); p.setAlignmentX(LEFT_ALIGNMENT);
        p.set(pts >= 600 ? 1 : (double) pts / next, Theme.accent);
        c.add(p); c.add(Box.createVerticalStrut(6));
        JLabel hint = UI.label(pts >= 600 ? "You've reached the top tier!" : (next - pts) + " pts to the next tier  |  1 pt per INR 100", Font.PLAIN, 12, Theme.dim);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        c.add(hint); c.add(Box.createVerticalStrut(16));
        JLabel tipHead = UI.label("\uD83D\uDCA1  Tip of the day", Font.BOLD, 13, Theme.text); tipHead.setAlignmentX(LEFT_ALIGNMENT);
        c.add(tipHead); c.add(Box.createVerticalStrut(4));
        JTextArea tip = new JTextArea(TIPS[LocalDate.now().getDayOfYear() % TIPS.length]);
        tip.setEditable(false); tip.setOpaque(false); tip.setLineWrap(true); tip.setWrapStyleWord(true);
        tip.setFont(Theme.font(Font.PLAIN, 12)); tip.setForeground(Theme.dim); tip.setAlignmentX(LEFT_ALIGNMENT);
        tip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        c.add(tip);
        c.add(Box.createVerticalGlue());
        UI.Btn ask = new UI.Btn("Ask the AI assistant", UI.Kind.GHOST); ask.setAlignmentX(LEFT_ALIGNMENT);
        ask.addActionListener(e -> app.toggleChat(true));
        c.add(ask);
        c.setPreferredSize(new Dimension(300, 300));
        return c;
    }

    private static GridBagConstraints cons(int y, int x, int w, Insets in) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x; g.gridy = y; g.gridwidth = w; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTH; g.insets = in;
        return g;
    }
    private static void addFiller(JPanel p, int row) {
        GridBagConstraints g = new GridBagConstraints(); g.gridy = row; g.weighty = 1; g.fill = GridBagConstraints.VERTICAL;
        p.add(Box.createVerticalGlue(), g);
    }
}
