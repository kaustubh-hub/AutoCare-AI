package ai;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Split-screen sign in / registration with a spinning 3D car. */
class LoginPage extends JPanel {
    private final AutoCareAI app;
    private final CardLayout forms = new CardLayout();
    private final JPanel formHost = new JPanel(forms);
    private final JLabel msg = UI.label(" ", Font.PLAIN, 12, Theme.danger);
    private final JLabel storeLabel = UI.label(AutoCareAI.storeStatus, Font.PLAIN, 11, Theme.dim);
    private final UI.Btn tabIn = new UI.Btn("Sign in", UI.Kind.PRIMARY), tabUp = new UI.Btn("Create account", UI.Kind.GHOST);
    private final UI.Field userIn = new UI.Field("Username"), userUp = new UI.Field("Choose a username"), phoneUp = new UI.Field("Phone number");
    private final UI.Pass passIn = new UI.Pass("Password"), passUp = new UI.Pass("At least 6 characters"), pass2Up = new UI.Pass("Repeat password");

    LoginPage(AutoCareAI app) {
        this.app = app;
        setLayout(new GridLayout(1, 2));
        add(brandPanel());
        add(formPanel());
    }

    private final UI.Btn retry = new UI.Btn("Retry connection", UI.Kind.GHOST);

    /** problem == null means connected. */
    void setStatus(String s, String problem) {
        storeLabel.setText(s);
        storeLabel.setForeground(problem == null ? Theme.success : Theme.danger);
        retry.setVisible(problem != null);
        if (problem != null) error("Database not connected: " + problem); else msg.setText(" ");
    }

    private JPanel brandPanel() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(8, 14, 34), getWidth(), getHeight(), new Color(37, 78, 190)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        p.setBorder(new EmptyBorder(40, 50, 40, 50));
        JPanel top = new JPanel(); top.setOpaque(false); top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(UI.label("\uD83D\uDE97  AutoCare AI", Font.BOLD, 30, Color.WHITE));
        top.add(Box.createVerticalStrut(6));
        top.add(UI.label("Your car's health, always in sight.", Font.PLAIN, 15, new Color(190, 205, 235)));
        p.add(top, BorderLayout.NORTH);

        Car3D car = new Car3D();
        car.transparentBg = true;
        car.setState(0x3B82F6, 90, 90, 90, 90, 90, false);
        p.add(car, BorderLayout.CENTER);

