package ai;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Add / edit / delete vehicles. A live 3D preview reacts to colour and condition sliders. */
class GaragePage extends JPanel implements Page {
    private static final int[] PALETTE = {0xEF4444, 0xF97316, 0xEAB308, 0x22C55E, 0x3B82F6, 0x6366F1, 0xEC4899, 0xF1F5F9, 0x64748B, 0x111827};

    private final AutoCareAI app;
    private final DefaultListModel<Vehicle> model = new DefaultListModel<>();
    private final JList<Vehicle> list = new JList<>(model);
    private boolean loading;
    private Vehicle editing = new Vehicle();

    private final Car3D preview = new Car3D();
    private final UI.Field nickname = new UI.Field("e.g. Family SUV"), make = new UI.Field("e.g. Hyundai"), modelF = new UI.Field("e.g. Creta"),
            year = new UI.Field("e.g. 2021"), reg = new UI.Field("e.g. MH 01 AB 1234"), vin = new UI.Field("17-character chassis number"),
            odo = new UI.Field("e.g. 46500"), puc = new UI.Field("dd/MM/yyyy"), ins = new UI.Field("dd/MM/yyyy"), last = new UI.Field("dd/MM/yyyy"),
            notes = new UI.Field("What was done last time? (optional)");
    private final JComboBox<String> fuel = UI.combo(Health.FUELS);
    private final JSlider[] sliders = new JSlider[5];
    private final JLabel[] sliderLabels = new JLabel[5];
    private final JLabel msg = UI.label(" ", Font.BOLD, 12, Theme.danger);
    private final UI.Btn delete = new UI.Btn("Delete vehicle", UI.Kind.DANGER);
    private final JLabel formTitle = UI.label("Add a vehicle", Font.BOLD, 20, Theme.text);
    private int colorRgb = PALETTE[4];
    private final JPanel swatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

    GaragePage(AutoCareAI app) {
        this.app = app;
        setLayout(new BorderLayout(16, 0));
        setBackground(Theme.bg);
        setBorder(new EmptyBorder(22, 28, 24, 28));
        add(listCard(), BorderLayout.WEST);
        add(UI.scroll(formCard()), BorderLayout.CENTER);
        fuel.addActionListener(e -> { if (!loading) { updateSliderNames(); pushPreview(); } });
    }

