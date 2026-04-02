package migration;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

public class MigrationPanel extends JPanel {

    private final MigrationSim sim;

    // Logical world size
    private static final int WORLD_W = 1600;
    private static final int WORLD_H = 900;

    // Colors
    private static final Color BG           = new Color(15, 20, 12);
    private static final Color TERRAIN      = new Color(30, 45, 25);
    private static final Color OBSTACLE_COL = new Color(70, 55, 40);
    private static final Color OBSTACLE_BRD = new Color(100, 80, 55);

    private static final Color NODE_ACTIVE_FILL   = new Color(255, 220, 80, 40);
    private static final Color NODE_ACTIVE_BORDER = new Color(255, 220, 80, 160);
    private static final Color NODE_INACTIVE_FILL  = new Color(80, 130, 200, 25);
    private static final Color NODE_INACTIVE_BORDER= new Color(80, 130, 200, 80);

    // Group 0 = amber/orange, Group 1 = teal/blue
    private static final Color[] GROUP_COLOR  = {new Color(240, 160, 50), new Color(70, 190, 200)};
    private static final Color[] LEADER_COLOR = {new Color(255, 220, 60), new Color(120, 240, 255)};
    private static final Color[] RADIUS_FILL  = {new Color(255, 220, 60, 18), new Color(120, 240, 255, 18)};
    private static final Color[] RADIUS_EDGE  = {new Color(255, 220, 60, 60), new Color(120, 240, 255, 60)};

    private static final Color SAFE_FILL   = new Color(100, 220, 100, 20);
    private static final Color SAFE_BORDER = new Color(100, 220, 100, 80);

    private static final Color HUD_BG   = new Color(0, 0, 0, 140);
    private static final Color HUD_TEXT = new Color(220, 220, 200);

    private double scale = 1.0;
    private int    offX  = 0;
    private int    offY  = 0;

    public MigrationPanel(MigrationSim sim) {
        this.sim = sim;
        setPreferredSize(new Dimension(1200, 675));
        setBackground(BG);
    }

    // Compute the scale and offset to fit the world into the panel
    private void computeTransform() {
        double sx = (double) getWidth()  / WORLD_W;
        double sy = (double) getHeight() / WORLD_H;
        scale = Math.min(sx, sy);
        offX  = (int) ((getWidth()  - WORLD_W * scale) / 2);
        offY  = (int) ((getHeight() - WORLD_H * scale) / 2);
    }

    private int wx(double worldX) { return (int) (offX + worldX * scale); }
    private int wy(double worldY) { return (int) (offY + worldY * scale); }
    private int ws(double worldSize) { return (int) (worldSize * scale); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        computeTransform();
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g2.setColor(BG);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // World terrain background
        g2.setColor(TERRAIN);
        g2.fillRect(offX, offY, ws(WORLD_W), ws(WORLD_H));

        drawObstacles(g2);
        drawNodes(g2);
        drawLeaderRadii(g2);
        drawBoids(g2);
        drawHUD(g2);
    }

    private void drawObstacles(Graphics2D g2) {
        for (java.awt.geom.Rectangle2D obs : sim.getObstacles()) {
            int rx = wx(obs.getX()), ry = wy(obs.getY());
            int rw = ws(obs.getWidth()), rh = ws(obs.getHeight());
            g2.setColor(OBSTACLE_COL);
            g2.fillRoundRect(rx, ry, rw, rh, 4, 4);
            g2.setColor(OBSTACLE_BRD);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(rx, ry, rw, rh, 4, 4);
        }
    }

    private void drawNodes(Graphics2D g2) {
        Node[] nodes = sim.getNodes();
        int active = sim.getActiveNode();
        for (int i = 0; i < nodes.length; i++) {
            Node n = nodes[i];
            boolean isActive = (i == active);

            // Safe zone (larger translucent)
            double r = n.safeRadius;
            g2.setColor(SAFE_FILL);
            g2.fill(new Ellipse2D.Double(wx(n.x - r), wy(n.y - r), ws(r * 2), ws(r * 2)));
            g2.setColor(SAFE_BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Double(wx(n.x - r), wy(n.y - r), ws(r * 2), ws(r * 2)));

            // Node marker
            double mr = 14;
            Color fill   = isActive ? NODE_ACTIVE_FILL   : NODE_INACTIVE_FILL;
            Color border = isActive ? NODE_ACTIVE_BORDER : NODE_INACTIVE_BORDER;
            g2.setColor(fill);
            g2.fill(new Ellipse2D.Double(wx(n.x - mr), wy(n.y - mr), ws(mr * 2), ws(mr * 2)));
            g2.setColor(border);
            float[] dash = isActive ? null : new float[]{4f, 4f};
            g2.setStroke(dash == null
                ? new BasicStroke(1.8f)
                : new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.draw(new Ellipse2D.Double(wx(n.x - mr), wy(n.y - mr), ws(mr * 2), ws(mr * 2)));
            g2.setStroke(new BasicStroke(1f));

            // Label
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(isActive ? new Color(255, 220, 80) : new Color(120, 160, 220));
            String label = (isActive ? "▶ " : "") + n.name;
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, wx(n.x) - fm.stringWidth(label) / 2, wy(n.y) + ws(mr) + 14);
        }
    }