        JPanel feats = new JPanel(new GridLayout(4, 1, 0, 8)); feats.setOpaque(false);
        for (String s : new String[]{"\u2713  Live health score & 3D vehicle view", "\u2713  PUC, insurance & service reminders", "\u2713  Instant AI diagnosis and GST invoices", "\u2713  Ask the built-in AI assistant anything"})
            feats.add(UI.label(s, Font.PLAIN, 14, new Color(220, 230, 250)));
        p.add(feats, BorderLayout.SOUTH);
        return p;
    }

    private JPanel formPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Theme.bg);
        UI.Card card = new UI.Card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(30, 34, 24, 34));
        card.setPreferredSize(new Dimension(440, 680));

        card.add(left(UI.label("Welcome \uD83D\uDC4B", Font.BOLD, 26, Theme.text)));
        card.add(Box.createVerticalStrut(4));
        card.add(left(UI.label("Sign in or create a free account to get started.", Font.PLAIN, 13, Theme.dim)));
        card.add(Box.createVerticalStrut(18));
        JPanel tabs = new JPanel(new GridLayout(1, 2, 8, 0)); tabs.setOpaque(false);
        tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        tabs.add(tabIn); tabs.add(tabUp);
        card.add(left(tabs));
        card.add(Box.createVerticalStrut(16));

        formHost.setOpaque(false);
        formHost.add(signInForm(), "in");
        formHost.add(signUpForm(), "up");
        card.add(left(formHost));
        card.add(Box.createVerticalStrut(6));
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(msg);
        card.add(Box.createVerticalGlue());

        UI.Btn demo = new UI.Btn("\u2728  Explore with a demo account", UI.Kind.GHOST);
        demo.setAlignmentX(Component.LEFT_ALIGNMENT);
        demo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        demo.addActionListener(e -> { msg.setText(" "); app.demoLogin(this); });
        card.add(demo);
        card.add(Box.createVerticalStrut(10));
        storeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(storeLabel);
        retry.setAlignmentX(Component.LEFT_ALIGNMENT);
        retry.setVisible(false);
        retry.addActionListener(e -> { retry.setVisible(false); msg.setText(" "); AutoCareAI.startStoreInit(); setStatusText("Connecting to MySQL..."); });
        card.add(Box.createVerticalStrut(6));
        card.add(retry);

        tabIn.addActionListener(e -> showTab(true));
        tabUp.addActionListener(e -> showTab(false));
        outer.add(card);
        return outer;
    }

    private void showTab(boolean in) {
        forms.show(formHost, in ? "in" : "up");
        tabIn.setKind(in ? UI.Kind.PRIMARY : UI.Kind.GHOST);
        tabUp.setKind(in ? UI.Kind.GHOST : UI.Kind.PRIMARY);
        msg.setText(" ");
    }

    private JPanel signInForm() {
        JPanel p = column();
        addField(p, "Username", userIn);
        addField(p, "Password", passIn);
        p.add(showPw(passIn));
        p.add(Box.createVerticalStrut(14));
        UI.Btn go = new UI.Btn("Sign in", UI.Kind.PRIMARY);
        go.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44)); go.setAlignmentX(Component.LEFT_ALIGNMENT);
        go.addActionListener(e -> doLogin());
        p.add(go);
        userIn.addActionListener(e -> passIn.requestFocusInWindow());
        passIn.addActionListener(e -> doLogin());
        return p;
    }

    private JPanel signUpForm() {
        JPanel p = column();
        addField(p, "Username", userUp);
        addField(p, "Phone number", phoneUp);
        addField(p, "Password", passUp);
        addField(p, "Confirm password", pass2Up);
        JCheckBox cb = showPw(passUp, pass2Up);
        p.add(cb);
        p.add(Box.createVerticalStrut(10));
        UI.Btn go = new UI.Btn("Create account", UI.Kind.PRIMARY);
        go.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44)); go.setAlignmentX(Component.LEFT_ALIGNMENT);
        go.addActionListener(e -> doRegister());
        p.add(go);
        pass2Up.addActionListener(e -> doRegister());
        return p;
    }

    private static JPanel column() { JPanel p = new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
    private static JComponent left(JComponent c) { c.setAlignmentX(Component.LEFT_ALIGNMENT); return c; }
    private static void addField(JPanel p, String label, JComponent f) {
        p.add(left(UI.label(label, Font.BOLD, 12, Theme.text)));
        p.add(Box.createVerticalStrut(5));
        p.add(left(f));
        p.add(Box.createVerticalStrut(10));
    }
    private static JCheckBox showPw(JPasswordField... fields) {
        JCheckBox cb = new JCheckBox("Show password");
        cb.setOpaque(false); cb.setForeground(Theme.dim); cb.setFont(Theme.font(Font.PLAIN, 12)); cb.setFocusPainted(false);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.addActionListener(e -> { for (JPasswordField f : fields) f.setEchoChar(cb.isSelected() ? (char) 0 : '\u2022'); });
        return cb;
    }

    private void doLogin() {
        String u = userIn.getText().trim(), p = new String(passIn.getPassword());
        if (u.isEmpty() || p.isEmpty()) { msg.setText("Please enter your username and password."); return; }
        app.login(this, u, p);
    }

    private void doRegister() {
        String u = userUp.getText().trim(), ph = phoneUp.getText().trim(), p = new String(passUp.getPassword()), p2 = new String(pass2Up.getPassword());
        if (u.length() < 3) { msg.setText("Username must be at least 3 characters."); return; }
        if (ph.replaceAll("\\D", "").length() < 10) { msg.setText("Please enter a valid phone number (10+ digits)."); return; }
        if (p.length() < 6) { msg.setText("Password must be at least 6 characters."); return; }
        if (!p.equals(p2)) { msg.setText("Passwords don't match."); return; }
        app.register(this, u, ph, p);
    }

    private void setStatusText(String s) { storeLabel.setText(s); storeLabel.setForeground(Theme.dim); }

    void error(String s) { msg.setText("<html>" + s.replace("<", "&lt;") + "</html>"); }
}