    // ------------------------------------------------------------------ left: vehicle list
    private JComponent listCard() {
        UI.Card c = new UI.Card();
        c.setLayout(new BorderLayout(0, 12));
        c.setPreferredSize(new Dimension(290, 0));
        c.add(UI.label("My vehicles", Font.BOLD, 18, Theme.text), BorderLayout.NORTH);
        list.setBackground(Theme.card);
        list.setFixedCellHeight(64);
        list.setCellRenderer(new ListCellRenderer<Vehicle>() {
            @Override public Component getListCellRendererComponent(JList<? extends Vehicle> l, Vehicle v, int i, boolean sel, boolean foc) {
                return new JComponent() {
                    @Override public Dimension getPreferredSize() { return new Dimension(200, 64); }
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create(); UI.aa(g2);
                        if (sel) { g2.setColor(Theme.hover); g2.fillRoundRect(0, 2, getWidth(), 60, 14, 14); g2.setColor(Theme.primary); g2.drawRoundRect(0, 2, getWidth() - 1, 60, 14, 14); }
                        g2.setColor(new Color(v.colorRgb)); g2.fillRoundRect(12, 20, 24, 24, 8, 8);
                        g2.setColor(Theme.text); g2.setFont(Theme.font(Font.BOLD, 14)); g2.drawString(v.displayName(), 48, 29);
                        g2.setColor(Theme.dim); g2.setFont(Theme.font(Font.PLAIN, 12)); g2.drawString(v.registration.isEmpty() ? v.fuelType : v.registration, 48, 47);
                        int s = Health.score(v);
                        g2.setColor(Health.color(s)); g2.setFont(Theme.font(Font.BOLD, 14));
                        String t = s + "%"; g2.drawString(t, getWidth() - g2.getFontMetrics().stringWidth(t) - 10, 38);
                        g2.dispose();
                    }
                };
            }
        });
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || loading) return;
            Vehicle v = list.getSelectedValue();
            if (v != null) { load(v); app.setActive(v.id); }
        });
        JScrollPane sp = new JScrollPane(list); sp.setBorder(null); sp.getViewport().setBackground(Theme.card); UI.styleBars(sp);
        c.add(sp, BorderLayout.CENTER);
        UI.Btn add = new UI.Btn("+  Add vehicle", UI.Kind.PRIMARY);
        add.addActionListener(e -> { loading = true; list.clearSelection(); loading = false; load(new Vehicle()); });
        c.add(add, BorderLayout.SOUTH);
        return c;
    }

    // ------------------------------------------------------------------ right: form
    private JComponent formCard() {
        UI.Card c = new UI.Card();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new EmptyBorder(22, 26, 22, 26));
        formTitle.setAlignmentX(LEFT_ALIGNMENT);
        c.add(formTitle);
        c.add(Box.createVerticalStrut(10));

        preview.setPreferredSize(new Dimension(400, 230));
        preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));
        preview.setAlignmentX(LEFT_ALIGNMENT);
        c.add(preview);
        c.add(Box.createVerticalStrut(14));

        c.add(section("Vehicle details"));
        JPanel g1 = grid();
        add(g1, 0, 0, "Nickname", nickname); add(g1, 1, 0, "Fuel type", fuel);
        add(g1, 0, 1, "Make", make);         add(g1, 1, 1, "Model", modelF);
        add(g1, 0, 2, "Year", year);         add(g1, 1, 2, "Registration number", reg);
        add(g1, 0, 3, "Chassis number (VIN)", vin); add(g1, 1, 3, "Odometer (km)", odo);
        c.add(g1);

        c.add(Box.createVerticalStrut(8));
        c.add(section("Documents & service dates"));
        JPanel g2 = grid();
        add(g2, 0, 0, "PUC expiry date", puc); add(g2, 1, 0, "Insurance expiry date", ins);
        add(g2, 0, 1, "Last service date", last); add(g2, 1, 1, "Last service notes", notes);
        c.add(g2);

        c.add(Box.createVerticalStrut(8));
        c.add(section("Paint colour"));
        swatches.setOpaque(false); swatches.setAlignmentX(LEFT_ALIGNMENT);
        swatches.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        for (int rgb : PALETTE) {
            JButton b = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create(); UI.aa(g2);
                    g2.setColor(new Color(rgb)); g2.fillOval(2, 2, 26, 26);
                    if (colorRgb == rgb) { g2.setColor(Theme.text); g2.setStroke(new BasicStroke(3)); g2.drawOval(0, 0, 29, 29); }
                    else { g2.setColor(Theme.border); g2.drawOval(2, 2, 26, 26); }
                    g2.dispose();
                }
            };
            b.setPreferredSize(new Dimension(32, 32)); b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> { colorRgb = rgb; swatches.repaint(); pushPreview(); });
            swatches.add(b);
        }
        c.add(swatches);

        c.add(Box.createVerticalStrut(12));
        c.add(section("Condition check  (your best estimate - we use this for the health score)"));
        String[] hints = {"Fresh oil = 100%. Dark / overdue oil = low.", "Pad thickness left: new = 100%, near the wear line = under 20%.",
                "Tread depth: new = 100%, near the legal limit = about 15%.", "Starts instantly = high. Slow cranking = low.", "Coolant at the MAX mark and no leaks = 100%."};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            sliders[i] = new JSlider(0, 100, 80);
            sliders[i].setOpaque(false);
            sliders[i].addChangeListener(e -> { sliderLabels[idx].setText(sliderText(idx)); if (!loading) pushPreview(); });
            sliderLabels[i] = UI.label("", Font.BOLD, 13, Theme.text);
            JPanel r = new JPanel(new BorderLayout(14, 0)); r.setOpaque(false); r.setAlignmentX(LEFT_ALIGNMENT);
            r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
            JPanel left = new JPanel(); left.setOpaque(false); left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setPreferredSize(new Dimension(230, 44));
            left.add(sliderLabels[i]);
            left.add(UI.label("<html>" + hints[i] + "</html>", Font.PLAIN, 10, Theme.dim));
            r.add(left, BorderLayout.WEST);
            r.add(sliders[i], BorderLayout.CENTER);
            c.add(r);
        }

        c.add(Box.createVerticalStrut(14));
        msg.setAlignmentX(LEFT_ALIGNMENT);
        c.add(msg);
        c.add(Box.createVerticalStrut(6));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); actions.setOpaque(false); actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        UI.Btn save = new UI.Btn("Save vehicle", UI.Kind.PRIMARY);
        save.addActionListener(e -> save());
        delete.addActionListener(e -> delete());
        actions.add(save); actions.add(delete);
        c.add(actions);
        updateSliderNames();
        return c;
    }

    private JComponent section(String t) {
        JLabel l = UI.label(t, Font.BOLD, 14, Theme.primary);
        l.setAlignmentX(LEFT_ALIGNMENT); l.setBorder(new EmptyBorder(0, 0, 6, 0));
        return l;
    }
    private JPanel grid() {
        JPanel p = new JPanel(new GridBagLayout()); p.setOpaque(false); p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }
    private void add(JPanel p, int col, int row, String label, JComponent f) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = col; g.weightx = 0.5; g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(0, col == 0 ? 0 : 8, 0, col == 0 ? 8 : 0);
        g.gridy = row * 2;
        p.add(UI.label(label, Font.BOLD, 12, Theme.text), g);
        g.gridy = row * 2 + 1; g.insets = new Insets(4, col == 0 ? 0 : 8, 12, col == 0 ? 8 : 0);
        p.add(f, g);
    }

    private String sliderText(int i) { return Health.metricNames(currentFuelProbe())[i] + " - " + sliders[i].getValue() + "%"; }
    private Vehicle currentFuelProbe() { Vehicle v = new Vehicle(); v.fuelType = (String) fuel.getSelectedItem(); return v; }
    private void updateSliderNames() { for (int i = 0; i < 5; i++) if (sliders[i] != null && sliderLabels[i] != null) sliderLabels[i].setText(sliderText(i)); }
    private void pushPreview() {
        preview.setState(colorRgb, sliders[0].getValue(), sliders[1].getValue(), sliders[2].getValue(), sliders[3].getValue(), sliders[4].getValue(),
                ((String) fuel.getSelectedItem()).startsWith("Electric"));
    }

    // ------------------------------------------------------------------ data
    @Override public void refresh() {
        loading = true;
        model.clear();
        for (Vehicle v : app.vehicles) model.addElement(v);
        int sel = -1;
        for (int i = 0; i < model.size(); i++) if (app.active != null && model.get(i).id == app.active.id) sel = i;
        if (sel >= 0) list.setSelectedIndex(sel); else list.clearSelection();
        loading = false;
        load(sel >= 0 ? model.get(sel) : new Vehicle());
    }

    private void load(Vehicle v) {
        loading = true;
        editing = v.copy();
        boolean isNew = v.id == 0;
        formTitle.setText(isNew ? "Add a vehicle" : "Edit " + v.displayName());
        delete.setVisible(!isNew);
        nickname.setText(v.nickname); make.setText(v.make); modelF.setText(v.model);
        year.setText(v.year > 0 ? String.valueOf(v.year) : ""); reg.setText(v.registration); vin.setText(v.vin);
        odo.setText(isNew ? "" : String.valueOf(v.mileage));
        puc.setText(v.pucExpiry == null ? "" : Fmt.date(v.pucExpiry));
        ins.setText(v.insuranceExpiry == null ? "" : Fmt.date(v.insuranceExpiry));
        last.setText(v.lastService == null ? "" : Fmt.date(v.lastService));
        notes.setText(v.serviceNotes);
        fuel.setSelectedItem(v.fuelType);
        colorRgb = v.colorRgb; swatches.repaint();
        int[] m = Health.metrics(v);
        for (int i = 0; i < 5; i++) sliders[i].setValue(m[i]);
        updateSliderNames();
        pushPreview();
        msg.setText(" ");
        loading = false;
    }

    private void save() {
        try {
            Vehicle v = editing.copy();
            v.userId = app.user.id;
            v.nickname = nickname.getText().trim(); v.make = make.getText().trim(); v.model = modelF.getText().trim();
            v.registration = reg.getText().trim().toUpperCase(); v.vin = vin.getText().trim().toUpperCase();
            if (v.make.isEmpty() || v.model.isEmpty()) throw new IllegalArgumentException("Please enter the make and model (for example Hyundai / Creta).");
            if (v.registration.isEmpty()) throw new IllegalArgumentException("Please enter the registration number.");
            String y = year.getText().trim();
            v.year = y.isEmpty() ? 0 : Integer.parseInt(y);
            if (v.year != 0 && (v.year < 1950 || v.year > LocalDate.now().getYear() + 1)) throw new IllegalArgumentException("Please enter a valid model year.");
            String o = odo.getText().trim().replace(",", "");
            if (o.isEmpty()) throw new IllegalArgumentException("Please enter the odometer reading in km.");
            v.mileage = Integer.parseInt(o);
            if (v.mileage < 0) throw new IllegalArgumentException("Odometer can't be negative.");
            v.fuelType = (String) fuel.getSelectedItem();
            v.pucExpiry = Fmt.parse(puc.getText()); v.insuranceExpiry = Fmt.parse(ins.getText()); v.lastService = Fmt.parse(last.getText());
            v.serviceNotes = notes.getText().trim();
            v.colorRgb = colorRgb;
            v.oil = sliders[0].getValue(); v.brakes = sliders[1].getValue(); v.tyres = sliders[2].getValue();
            v.battery = sliders[3].getValue(); v.coolant = sliders[4].getValue();
            Vehicle saved = AutoCareAI.store.saveVehicle(v);
            app.reloadData();
            app.setActive(saved.id);
            app.toast("\u2713 " + saved.displayName() + " saved");
            refresh();
        } catch (NumberFormatException e) { msg.setText("Year and odometer must be numbers."); }
        catch (IllegalArgumentException | StoreException e) { msg.setText(e.getMessage()); }
    }

    private void delete() {
        if (editing.id == 0) return;
        int ok = JOptionPane.showConfirmDialog(app, "Delete " + editing.displayName() + "? Past invoices will be kept.", "Delete vehicle", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            AutoCareAI.store.deleteVehicle(editing.id, app.user.id);
            app.active = null;
            app.reloadData();
            app.toast("Vehicle deleted");
            refresh();
        } catch (StoreException e) { msg.setText(e.getMessage()); }
    }
}
