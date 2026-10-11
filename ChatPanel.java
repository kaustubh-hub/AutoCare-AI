package ai;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Docked AI assistant (right side). */
class ChatPanel extends JPanel {
    private static final int BUBBLE_W = 270;
    private final AutoCareAI app;
    private final ChatEngine engine;
    private final List<String[]> log;
    private final JPanel list = new JPanel();
    private final JScrollPane sp;
    private final UI.Field input = new UI.Field("Ask about your car or the app...");
    private JComponent typing;

    ChatPanel(AutoCareAI app, ChatEngine engine, List<String[]> log) {
        this.app = app; this.engine = engine; this.log = log;
        setLayout(new BorderLayout());
        setBackground(Theme.card);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.border));
        setPreferredSize(new Dimension(390, 0));

        // header
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(16, 18, 12, 14));
        JPanel titles = new JPanel(); titles.setOpaque(false); titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel tl = UI.label("AutoCare Assistant", Font.BOLD, 17, Theme.text); tl.setAlignmentX(LEFT_ALIGNMENT); titles.add(tl);
        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4)); pillRow.setOpaque(false);
        pillRow.add(new UI.Pill(engine.online() ? "Claude AI - online" : "Offline expert mode", engine.online() ? Theme.success : Theme.primary));
        pillRow.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(pillRow);
        head.add(titles, BorderLayout.CENTER);
        UI.Btn close = new UI.Btn("\u2715", UI.Kind.GHOST);
        close.setBorder(new EmptyBorder(6, 10, 6, 10));
        close.addActionListener(e -> app.toggleChat(false));
        JPanel closeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); closeWrap.setOpaque(false); closeWrap.add(close);
        head.add(closeWrap, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        // messages
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Theme.card);
        list.setBorder(new EmptyBorder(6, 14, 6, 14));
        sp = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null); sp.getViewport().setBackground(Theme.card); UI.styleBars(sp);
        add(sp, BorderLayout.CENTER);

        // chips + input
        JPanel south = new JPanel(); south.setOpaque(false); south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBorder(new EmptyBorder(6, 14, 14, 14));
        JPanel chips = new JPanel(new GridLayout(0, 2, 6, 6)); chips.setOpaque(false);
        String[][] qs = {{"Car health?", "How healthy is my car?"}, {"Next service?", "When is my next service?"}, {"Brakes squealing?", "Why do my brakes squeal?"},
                {"Monsoon tips", "Monsoon car care tips"}, {"Repair costs", "How much does a service cost?"}, {"Earn points?", "How do I earn loyalty points?"}};
        for (String[] qa : qs) {
            JButton b = new JButton(qa[0]);
            b.setFont(Theme.font(Font.PLAIN, 12)); b.setForeground(Theme.primary); b.setBackground(Theme.input);
            b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border), new EmptyBorder(5, 8, 5, 8)));
            b.addActionListener(e -> send(qa[1]));
            chips.add(b);
        }
        chips.setAlignmentX(LEFT_ALIGNMENT);
        chips.setBorder(new EmptyBorder(0, 0, 8, 0));
        south.add(chips);
        JPanel row = new JPanel(new BorderLayout(8, 0)); row.setOpaque(false);
        row.add(input, BorderLayout.CENTER);
        UI.Btn send = new UI.Btn("Send", UI.Kind.PRIMARY);
        row.add(send, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        south.add(row);
        add(south, BorderLayout.SOUTH);
        Runnable go = () -> { String t = input.getText().trim(); if (!t.isEmpty()) { input.setText(""); send(t); } };
        send.addActionListener(e -> go.run());
        input.addActionListener(e -> go.run());

        // replay history / greeting
        if (log.isEmpty()) {
            String name = app.user == null ? "" : " " + app.user.username;
            String hello = "Hi" + name + "! I'm your AutoCare assistant. Ask me about your car's health, warning signs, costs - or how to use the app.";
            log.add(new String[]{"assistant", hello});
        }
        for (String[] m : log) addBubble(m[1], m[0].equals("user"));
    }

    void focusInput() { input.requestFocusInWindow(); }

    private void send(String text) {
        if (typing != null) return;
        log.add(new String[]{"user", text});
        addBubble(text, true);
        typing = addBubble("Thinking...", false);
        List<String[]> snapshot = new ArrayList<>(log);
        new Thread(() -> {
            String answer;
            try { answer = engine.reply(snapshot); } catch (Exception e) { answer = "Sorry, something went wrong. Please try again."; }
            final String a = answer;
            SwingUtilities.invokeLater(() -> {
                list.remove(typing); typing = null;
                log.add(new String[]{"assistant", a});
                addBubble(a, false);
            });
        }, "chat-reply").start();
    }

    private JComponent addBubble(String text, boolean mine) {
        Bubble b = new Bubble(text, mine);
        JPanel row = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 5)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        row.setOpaque(false);
        row.add(b);
        list.add(row);
        list.revalidate(); list.repaint();
        SwingUtilities.invokeLater(() -> sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum()));
        return row;
    }

    private static class Bubble extends JComponent {
        private final List<String> lines; private final boolean mine; private final int w, h;
        Bubble(String text, boolean mine) {
            this.mine = mine;
            Font f = Theme.font(Font.PLAIN, 13);
            FontMetrics fm = getFontMetrics(f);
            lines = UI.wrap(text, fm, BUBBLE_W);
            int widest = 0;
            for (String l : lines) widest = Math.max(widest, fm.stringWidth(l));
            w = widest + 28; h = lines.size() * (fm.getHeight() + 2) + 22;
            setFont(f);
            setPreferredSize(new Dimension(w, h));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UI.aa(g2);
            g2.setColor(mine ? Theme.primary : Theme.dark ? Theme.hover : new Color(238, 242, 250));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.setColor(mine ? Color.WHITE : Theme.text);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int y = 11 + fm.getAscent();
            for (String l : lines) { g2.drawString(l, 14, y); y += fm.getHeight() + 2; }
            g2.dispose();
        }
    }
}
