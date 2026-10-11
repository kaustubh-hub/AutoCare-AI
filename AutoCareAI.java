package ai;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AutoCare AI - vehicle service manager (redesigned).
 * Shell: sign-in screen, then sidebar + header + pages + docked AI assistant.
 */
public class AutoCareAI extends JFrame {
    static volatile Store store;
    static volatile String storeStatus = "Connecting to database...";
    private static AutoCareAI instance;

    // session
    User user;
    List<Vehicle> vehicles = new ArrayList<>();
    List<Report> reports = new ArrayList<>();
    Vehicle active;

    private final CardLayout rootCards = new CardLayout();
    private final JPanel root = new JPanel(rootCards);
    private LoginPage loginPage;
    private final Map<String, JComponent> pages = new LinkedHashMap<>();
    private final Map<String, UI.Btn> navButtons = new LinkedHashMap<>();
    private CardLayout pageCards;
    private JPanel pageHost, chatHost;
    private JLabel title, subtitle, toastLabel;
    private JComboBox<String> vehicleCombo;
    private boolean updatingCombo;
    private String currentPage = "dashboard";
    private boolean chatOpen;
    private ChatPanel chat;
    private final List<String[]> chatLog = new ArrayList<>();
    private ChatEngine engine;
    private javax.swing.Timer toastTimer;

