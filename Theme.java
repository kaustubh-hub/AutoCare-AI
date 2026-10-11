package ai;

import java.awt.*;

/** Central colour/font palette. Call Theme.apply(true/false) then rebuild the UI to switch light/dark. */
final class Theme {
    static boolean dark = false;
    static Color bg, card, sidebar, text, dim, border, input, hover;
    static Color primary, primaryDark, accent, success, warn, danger;

    static { apply(false); }

    static void apply(boolean d) {
        dark = d;
        if (d) {
            bg = new Color(11, 16, 28);   card = new Color(20, 28, 46);  text = new Color(232, 238, 250);
            dim = new Color(143, 157, 184); border = new Color(40, 52, 78); input = new Color(13, 20, 36);
            hover = new Color(30, 41, 66);
        } else {
            bg = new Color(243, 246, 252); card = Color.WHITE;          text = new Color(15, 23, 42);
            dim = new Color(100, 116, 139); border = new Color(226, 232, 240); input = new Color(248, 250, 252);
            hover = new Color(238, 243, 255);
        }
        sidebar = new Color(10, 16, 32);
        primary = new Color(59, 130, 246); primaryDark = new Color(37, 99, 235);
        accent = new Color(139, 92, 246);  success = new Color(34, 197, 94);
        warn = new Color(245, 158, 11);    danger = new Color(239, 68, 68);
    }

    static Font font(int style, int size) { return new Font("Segoe UI", style, size); }
    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }
}
