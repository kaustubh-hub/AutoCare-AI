package ai;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Diagnosis / Invoice viewer with Save and Print actions - used by Book Service and History. */
class ReportViewer extends UI.Card {
    private final JTextArea diag = area(), inv = area();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final UI.Btn tabDiag = new UI.Btn("Diagnosis", UI.Kind.PRIMARY), tabInv = new UI.Btn("Invoice", UI.Kind.GHOST);
    private String suggestedName = "autocare-report";
    private final AutoCareAI app;

    ReportViewer(AutoCareAI app, String placeholder) {
        this.app = app;
        setLayout(new BorderLayout(0, 12));
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); tabs.setOpaque(false);
        tabs.add(tabDiag); tabs.add(tabInv);
        add(tabs, BorderLayout.NORTH);
        body.setOpaque(false);
        body.add(wrap(diag), "d"); body.add(wrap(inv), "i");
        add(body, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); actions.setOpaque(false);
        UI.Btn save = new UI.Btn("Save as .txt", UI.Kind.GHOST), print = new UI.Btn("Print", UI.Kind.PRIMARY);
        actions.add(save); actions.add(print);
        add(actions, BorderLayout.SOUTH);
        tabDiag.addActionListener(e -> select(true));
        tabInv.addActionListener(e -> select(false));
        save.addActionListener(e -> save());
        print.addActionListener(e -> print());
        clear(placeholder);
    }

    private static JTextArea area() {
        JTextArea a = new JTextArea();
        a.setEditable(false); a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        a.setBackground(Theme.input); a.setForeground(Theme.text); a.setMargin(new Insets(14, 16, 14, 16));
        return a;
    }
    private static JScrollPane wrap(JTextArea a) {
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(BorderFactory.createLineBorder(Theme.border));
        UI.styleBars(sp);
        return sp;
    }

    private boolean onDiag = true;
    private void select(boolean d) {
        onDiag = d;
        cards.show(body, d ? "d" : "i");
        tabDiag.setKind(d ? UI.Kind.PRIMARY : UI.Kind.GHOST);
        tabInv.setKind(d ? UI.Kind.GHOST : UI.Kind.PRIMARY);
    }

    void clear(String msg) { diag.setText(msg); inv.setText(msg); diag.setCaretPosition(0); select(true); }

    void show(Report r) {
        diag.setText(r.diagnosisText); inv.setText(r.invoiceText);
        diag.setCaretPosition(0); inv.setCaretPosition(0);
        suggestedName = r.invoiceNo.isEmpty() ? "autocare-report" : r.invoiceNo;
    }
    void showInvoiceTab() { select(false); }

    private void save() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(suggestedName + ".txt"));
        if (fc.showSaveDialog(app) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.write(fc.getSelectedFile().toPath(), (diag.getText() + "\n\n" + inv.getText()).getBytes(StandardCharsets.UTF_8));
            app.toast("Saved to " + fc.getSelectedFile().getName());
        } catch (IOException e) { JOptionPane.showMessageDialog(app, "Could not save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
    }
    private void print() {
        try { (onDiag ? diag : inv).print(); }
        catch (PrinterException e) { JOptionPane.showMessageDialog(app, "Printing failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
    }
}