    public AutoCareAI() {
        setTitle("AutoCare AI - Vehicle Service Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1380, 880);
        setMinimumSize(new Dimension(1120, 720));
        setLocationRelativeTo(null);
        setContentPane(root);
        instance = this;
        showLogin();
    }

    // ------------------------------------------------------------------ startup
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new AutoCareAI().setVisible(true));
        startStoreInit();
    }

    static volatile String storeError;

    static void startStoreInit() {
        storeStatus = "Connecting to MySQL...";
        storeError = null;
        Thread t = new Thread(AutoCareAI::initStore, "store-init");
        t.setDaemon(true);
        t.start();
    }

    private static void initStore() {
        boolean ok;
        try {
            store = new MySqlStore();
            storeStatus = "\u25CF Connected to MySQL  (" + Config.get("db.host") + ":" + Config.get("db.port") + " / " + Config.get("db.name") + ")";
            storeError = null;
            ok = true;
        } catch (StoreException e) {
            store = null;
            storeError = e.getMessage();
            storeStatus = "\u25CF MySQL is not connected";
            System.err.println("MySQL connection failed: " + (e.getCause() != null ? e.getCause() : e));
            ok = false;
        }
        final boolean good = ok;
        SwingUtilities.invokeLater(() -> { if (instance != null && instance.loginPage != null) instance.loginPage.setStatus(storeStatus, good ? null : storeError); });
    }

    // ------------------------------------------------------------------ auth
    private boolean storeReady(LoginPage lp) {
        if (store == null) {
            lp.error(storeError != null ? "Database not connected: " + storeError : "Still connecting to MySQL - please try again in a moment.");
            return false;
        }
        return true;
    }

    void login(LoginPage lp, String username, String password) {
        if (!storeReady(lp)) return;
        try {
            User u = store.login(username, password);
            if (u == null) { lp.error("Incorrect username or password."); return; }
            start(u);
        } catch (StoreException e) { lp.error(e.getMessage()); }
    }

    void register(LoginPage lp, String username, String phone, String password) {
        if (!storeReady(lp)) return;
        try { start(store.register(username, phone, password)); }
        catch (StoreException e) { lp.error(e.getMessage()); }
    }

    void demoLogin(LoginPage lp) {
        if (!storeReady(lp)) return;
        try {
            User u = store.login("demo", "demo123");
            if (u == null) { u = store.register("demo", "9876543210", "demo123"); seedDemo(u); }
            start(u);
        } catch (StoreException e) { lp.error(e.getMessage()); }
    }

    private void seedDemo(User u) throws StoreException {
        LocalDate today = LocalDate.now();
        Vehicle a = new Vehicle();
        a.userId = u.id; a.nickname = "Family SUV"; a.make = "Hyundai"; a.model = "Creta"; a.year = 2021; a.registration = "MH 01 AB 1234";
        a.vin = "MALC281CMNM123456"; a.fuelType = "Diesel (CRDi)"; a.mileage = 46500; a.colorRgb = 0x3B82F6;
        a.pucExpiry = today.plusDays(12); a.insuranceExpiry = today.plusDays(160); a.lastService = today.minusDays(196);
        a.serviceNotes = "Oil + filter change"; a.oil = 48; a.brakes = 62; a.tyres = 70; a.battery = 78; a.coolant = 85;
        a = store.saveVehicle(a);
        Vehicle b = new Vehicle();
        b.userId = u.id; b.nickname = "City EV"; b.make = "Tata"; b.model = "Nexon EV"; b.year = 2023; b.registration = "MH 02 CD 5678";
        b.vin = "MAT625100PXA00123"; b.fuelType = "Electric"; b.mileage = 18200; b.colorRgb = 0x10B981;
        b.insuranceExpiry = today.plusDays(300); b.lastService = today.minusDays(90);
        b.oil = 90; b.brakes = 85; b.tyres = 80; b.battery = 88; b.coolant = 92;
        store.saveVehicle(b);
        store.saveReport(Health.makeReport(u, a, "Routine Maintenance", Health.ADVISORS[0], today.minusDays(200)));
        store.saveReport(Health.makeReport(u, a, "Brake Squealing", Health.ADVISORS[2], today.minusDays(120)));
        store.saveReport(Health.makeReport(u, a, "AC Not Cooling", Health.ADVISORS[1], today.minusDays(45)));
    }

    private void start(User u) throws StoreException {
        user = u;
        active = null;
        chatLog.clear();
        engine = new ChatEngine(this);
        reloadData();
        buildApp();
        rootCards.show(root, "app");
        showPage("dashboard");
    }

    void logout() {
        user = null; vehicles = new ArrayList<>(); reports = new ArrayList<>(); active = null; chatOpen = false;
        showLogin();
    }

    private void showLogin() {
        loginPage = new LoginPage(this);
        root.removeAll();
        root.add(loginPage, "login");
        rootCards.show(root, "login");
        root.revalidate(); root.repaint();
    }

    // ------------------------------------------------------------------ data
    void reloadData() {
        try {
            vehicles = store.vehicles(user.id);
            reports = store.reports(user.id);
        } catch (StoreException e) { JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
        int keep = active == null ? -1 : active.id;
        active = null;
        for (Vehicle v : vehicles) if (v.id == keep) active = v;
        if (active == null && !vehicles.isEmpty()) active = vehicles.get(0);
        syncCombo();
    }

    void setActive(int vehicleId) {
        for (Vehicle v : vehicles) if (v.id == vehicleId) active = v;
        syncCombo();
    }

    // ------------------------------------------------------------------ shell
    private void buildApp() {
        JPanel app = new JPanel(new BorderLayout());
        app.setBackground(Theme.bg);
        app.add(sidebar(), BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.bg);
        center.add(header(), BorderLayout.NORTH);

        pageCards = new CardLayout();
        pageHost = new JPanel(pageCards);
        pageHost.setBackground(Theme.bg);
        pages.clear();
        pages.put("dashboard", new DashboardPage(this));
        pages.put("garage", new GaragePage(this));
        pages.put("service", new ServicePage(this));
        pages.put("history", new HistoryPage(this));
        for (Map.Entry<String, JComponent> e : pages.entrySet()) pageHost.add(e.getValue(), e.getKey());
        center.add(pageHost, BorderLayout.CENTER);

        chatHost = new JPanel(new BorderLayout());
        chatHost.setOpaque(false);
        app.add(center, BorderLayout.CENTER);
        app.add(chatHost, BorderLayout.EAST);

        root.add(app, "app");
        syncCombo();
        if (chatOpen) { chatOpen = false; toggleChat(true); }
    }

    private JComponent sidebar() {
        JPanel s = new JPanel();
        s.setBackground(Theme.sidebar);
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        s.setPreferredSize(new Dimension(230, 0));
        s.setBorder(new EmptyBorder(24, 16, 20, 16));
        JLabel logo = UI.label("\uD83D\uDE97 AutoCare AI", Font.BOLD, 20, Color.WHITE);
        logo.setAlignmentX(LEFT_ALIGNMENT); logo.setBorder(new EmptyBorder(0, 8, 2, 0));
        JLabel tag = UI.label("Vehicle service manager", Font.PLAIN, 11, new Color(130, 146, 180));
        tag.setAlignmentX(LEFT_ALIGNMENT); tag.setBorder(new EmptyBorder(0, 10, 24, 0));
        s.add(logo); s.add(tag);

        navButtons.clear();
        nav(s, "dashboard", "\u25A6   Dashboard");
        nav(s, "garage", "\u2699   My Garage");
        nav(s, "service", "\u26A1   Book Service");
        nav(s, "history", "\u2630   History");
        s.add(Box.createVerticalStrut(14));
        UI.Btn ai = new UI.Btn("\u2728   AI Assistant", UI.Kind.PRIMARY);
        ai.setAlignmentX(LEFT_ALIGNMENT); ai.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        ai.setHorizontalAlignment(SwingConstants.LEFT);
        ai.addActionListener(e -> toggleChat(!chatOpen));
        s.add(ai);
        s.add(Box.createVerticalGlue());

        JPanel me = new JPanel(new BorderLayout(10, 0)); me.setOpaque(false); me.setAlignmentX(LEFT_ALIGNMENT);
        me.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        JLabel avatar = new JLabel(user.username.substring(0, 1).toUpperCase(), SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create(); UI.aa(g2);
                g2.setPaint(new GradientPaint(0, 0, Theme.primary, getWidth(), getHeight(), Theme.accent));
                g2.fillOval(0, 0, getWidth(), getHeight()); g2.dispose(); super.paintComponent(g);
            }
        };
        avatar.setPreferredSize(new Dimension(38, 38)); avatar.setForeground(Color.WHITE); avatar.setFont(Theme.font(Font.BOLD, 16));
        me.add(avatar, BorderLayout.WEST);
        JPanel who = new JPanel(); who.setOpaque(false); who.setLayout(new BoxLayout(who, BoxLayout.Y_AXIS));
        who.add(UI.label(user.username, Font.BOLD, 13, Color.WHITE));
        who.add(UI.label(Health.tier(Health.points(reports)) + " member", Font.PLAIN, 11, new Color(130, 146, 180)));
        me.add(who, BorderLayout.CENTER);
        s.add(me);
        s.add(Box.createVerticalStrut(12));
        UI.Btn out = new UI.Btn("Sign out", UI.Kind.GHOST);
        out.setAlignmentX(LEFT_ALIGNMENT); out.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        out.addActionListener(e -> logout());
        s.add(out);
        return s;
    }

    private void nav(JPanel s, String key, String text) {
        UI.Btn b = new UI.Btn(text, UI.Kind.GHOST) {
            @Override protected void paintComponent(Graphics g) {
                boolean on = key.equals(currentPage);
                Graphics2D g2 = (Graphics2D) g.create(); UI.aa(g2);
                if (on) { g2.setColor(new Color(59, 130, 246, 55)); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12); g2.setColor(Theme.primary); g2.fillRoundRect(0, 8, 4, getHeight() - 16, 4, 4); }
                else if (getModel().isRollover()) { g2.setColor(new Color(255, 255, 255, 18)); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12); }
                g2.dispose();
                setForeground(on ? Color.WHITE : new Color(160, 174, 205));
                paintLabelOnly(g);
            }
            private void paintLabelOnly(Graphics g) { getUI().paint(g, this); }
        };
        b.setContentAreaFilled(false);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setBorder(new EmptyBorder(11, 16, 11, 10));
        b.addActionListener(e -> showPage(key));
        navButtons.put(key, b);
        s.add(b);
        s.add(Box.createVerticalStrut(4));
    }

    private JComponent header() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(Theme.card);
        h.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border), new EmptyBorder(14, 28, 14, 24)));
        JPanel t = new JPanel(); t.setOpaque(false); t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        title = UI.label("Dashboard", Font.BOLD, 20, Theme.text);
        subtitle = UI.label(" ", Font.PLAIN, 12, Theme.dim);
        t.add(title); t.add(subtitle);
        h.add(t, BorderLayout.WEST);

        toastLabel = UI.label(" ", Font.BOLD, 13, Theme.success);
        toastLabel.setHorizontalAlignment(SwingConstants.CENTER);
        h.add(toastLabel, BorderLayout.CENTER);

        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); r.setOpaque(false);
        r.add(UI.label("Vehicle", Font.BOLD, 12, Theme.dim));
        vehicleCombo = UI.combo(new String[]{"No vehicles yet"});
        vehicleCombo.setPreferredSize(new Dimension(220, 38));
        vehicleCombo.addActionListener(e -> {
            if (updatingCombo) return;
            int i = vehicleCombo.getSelectedIndex();
            if (i >= 0 && i < vehicles.size()) { active = vehicles.get(i); refreshPages(); }
        });
        r.add(vehicleCombo);
        UI.Btn theme = new UI.Btn(Theme.dark ? "\u2600  Light" : "\u263E  Dark", UI.Kind.GHOST);
        theme.addActionListener(e -> { Theme.apply(!Theme.dark); rebuild(); });
        r.add(theme);
        h.add(r, BorderLayout.EAST);
        return h;
    }

    private void rebuild() {
        if (user == null) { showLogin(); return; }
        String keep = currentPage;
        root.removeAll();
        buildApp();
        rootCards.show(root, "app");
        showPage(keep);
        root.revalidate(); root.repaint();
    }

    private void syncCombo() {
        if (vehicleCombo == null) return;
        updatingCombo = true;
        vehicleCombo.removeAllItems();
        if (vehicles.isEmpty()) vehicleCombo.addItem("No vehicles yet");
        for (Vehicle v : vehicles) vehicleCombo.addItem(v.displayName());
        if (active != null) for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).id == active.id) vehicleCombo.setSelectedIndex(i);
        vehicleCombo.setEnabled(!vehicles.isEmpty());
        updatingCombo = false;
    }

    // ------------------------------------------------------------------ navigation / helpers
    void showPage(String key) {
        currentPage = key;
        pageCards.show(pageHost, key);
        String[][] meta = {{"dashboard", "Dashboard", "Live health of your vehicle"}, {"garage", "My Garage", "Add and update your vehicles"},
                {"service", "Book Service", "AI diagnosis, estimate and invoice"}, {"history", "History", "Past diagnoses and invoices"}};
        for (String[] m : meta) if (m[0].equals(key)) { title.setText(m[1]); subtitle.setText(m[2]); }
        refreshPages();
        for (UI.Btn b : navButtons.values()) b.repaint();
    }

    void refreshPages() {
        JComponent p = pages.get(currentPage);
        if (p instanceof Page) ((Page) p).refresh();
    }

    void toggleChat(boolean open) {
        boolean was = chatOpen;
        chatOpen = open;
        if (open != was && (getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) {
            int max = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width;
            int target = Math.max(getMinimumSize().width, Math.min(max, getWidth() + (open ? 390 : -390)));
            setSize(target, getHeight());
        }
        chatHost.removeAll();
        if (open) {
            chat = new ChatPanel(this, engine, chatLog);
            chatHost.add(chat, BorderLayout.CENTER);
        }
        chatHost.revalidate(); chatHost.repaint();
        if (open) SwingUtilities.invokeLater(() -> chat.focusInput());
    }

    void toast(String text) {
        if (toastLabel == null) return;
        toastLabel.setText(text);
        if (toastTimer != null) toastTimer.stop();
        toastTimer = new javax.swing.Timer(3500, e -> toastLabel.setText(" "));
        toastTimer.setRepeats(false);
        toastTimer.start();
    }
}
