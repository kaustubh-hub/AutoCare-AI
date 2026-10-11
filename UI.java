package ai;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.List;

/** Small custom-painted widget kit used by every screen. */
final class UI {
    private UI() {}

    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    static JLabel label(String text, int style, int size, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(c);
        return l;
    }

    // ---------------------------------------------------------------- Card
    static class Card extends JPanel {
        Card() { setOpaque(false); setBorder(new EmptyBorder(18, 20, 18, 20)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(Theme.card);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.setColor(Theme.border);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------- Button
    enum Kind { PRIMARY, GHOST, DANGER, SUCCESS }

    static class Btn extends JButton {
        private Kind kind;
        Btn(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setFont(Theme.font(Font.BOLD, 13));
            setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 20, 10, 20));
            setForeground(kind == Kind.GHOST ? Theme.text : Color.WHITE);
        }
        void setKind(Kind k) { kind = k; setForeground(k == Kind.GHOST ? Theme.text : Color.WHITE); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            boolean hot = getModel().isRollover() || getModel().isPressed();
            Color c;
            switch (kind) {
                case DANGER: c = hot ? Theme.danger.darker() : Theme.danger; break;
                case SUCCESS: c = hot ? Theme.success.darker() : Theme.success; break;
                case GHOST: c = hot ? Theme.hover : Theme.input; break;
                default: c = hot ? Theme.primaryDark : Theme.primary;
            }
            if (!isEnabled()) c = Theme.alpha(c, 110);
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            if (kind == Kind.GHOST) { g2.setColor(Theme.border); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12); }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------- Inputs
    private static void paintField(Graphics g, JTextComponent tc, String hint, Runnable superPaint) {
        Graphics2D g2 = (Graphics2D) g.create();
        aa(g2);
        g2.setColor(Theme.input);
        g2.fillRoundRect(0, 0, tc.getWidth() - 1, tc.getHeight() - 1, 12, 12);
        g2.setColor(tc.isFocusOwner() ? Theme.primary : Theme.border);
        g2.setStroke(new BasicStroke(tc.isFocusOwner() ? 2f : 1f));
        g2.drawRoundRect(1, 1, tc.getWidth() - 3, tc.getHeight() - 3, 12, 12);
        g2.dispose();
        superPaint.run();
        if (tc.getText().isEmpty() && hint != null) {
            Graphics2D h = (Graphics2D) g.create();
            aa(h);
            h.setColor(Theme.alpha(Theme.dim, 170));
            h.setFont(tc.getFont());
            FontMetrics fm = h.getFontMetrics();
            h.drawString(hint, tc.getInsets().left, (tc.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            h.dispose();
        }
    }
    private static void styleField(JTextComponent t) {
        t.setOpaque(false);
        t.setFont(Theme.font(Font.PLAIN, 14));
        t.setForeground(Theme.text);
        t.setCaretColor(Theme.primary);
        t.setSelectionColor(Theme.primary);
        t.setSelectedTextColor(Color.WHITE);
        t.setBorder(new EmptyBorder(9, 13, 9, 13));
        t.setPreferredSize(new Dimension(220, 40));
        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    }

    static class Field extends JTextField {
        private final String hint;
        Field(String hint) { this.hint = hint; styleField(this); }
        @Override protected void paintComponent(Graphics g) { paintField(g, this, hint, () -> super.paintComponent(g)); }
    }
    static class Pass extends JPasswordField {
        private final String hint;
        Pass(String hint) { this.hint = hint; styleField(this); }
        @Override protected void paintComponent(Graphics g) { paintField(g, this, hint, () -> super.paintComponent(g)); }
    }

    static JComboBox<String> combo(String[] items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(Theme.font(Font.PLAIN, 14));
        c.setBackground(Theme.input);
        c.setForeground(Theme.text);
        c.setPreferredSize(new Dimension(220, 40));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        c.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                setBorder(new EmptyBorder(7, 12, 7, 12));
                setBackground(sel ? Theme.primary : Theme.input);
                setForeground(sel ? Color.WHITE : Theme.text);
                return this;
            }
        });
        return c;
    }

    // ---------------------------------------------------------------- Scrolling
    static class ScrollPanel extends JPanel implements Scrollable {
        ScrollPanel() { setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(60, r.height - 60); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getViewport().setBackground(Theme.bg);
        sp.setBackground(Theme.bg);
        styleBars(sp);
        return sp;
    }
    static void styleBars(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new FlatScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new FlatScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
        sp.getVerticalScrollBar().setUnitIncrement(16);
    }

    static class FlatScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { trackColor = Theme.bg; thumbColor = Theme.border; }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(Theme.alpha(Theme.dim, 120));
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- Chip / pill
    static class Pill extends JLabel {
        private final Color tint;
        Pill(String text, Color tint) {
            super(text);
            this.tint = tint;
            setFont(Theme.font(Font.BOLD, 11));
            setForeground(tint);
            setBorder(new EmptyBorder(4, 10, 4, 10));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(Theme.alpha(tint, 38));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------- Text wrapping
    static List<String> wrap(String text, FontMetrics fm, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { out.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(trial) <= maxW || line.length() == 0) line = new StringBuilder(trial);
                else { out.add(line.toString()); line = new StringBuilder(word); }
            }
            out.add(line.toString());
        }
        return out;
    }

