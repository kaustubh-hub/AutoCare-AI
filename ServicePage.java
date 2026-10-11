package ai;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;

/** Pick an issue, run the AI diagnosis, get a report + invoice. */
class ServicePage extends JPanel implements Page {
    private final AutoCareAI app;
    private final JLabel vehicleLabel = UI.label("", Font.BOLD, 15, Theme.text), vehicleSub = UI.label("", Font.PLAIN, 12, Theme.dim);
    private final JComboBox<String> issue = UI.combo(Health.ISSUES), advisor = UI.combo(Health.ADVISORS);
    private final UI.Field odo = new UI.Field("Current odometer in km"), notes = new UI.Field("Anything the technician should know? (optional)");
    private final JCheckBox served = new JCheckBox("Mark as serviced today (resets next-service timer)");
    private final JLabel estimate = UI.label("", Font.BOLD, 20, Theme.primary), msg = UI.label(" ", Font.BOLD, 12, Theme.danger);
    private final ReportViewer viewer;

    ServicePage(AutoCareAI app) {
        this.app = app;
        viewer = new ReportViewer(app, "Your AI diagnosis will appear here.\n\nChoose the issue on the left and press 'Run AI diagnosis'.");
        setLayout(new BorderLayout(16, 0));
        setBackground(Theme.bg);
        setBorder(new EmptyBorder(22, 28, 24, 28));

        UI.Card form = new UI.Card();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(380, 0));
        form.add(left(UI.label("Book a service", Font.BOLD, 20, Theme.text)));
        form.add(Box.createVerticalStrut(14));
        form.add(left(UI.label("VEHICLE", Font.BOLD, 11, Theme.dim)));
        form.add(Box.createVerticalStrut(4));
        form.add(left(vehicleLabel)); form.add(left(vehicleSub));
        form.add(Box.createVerticalStrut(16));
        field(form, "What's the problem?", issue);
        field(form, "Service advisor", advisor);
        field(form, "Current odometer (km)", odo);
        field(form, "Notes for the technician", notes);
        served.setOpaque(false); served.setForeground(Theme.text); served.setFont(Theme.font(Font.PLAIN, 12)); served.setFocusPainted(false);
        form.add(left(served));
        form.add(Box.createVerticalStrut(14));
        form.add(left(UI.label("ESTIMATED COST (incl. 18% GST)", Font.BOLD, 11, Theme.dim)));
        form.add(left(estimate));
        form.add(Box.createVerticalStrut(10));
        form.add(left(msg));
        form.add(Box.createVerticalGlue());
        UI.Btn run = new UI.Btn("\u26A1  Run AI diagnosis & create invoice", UI.Kind.PRIMARY);
        run.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        left(run);
        run.addActionListener(e -> run());
        form.add(run);
        issue.addActionListener(e -> updateEstimate());

        add(form, BorderLayout.WEST);
        add(viewer, BorderLayout.CENTER);
    }

    private static JComponent left(JComponent c) { c.setAlignmentX(Component.LEFT_ALIGNMENT); return c; }
    private static void field(JPanel p, String label, JComponent f) {
        p.add(left(UI.label(label, Font.BOLD, 12, Theme.text)));
        p.add(Box.createVerticalStrut(5));
        p.add(left(f));
        p.add(Box.createVerticalStrut(12));
    }
    private void updateEstimate() { estimate.setText("INR " + Fmt.money(Health.estimateTotal((String) issue.getSelectedItem()))); }

    @Override public void refresh() {
        Vehicle v = app.active;
        if (v == null) {
            vehicleLabel.setText("No vehicle selected"); vehicleSub.setText("Add a vehicle in My Garage first.");
            odo.setText("");
        } else {
            vehicleLabel.setText(v.displayName());
            vehicleSub.setText(v.make + " " + v.model + "  |  " + v.registration);
            odo.setText(String.valueOf(v.mileage));
        }
        msg.setText(" ");
        updateEstimate();
    }

    private void run() {
        Vehicle v = app.active;
        if (v == null) { msg.setText("Please add a vehicle in My Garage first."); app.showPage("garage"); return; }
        try {
            v = v.copy();
            String o = odo.getText().trim().replace(",", "");
            if (o.isEmpty()) throw new IllegalArgumentException("Please enter the current odometer reading.");
            int km = Integer.parseInt(o);
            if (km < 0) throw new IllegalArgumentException("Odometer can't be negative.");
            boolean changed = km != v.mileage;
            v.mileage = km;
            if (served.isSelected()) { v.lastService = LocalDate.now(); changed = true; }
            if (changed) AutoCareAI.store.saveVehicle(v);
            Report r = Health.makeReport(app.user, v, (String) issue.getSelectedItem(), (String) advisor.getSelectedItem(), LocalDate.now(), notes.getText().trim());
            Report saved = AutoCareAI.store.saveReport(r);
            app.reloadData();
            viewer.show(saved);
            viewer.clear(saved.diagnosisText); viewer.show(saved);
            served.setSelected(false); notes.setText("");
            msg.setText(" ");
            app.toast("\u2713 Diagnosis ready - " + saved.invoiceNo + " (+" + saved.pointsEarned + " loyalty points)");
        } catch (NumberFormatException e) { msg.setText("Odometer must be a number."); }
        catch (IllegalArgumentException | StoreException e) { msg.setText(e.getMessage()); }
    }
}
