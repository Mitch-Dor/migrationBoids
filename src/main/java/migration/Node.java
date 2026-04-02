package migration;

public class Node {
    public final double x, y;
    public final double safeRadius;
    public final String name;
    public boolean[] boosted; // Boolean that is true if the node has boosted the population already (resets each time the objective node changes) and false if the node has not yet boosted

    public Node(double x, double y, double safeRadius, String name) {
        this.x = x;
        this.y = y;
        this.safeRadius = safeRadius;
        this.name = name;
        this.boosted = new boolean[2];
        this.boosted[0] = false;
        this.boosted[1] = false;
    }

    public double distanceTo(double ox, double oy) {
        double dx = x - ox, dy = y - oy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public boolean contains(double ox, double oy) {
        return distanceTo(ox, oy) <= safeRadius;
    }
}