    // ---------------------------------------------------------------- Gauge
    static class Gauge extends JComponent {
        private int target, shown;
        private Timer anim;
        Gauge() { setPreferredSize(new Dimension(220, 190)); }
        void setValue(int v) {
            target = v; shown = 0;
            if (anim != null) anim.stop();
            anim = new Timer(16, e -> { shown += Math.max(1, (target - shown) / 8); if (shown >= target) { shown = target; anim.stop(); } repaint(); });
            anim.start();
        }
        @Override public void removeNotify() { if (anim != null) anim.stop(); super.removeNotify(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int size = Math.min(getWidth(), getHeight()) - 24;
            int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2 + 6;
            g2.setStroke(new BasicStroke(16, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.border);
            g2.draw(new Arc2D.Double(x, y, size, size, 225, -270, Arc2D.OPEN));
            g2.setColor(Health.color(shown));
            g2.draw(new Arc2D.Double(x, y, size, size, 225, -270.0 * shown / 100.0, Arc2D.OPEN));
            g2.setColor(Theme.text);
            g2.setFont(Theme.font(Font.BOLD, 44));
            FontMetrics fm = g2.getFontMetrics();
            String s = String.valueOf(shown);
            g2.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, y + size / 2 + fm.getAscent() / 3);
            g2.setColor(Theme.dim);
            g2.setFont(Theme.font(Font.PLAIN, 12));
            fm = g2.getFontMetrics();
            String t = "out of 100";
            g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, y + size / 2 + 26);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- Metric bars
    static class MetricBars extends JComponent {
        private String[] names = {}; private int[] vals = {};
        void set(String[] n, int[] v) { names = n; vals = v; setPreferredSize(new Dimension(300, 36 * n.length + 4)); revalidate(); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            for (int i = 0; i < names.length; i++) {
                int y = i * 36;
                g2.setFont(Theme.font(Font.PLAIN, 13));
                g2.setColor(Theme.text);
                g2.drawString(names[i], 0, y + 14);
                g2.setFont(Theme.font(Font.BOLD, 13));
                g2.setColor(Health.color(vals[i]));
                String p = vals[i] + "%";
                g2.drawString(p, getWidth() - g2.getFontMetrics().stringWidth(p), y + 14);
                g2.setColor(Theme.border);
                g2.fillRoundRect(0, y + 21, getWidth(), 8, 8, 8);
                g2.setColor(Health.color(vals[i]));
                g2.fillRoundRect(0, y + 21, Math.max(8, getWidth() * vals[i] / 100), 8, 8, 8);
            }
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- Progress bar (loyalty)
    static class Progress extends JComponent {
        private double frac; private Color color = Theme.accent;
        Progress() { setPreferredSize(new Dimension(100, 10)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 10)); }
        void set(double f, Color c) { frac = Math.max(0, Math.min(1, f)); color = c; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(Theme.border); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.setColor(color); g2.fillRoundRect(0, 0, Math.max(10, (int) (getWidth() * frac)), getHeight(), 10, 10);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- Spend chart
    static class SpendChart extends JComponent {
        private List<Report> data = new ArrayList<>();
        SpendChart() { setPreferredSize(new Dimension(300, 170)); }
        void set(List<Report> reports) {
            int from = Math.max(0, reports.size() - 6);
            data = new ArrayList<>(reports.subList(from, reports.size()));
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth(), h = getHeight();
            if (data.isEmpty()) {
                g2.setColor(Theme.dim); g2.setFont(Theme.font(Font.PLAIN, 13));
                String[] lines = {"No services yet.", "Your spending trend will appear here."};
                for (int i = 0; i < lines.length; i++) g2.drawString(lines[i], (w - g2.getFontMetrics().stringWidth(lines[i])) / 2, h / 2 - 4 + i * 20);
                g2.dispose(); return;
            }
            double max = 1;
            for (Report r : data) max = Math.max(max, r.total);
            int n = data.size(), slot = w / Math.max(n, 3), bw = Math.min(40, slot - 14), base = h - 24, top = 22;
            for (int i = 0; i < n; i++) {
                Report r = data.get(i);
                int bh = (int) ((base - top) * r.total / max);
                int x = i * slot + (slot - bw) / 2;
                g2.setPaint(new GradientPaint(0, base - bh, Theme.primary, 0, base, Theme.accent));
                g2.fillRoundRect(x, base - bh, bw, bh, 8, 8);
                g2.setColor(Theme.dim); g2.setFont(Theme.font(Font.PLAIN, 11));
                String d = r.createdOn == null ? "" : String.format("%02d/%02d", r.createdOn.getDayOfMonth(), r.createdOn.getMonthValue());
                g2.drawString(d, x + (bw - g2.getFontMetrics().stringWidth(d)) / 2, h - 6);
                String amt = r.total >= 1000 ? String.format("%.1fk", r.total / 1000) : String.format("%.0f", r.total);
                g2.setColor(Theme.text);
                g2.drawString(amt, x + (bw - g2.getFontMetrics().stringWidth(amt)) / 2, base - bh - 5);
            }
            g2.dispose();
        }
    }
}
