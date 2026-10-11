package ai;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Past diagnoses and invoices. */
class HistoryPage extends JPanel implements Page {
    private final AutoCareAI app;
    private List<Report> rows = new ArrayList<>();
    private final String[] cols = {"Invoice", "Date", "Vehicle", "Issue", "Total (INR)", "Health"};
    private final AbstractTableModel tm = new AbstractTableModel() {
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        public Object getValueAt(int r, int c) {
            Report x = rows.get(r);
            switch (c) {
                case 0: return x.invoiceNo; case 1: return Fmt.date(x.createdOn); case 2: return x.vehicleName;
                case 3: return x.issue; case 4: return Fmt.money(x.total); default: return x.healthScore + "/100";
            }
        }
    };
    private final JTable table = new JTable(tm);
    private final ReportViewer viewer;
    private final JPanel summary = new JPanel(new GridLayout(1, 4, 14, 0));

    HistoryPage(AutoCareAI app) {
        this.app = app;
        viewer = new ReportViewer(app, "Select a record on the left to view its diagnosis and invoice.");
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.bg);
        setBorder(new EmptyBorder(22, 28, 24, 28));
        summary.setOpaque(false);
        add(summary, BorderLayout.NORTH);

        table.setRowHeight(38);
        table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0, 0));
        table.setFont(Theme.font(Font.PLAIN, 13)); table.setForeground(Theme.text); table.setBackground(Theme.card);
        table.setSelectionBackground(Theme.hover); table.setSelectionForeground(Theme.text);
        table.setFillsViewportHeight(true);
        DefaultTableCellRenderer cell = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, false, r, c);
                setBorder(new EmptyBorder(0, 12, 0, 12));
                setBackground(s ? Theme.hover : Theme.card);
                setForeground(Theme.text);
                if (c == 4) setHorizontalAlignment(SwingConstants.RIGHT); else setHorizontalAlignment(SwingConstants.LEFT);
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, cell);
        JTableHeader h = table.getTableHeader();
        h.setReorderingAllowed(false);
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, false, false, r, c);
                setFont(Theme.font(Font.BOLD, 12)); setForeground(Theme.dim); setBackground(Theme.card);
                setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border), new EmptyBorder(8, 12, 8, 12)));
                setHorizontalAlignment(c == 4 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                return this;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            int r = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0) viewer.show(rows.get(r));
        });
        JScrollPane sp = new JScrollPane(table); sp.setBorder(null); sp.getViewport().setBackground(Theme.card); UI.styleBars(sp);
        UI.Card tableCard = new UI.Card();
        tableCard.setLayout(new BorderLayout(0, 10));
        tableCard.setBorder(new EmptyBorder(16, 16, 16, 16));
        tableCard.add(UI.label("Service history", Font.BOLD, 18, Theme.text), BorderLayout.NORTH);
        tableCard.add(sp, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableCard, viewer);
        split.setOpaque(false); split.setBorder(null); split.setDividerSize(16); split.setResizeWeight(0.52);
        split.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) { @Override public void paint(Graphics g) {} };
            }
        });
        add(split, BorderLayout.CENTER);
    }

    @Override public void refresh() {
        rows = new ArrayList<>(app.reports);
        java.util.Collections.reverse(rows);
        tm.fireTableDataChanged();
        double total = 0;
        for (Report r : rows) total += r.total;
        int pts = Health.points(rows);
        summary.removeAll();
        summary.add(chip("TOTAL VISITS", String.valueOf(rows.size())));
        summary.add(chip("TOTAL SPENT", "INR " + Fmt.money(total)));
        summary.add(chip("LOYALTY POINTS", String.valueOf(pts)));
        summary.add(chip("MEMBER TIER", Health.tier(pts)));
        summary.revalidate(); summary.repaint();
        if (!rows.isEmpty()) table.setRowSelectionInterval(0, 0);
        else viewer.clear(app.vehicles.isEmpty() ? "Nothing here yet.\n\nAdd a vehicle, then book a service to see your history." : "No services yet.\n\nGo to 'Book Service' to run your first AI diagnosis.");
    }

    private JComponent chip(String title, String value) {
        UI.Card c = new UI.Card();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new EmptyBorder(12, 18, 12, 18));
        JLabel a = UI.label(title, Font.BOLD, 11, Theme.dim), b = UI.label(value, Font.BOLD, 20, Theme.text);
        a.setAlignmentX(LEFT_ALIGNMENT); b.setAlignmentX(LEFT_ALIGNMENT);
        c.add(a); c.add(Box.createVerticalStrut(2)); c.add(b);
        return c;
    }
}