    private void drawLeaderRadii(Graphics2D g2) {
        int[] leaders = {sim.getLeader0(), sim.getLeader1()};
        List<Boid> boids = sim.getBoids();
        double lr = sim.getLeaderRadius();
        float[] dash = {5f, 4f};

        for (int i = 0; i < 2; i++) {
            int li = leaders[i];
            if (li < 0 || li >= boids.size()) continue;
            Boid leader = boids.get(li);

            g2.setColor(RADIUS_FILL[i]);
            g2.fill(new Ellipse2D.Double(wx(leader.x - lr), wy(leader.y - lr), ws(lr * 2), ws(lr * 2)));
            g2.setColor(RADIUS_EDGE[i]);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.draw(new Ellipse2D.Double(wx(leader.x - lr), wy(leader.y - lr), ws(lr * 2), ws(lr * 2)));
            g2.setStroke(new BasicStroke(1f));
        }
    }

    private void drawBoids(Graphics2D g2) {
        int[] leaders = {sim.getLeader0(), sim.getLeader1()};
        List<Boid> boids = sim.getBoids();

        for (int i = 0; i < boids.size(); i++) {
            Boid b = boids.get(i);
            boolean isLeader = b.isLeader;
            Color col  = isLeader ? LEADER_COLOR[b.groupId] : GROUP_COLOR[b.groupId];
            double size = isLeader ? 5.0 : 3.0;
            drawBoidShape(g2, b, col, size);
        }
    }

    private void drawBoidShape(Graphics2D g2, Boid b, Color color, double size) {
        double angle = Math.atan2(b.vy, b.vx);
        double tip   = size * 1.8 * scale;
        double back  = -size * scale;
        double wing  = size * 0.7 * scale;
        double mid   = -size * 0.4 * scale;

        Path2D.Double shape = new Path2D.Double();
        shape.moveTo(tip, 0);
        shape.lineTo(back, -wing);
        shape.lineTo(mid, 0);
        shape.lineTo(back, wing);
        shape.closePath();

        AffineTransform at = new AffineTransform();
        at.translate(wx(b.x), wy(b.y));
        at.rotate(angle);
        shape.transform(at);

        g2.setColor(color);
        g2.fill(shape);
    }

    private void drawHUD(Graphics2D g2) {
        Node[] nodes = sim.getNodes();
        int active = sim.getActiveNode();
        double timer = sim.getNodeTimer();
        double interval = sim.getNodeSwitchInterval();
        List<Boid> boids = sim.getBoids();

        int g0total = 0, g1total = 0;
        for (Boid b : boids) {
            if (b.groupId == 0) g0total++;
            else                g1total++;
        }

        // Build HUD text
        String[] lines = {
            "Migration Sim",
            "─────────────────",
            String.format("Destination: %s", nodes[active].name),
            String.format("Switch in: %.0fs", Math.max(0, interval - timer)),
            "─────────────────",
            String.format("Group A (amber): %d boids", g0total),
            String.format("Group B (teal):  %d boids", g1total),
        };

        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        FontMetrics fm = g2.getFontMetrics();
        int lineH = fm.getHeight();
        int pad = 8;
        int boxW = 200;
        int boxH = lines.length * lineH + pad * 2;
        int bx = offX + 10, by = offY + 10;

        g2.setColor(HUD_BG);
        g2.fillRoundRect(bx, by, boxW, boxH, 6, 6);

        g2.setColor(HUD_TEXT);
        for (int i = 0; i < lines.length; i++) {
            g2.drawString(lines[i], bx + pad, by + pad + (i + 1) * lineH - 3);
        }

        // Timer bar
        double frac = timer / interval;
        int barY = by + boxH + 4;
        int barW = boxW;
        g2.setColor(new Color(50, 50, 50, 160));
        g2.fillRoundRect(bx, barY, barW, 6, 3, 3);
        g2.setColor(new Color(255, 220, 80, 200));
        g2.fillRoundRect(bx, barY, (int)(barW * frac), 6, 3, 3);
    }
}
