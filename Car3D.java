package ai;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A real-time 3D car drawn with pure Java2D (no external libraries).
 * Drag to rotate, hover a part to see its health. "X-ray" mode recolours parts by condition.
 */
class Car3D extends JPanel {
    private static final class Face {
        final double[][] v; final String part; final double bias; final double[] n = new double[3];
        Face(double[][] v, String part, double bias) { this.v = v; this.part = part; this.bias = bias; }
    }
    private static final class Drawn { final Polygon poly; final String part; Drawn(Polygon p, String s) { poly = p; part = s; } }

    private final List<Face> faces = new ArrayList<>();
    private final List<Drawn> drawn = new ArrayList<>();
    private double yaw = -0.75, pitch = 0.26;
    boolean autoRotate = true, transparentBg = false;
    private boolean xray = false, dragging = false, electric = false;
    private int paintRgb = 0x3B82F6;
    private int[] m = {85, 85, 85, 85, 85};
    private int lastX, lastY;
    private String hoverText;
    private Point hoverPt;
    private Timer timer;

    Car3D() {
        setOpaque(false);
        setPreferredSize(new Dimension(420, 320));
        buildMesh();
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragging = true; lastX = e.getX(); lastY = e.getY(); setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); }
            @Override public void mouseReleased(MouseEvent e) { dragging = false; setCursor(Cursor.getDefaultCursor()); }
            @Override public void mouseDragged(MouseEvent e) {
                yaw += (e.getX() - lastX) * 0.012;
                pitch = Math.max(0.05, Math.min(0.85, pitch + (e.getY() - lastY) * 0.008));
                lastX = e.getX(); lastY = e.getY();
                hoverText = null; repaint();
            }
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getPoint()); }
            @Override public void mouseExited(MouseEvent e) { hoverText = null; repaint(); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // ------------------------------------------------------------------ public API
    void setState(int rgb, int oil, int brakes, int tyres, int battery, int coolant, boolean electric) {
        this.paintRgb = rgb; this.m = new int[]{oil, brakes, tyres, battery, coolant}; this.electric = electric; repaint();
    }
    void setVehicle(Vehicle v) { setState(v.colorRgb, v.oil, v.brakes, v.tyres, v.battery, v.coolant, v.isElectric()); }
    void setXray(boolean on) { xray = on; repaint(); }
    boolean isXray() { return xray; }

    @Override public void addNotify() {
        super.addNotify();
        if (timer == null) timer = new Timer(33, e -> { if (autoRotate && !dragging && hoverText == null) { yaw += 0.011; repaint(); } });
        timer.start();
    }
    @Override public void removeNotify() { if (timer != null) timer.stop(); super.removeNotify(); }

    // ------------------------------------------------------------------ mesh
    private void buildMesh() {
        // lower body, hood, trunk, cabin (glass on its four sides), bumpers, lights
        box("body", -2.1, 2.1, 0.30, 0.82, -0.90, 0.90);
        hex(new String[]{"engine", "engine", "engine", "engine", "engine", "engine"},
                new double[][]{{0.75, 0.82, -0.88}, {2.10, 0.82, -0.88}, {2.10, 0.82, 0.88}, {0.75, 0.82, 0.88}},
                new double[][]{{0.75, 0.98, -0.88}, {2.10, 0.88, -0.88}, {2.10, 0.88, 0.88}, {0.75, 0.98, 0.88}});
        hex(new String[]{"body", "body", "body", "body", "body", "body"},
                new double[][]{{-2.10, 0.82, -0.88}, {-1.10, 0.82, -0.88}, {-1.10, 0.82, 0.88}, {-2.10, 0.82, 0.88}},
                new double[][]{{-2.10, 0.92, -0.88}, {-1.10, 0.96, -0.88}, {-1.10, 0.96, 0.88}, {-2.10, 0.92, 0.88}});
        hex(new String[]{"body", "body", "glass", "glass", "glass", "glass"},
                new double[][]{{-1.25, 0.82, -0.82}, {0.85, 0.82, -0.82}, {0.85, 0.82, 0.82}, {-1.25, 0.82, 0.82}},
                new double[][]{{-0.80, 1.40, -0.68}, {0.20, 1.40, -0.68}, {0.20, 1.40, 0.68}, {-0.80, 1.40, 0.68}});
        box("trim", 2.02, 2.16, 0.32, 0.52, -0.86, 0.86);
        box("trim", -2.16, -2.02, 0.32, 0.52, -0.86, 0.86);
        box("light", 2.08, 2.14, 0.60, 0.74, 0.50, 0.84);
        box("light", 2.08, 2.14, 0.60, 0.74, -0.84, -0.50);
        box("tail", -2.14, -2.08, 0.62, 0.76, 0.50, 0.84);
        box("tail", -2.14, -2.08, 0.62, 0.76, -0.84, -0.50);
        for (double x : new double[]{1.35, -1.35})
            for (double z : new double[]{0.78, -0.78}) {
                cylinder("tyre", x, 0.36, z, 0.36, 0.17, 16, 0.4);
                cylinder("brake", x, 0.36, z, 0.20, 0.19, 12, 0.55);
            }
    }

    private void poly(String part, double bias, double[][] verts, double[] ref) {
        Face f = new Face(verts, part, bias);
        double[] a = verts[0], b = verts[1], c = verts[2];
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len == 0) return;
        nx /= len; ny /= len; nz /= len;
        double cx = 0, cy = 0, cz = 0;
        for (double[] p : verts) { cx += p[0]; cy += p[1]; cz += p[2]; }
        cx /= verts.length; cy /= verts.length; cz /= verts.length;
        if (nx * (cx - ref[0]) + ny * (cy - ref[1]) + nz * (cz - ref[2]) < 0) { nx = -nx; ny = -ny; nz = -nz; }
        f.n[0] = nx; f.n[1] = ny; f.n[2] = nz;
        faces.add(f);
    }

    private static double[] centroid(double[][]... groups) {
        double[] c = new double[3]; int n = 0;
        for (double[][] g : groups) for (double[] p : g) { c[0] += p[0]; c[1] += p[1]; c[2] += p[2]; n++; }
        c[0] /= n; c[1] /= n; c[2] /= n;
        return c;
    }

    /** Convex 6-sided solid: parts order = bottom, top, then the four sides. */
    private void hex(String[] parts, double[][] b, double[][] t) {
        double[] ref = centroid(b, t);
        poly(parts[0], 0, new double[][]{b[0], b[1], b[2], b[3]}, ref);
        poly(parts[1], 0, new double[][]{t[0], t[1], t[2], t[3]}, ref);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            poly(parts[2 + i], 0, new double[][]{b[i], b[j], t[j], t[i]}, ref);
        }
    }
    private void box(String part, double x0, double x1, double y0, double y1, double z0, double z1) {
        hex(new String[]{part, part, part, part, part, part},
                new double[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}});
    }
    /** Cylinder whose axis runs along Z (a wheel). The outward-facing cap gets a depth bias so it draws over the body. */
    private void cylinder(String part, double cx, double cy, double cz, double r, double hw, int sides, double outerBias) {
        double[][] front = new double[sides][], back = new double[sides][];
        for (int i = 0; i < sides; i++) {
            double a = 2 * Math.PI * i / sides;
            front[i] = new double[]{cx + r * Math.cos(a), cy + r * Math.sin(a), cz + hw};
            back[i] = new double[]{cx + r * Math.cos(a), cy + r * Math.sin(a), cz - hw};
        }
        double[] ref = {cx, cy, cz};
        boolean outerIsFront = cz > 0;
        poly(part, outerIsFront ? outerBias : 0, front, ref);
        poly(part, outerIsFront ? 0 : outerBias, back, ref);
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            poly(part, 0, new double[][]{front[i], front[j], back[j], back[i]}, ref);
        }
    }

    // ------------------------------------------------------------------ colours / labels
    private Color partColor(String part) {
        if (xray) {
            switch (part) {
                case "engine": return Health.color((m[0] + m[4]) / 2);
                case "brake": return Health.color(m[1]);
                case "tyre": return Health.color(m[2]).darker();
                case "light": case "tail": return Health.color(m[3]);
                case "glass": return new Color(48, 66, 98);
                default: return Theme.dark ? new Color(84, 98, 128) : new Color(120, 136, 168);
            }
        }
        switch (part) {
            case "engine": case "body": return new Color(paintRgb);
            case "glass": return new Color(26, 38, 54);
            case "tyre": return new Color(34, 34, 40);
            case "brake": return new Color(176, 182, 194);
            case "light": return new Color(255, 244, 205);
            case "tail": return new Color(210, 36, 48);
            default: return new Color(42, 46, 54);
        }
    }
    private String partText(String part) {
        switch (part) {
            case "engine": return (electric ? "Motor & cooling - " : "Engine & coolant - ") + (m[0] + m[4]) / 2 + "%";
            case "brake": return "Brakes - " + m[1] + "%";
            case "tyre": return "Tyres - " + m[2] + "%";
            case "light": case "tail": return "Battery & lights - " + m[3] + "%";
            case "glass": return "Cabin glass";
            case "trim": return "Bumpers";
            default: return "Body & chassis";
        }
    }

    private void updateHover(Point p) {
        String found = null;
        for (int i = drawn.size() - 1; i >= 0; i--) if (drawn.get(i).poly.contains(p)) { found = partText(drawn.get(i).part); break; }
        if (found == null ? hoverText != null : !found.equals(hoverText)) { hoverText = found; hoverPt = p; repaint(); }
        else if (found != null) { hoverPt = p; repaint(); }
    }

    // ------------------------------------------------------------------ rendering
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        UI.aa(g);
        int w = getWidth(), h = getHeight();
        if (!transparentBg) {
            g.setPaint(new GradientPaint(0, 0, Theme.dark ? new Color(24, 36, 62) : new Color(240, 246, 255),
                    0, h, Theme.dark ? new Color(12, 18, 32) : new Color(214, 226, 247)));
            g.fillRoundRect(0, 0, w, h, 18, 18);
        }
        double s = Math.min(0.17 * w, 0.33 * h), dist = 16.0, f = s * dist, cx = w / 2.0, cy = h * 0.56;
        double cy0 = Math.cos(yaw), sy0 = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);

        // floor: soft shadow + turntable ring
        Polygon shadow = new Polygon(), ring = new Polygon();
        for (int i = 0; i < 48; i++) {
            double a = 2 * Math.PI * i / 48;
            double[] p = tr(2.7 * Math.cos(a), -0.02, 1.5 * Math.sin(a), cy0, sy0, cp, sp);
            shadow.addPoint((int) (cx + p[0] * f / (p[2] + dist)), (int) (cy - p[1] * f / (p[2] + dist)));
            double[] q = tr(3.3 * Math.cos(a), -0.02, 3.3 * Math.sin(a), cy0, sy0, cp, sp);
            ring.addPoint((int) (cx + q[0] * f / (q[2] + dist)), (int) (cy - q[1] * f / (q[2] + dist)));
        }
        g.setColor(Theme.alpha(Theme.primary, 22)); g.fillPolygon(ring);
        g.setColor(Theme.alpha(Theme.primary, 90)); g.setStroke(new BasicStroke(1.4f)); g.drawPolygon(ring);
        g.setColor(new Color(0, 0, 0, Theme.dark ? 110 : 55)); g.fillPolygon(shadow);

        // faces
        double[] light = {-0.35, 0.80, -0.55};
        double ll = Math.sqrt(light[0] * light[0] + light[1] * light[1] + light[2] * light[2]);
        List<double[]> order = new ArrayList<>(); // {faceIndex, depth}
        double[][][] proj = new double[faces.size()][][];
        double[][] normals = new double[faces.size()][];
        for (int i = 0; i < faces.size(); i++) {
            Face fc = faces.get(i);
            double[] nv = rot(fc.n[0], fc.n[1], fc.n[2], cy0, sy0, cp, sp);
            double[][] pts = new double[fc.v.length][];
            double dsum = 0, mx = 0, my = 0, mz = 0;
            for (int k = 0; k < fc.v.length; k++) {
                double[] p = tr(fc.v[k][0], fc.v[k][1], fc.v[k][2], cy0, sy0, cp, sp);
                pts[k] = p; dsum += p[2]; mx += p[0]; my += p[1]; mz += p[2];
            }
            int nn = fc.v.length;
            mx /= nn; my /= nn; mz /= nn;
            // back-face culling: normal must point toward the camera at (0,0,-dist)
            if (nv[0] * mx + nv[1] * my + nv[2] * (mz + dist) >= 0) continue;
            proj[i] = pts; normals[i] = nv;
            order.add(new double[]{i, dsum / nn - fc.bias});
        }
        order.sort((a, b) -> Double.compare(b[1], a[1]));
        drawn.clear();
        for (double[] o : order) {
            int i = (int) o[0];
            Face fc = faces.get(i);
            Polygon poly = new Polygon();
            for (double[] p : proj[i]) poly.addPoint((int) Math.round(cx + p[0] * f / (p[2] + dist)), (int) Math.round(cy - p[1] * f / (p[2] + dist)));
            double[] nv = normals[i];
            double diff = Math.max(0, (nv[0] * light[0] + nv[1] * light[1] + nv[2] * light[2]) / ll);
            double k = 0.48 + 0.62 * diff;
            Color base = partColor(fc.part);
            if (fc.part.equals("light") || fc.part.equals("tail")) k = Math.max(k, 0.95);
            g.setColor(new Color(clamp(base.getRed() * k), clamp(base.getGreen() * k), clamp(base.getBlue() * k)));
            g.fillPolygon(poly);
            g.setColor(new Color(0, 0, 0, 55)); g.setStroke(new BasicStroke(0.8f)); g.drawPolygon(poly);
            drawn.add(new Drawn(poly, fc.part));
        }

        if (!transparentBg) {
            g.setFont(Theme.font(Font.PLAIN, 11));
            g.setColor(Theme.alpha(Theme.dim, 200));
            g.drawString("Drag to rotate  |  Hover a part for its condition", 14, h - 12);
        }
        if (hoverText != null && hoverPt != null) {
            g.setFont(Theme.font(Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(hoverText) + 20, tx = Math.min(hoverPt.x + 14, w - tw - 6), ty = Math.max(hoverPt.y - 34, 6);
            g.setColor(new Color(15, 23, 42, 230)); g.fillRoundRect(tx, ty, tw, 26, 10, 10);
            g.setColor(Color.WHITE); g.drawString(hoverText, tx + 10, ty + 17);
        }
        g.dispose();
    }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, v)); }

    /** Model -> view space: centre car, yaw about Y, pitch about X. */
    private static double[] tr(double x, double y, double z, double cosY, double sinY, double cosP, double sinP) {
        return rot(x, y - 0.62, z, cosY, sinY, cosP, sinP);
    }
    private static double[] rot(double x, double y, double z, double cosY, double sinY, double cosP, double sinP) {
        double x1 = x * cosY + z * sinY, z1 = -x * sinY + z * cosY;
        double y2 = y * cosP + z1 * sinP, z2 = z1 * cosP - y * sinP;
        return new double[]{x1, y2, z2};
    }
}
