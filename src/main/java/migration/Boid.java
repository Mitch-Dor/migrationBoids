package migration;

import java.awt.geom.Rectangle2D;
import java.util.List;

public class Boid {

    public double x, y;
    public double vx, vy;
    public boolean isLeader;
    public int groupId; // 0 or 1 — which leader group this boid belongs to

    private static final double BASE_SPEED = 1.2;

    public Boid(double x, double y, boolean isLeader, int groupId) {
        this.x = x;
        this.y = y;
        this.isLeader = isLeader;
        this.groupId = groupId;
        double angle = Math.random() * Math.PI * 2;
        this.vx = Math.cos(angle) * BASE_SPEED;
        this.vy = Math.sin(angle) * BASE_SPEED;
    }

    public double distanceTo(double ox, double oy) {
        double dx = x - ox, dy = y - oy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceTo(Boid other) {
        return distanceTo(other.x, other.y);
    }

    public void steerToward(double tx, double ty, double strength) {
        double dx = tx - x, dy = ty - y;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 0.001) return;
        vx += (dx / d) * strength;
        vy += (dy / d) * strength;
    }

    public void applySeparation(List<Boid> others, double minDist, double strength) {
        for (Boid o : others) {
            if (o == this) continue;
            double dx = x - o.x, dy = y - o.y;
            double d2 = dx * dx + dy * dy;
            if (d2 < minDist * minDist && d2 > 0) {
                double d = Math.sqrt(d2);
                double force = strength * (1.0 - d / minDist);
                vx += (dx / d) * force;
                vy += (dy / d) * force;
            }
        }
    }

    // Push boid away from rectangular obstacles
    public void avoidObstacles(List<Rectangle2D> obstacles, double margin, double strength) {
        for (Rectangle2D obs : obstacles) {
            // Find closest point on rect to this boid
            double cx = Math.max(obs.getMinX(), Math.min(x, obs.getMaxX()));
            double cy = Math.max(obs.getMinY(), Math.min(y, obs.getMaxY()));
            double dx = x - cx, dy = y - cy;
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < margin && d > 0.001) {
                double force = strength * (1.0 - d / margin);
                vx += (dx / d) * force;
                vy += (dy / d) * force;
            }
        }
    }

    // Resolve hard collision: push boid outside obstacle
    public void resolveObstacleCollision(List<Rectangle2D> obstacles) {
        for (Rectangle2D obs : obstacles) {
            if (obs.contains(x, y)) {
                // Push out via shortest axis
                double overlapLeft   = x - obs.getMinX();
                double overlapRight  = obs.getMaxX() - x;
                double overlapTop    = y - obs.getMinY();
                double overlapBottom = obs.getMaxY() - y;
                double min = Math.min(Math.min(overlapLeft, overlapRight),
                                      Math.min(overlapTop, overlapBottom));
                if (min == overlapLeft)       { x = obs.getMinX() - 1; vx = Math.abs(vx) * -1; }
                else if (min == overlapRight) { x = obs.getMaxX() + 1; vx = Math.abs(vx); }
                else if (min == overlapTop)   { y = obs.getMinY() - 1; vy = Math.abs(vy) * -1; }
                else                          { y = obs.getMaxY() + 1; vy = Math.abs(vy); }
            }
        }
    }

    public void applyEdgeTurning(int width, int height, int margin, double strength) {
        if (x < margin)            vx += strength;
        if (x > width - margin)    vx -= strength;
        if (y < margin)            vy += strength;
        if (y > height - margin)   vy -= strength;
    }

    public void clampSpeed(double maxSpeed) {
        double s = Math.sqrt(vx * vx + vy * vy);
        if (s == 0) return;
        double minSpeed = maxSpeed * 0.4;
        if (s > maxSpeed) { vx = vx / s * maxSpeed; vy = vy / s * maxSpeed; }
        if (s < minSpeed) { vx = vx / s * minSpeed; vy = vy / s * minSpeed; }
    }

    public void move() {
        x += vx;
        y += vy;
    }
}
