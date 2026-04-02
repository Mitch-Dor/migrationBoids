package migration;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MigrationSim {

    // World dimensions (logical, panel maps to these)
    private int width  = 1600;
    private int height = 900;

    private final List<Boid>         boids     = new ArrayList<>();
    private final List<Rectangle2D>  obstacles = new ArrayList<>();
    private final Node[]             nodes     = new Node[2];
    private final Random             rng       = new Random();

    private int    activeNode      = 0;   // which node boids are heading to
    private double nodeTimer       = 0;   // seconds since last switch
    private static final double NODE_SWITCH_INTERVAL = 25.0;

    // Per-group leader indices into boids list (or -1)
    private int leader0 = -1;
    private int leader1 = -1;

    private static final double LEADER_RADIUS   = 120.0;
    private static final double SAFE_RADIUS     = 110.0;
    private static final double BASE_SPEED      = 1.2;
    private double speedMult = 1.0;
    private static final double SEPARATION_DIST = 14.0;
    private static final int    EDGE_MARGIN     = 60;
    private static final double EDGE_TURN       = 0.18;

    // Death chance per second
    private static final double DEATH_OUTSIDE   = 0.15;
    private static final double DEATH_INSIDE    = 0.02;

    private static final int INITIAL_BOIDS = 60;

    // Accumulates fractional death probability between ticks
    private double deathAccum = 0;

    public MigrationSim() {
        buildNodes();
        buildObstacles();
        spawnInitialBoids();
    }

    private void buildNodes() {
        // Nodes in opposite corners, well inset
        nodes[0] = new Node(200,          height / 2.0, SAFE_RADIUS, "Winter Grounds");
        nodes[1] = new Node(width - 200,  height / 2.0, SAFE_RADIUS, "Summer Grounds");
    }

    private void buildObstacles() {
        // A few large rectangular obstacles scattered across the middle of the map
        // Format: x, y, w, h  (top-left origin)
        obstacles.add(new Rectangle2D.Double(500,  100, 80, 320));
        obstacles.add(new Rectangle2D.Double(750,  420, 80, 280));
        obstacles.add(new Rectangle2D.Double(950,  80,  80, 300));
        obstacles.add(new Rectangle2D.Double(1150, 350, 80, 350));
        obstacles.add(new Rectangle2D.Double(620,  640, 300, 70));
        obstacles.add(new Rectangle2D.Double(1050, 580, 260, 70));
    }

    private void spawnInitialBoids() {
        boids.clear();
        leader0 = -1;
        leader1 = -1;

        // Spawn group 0 near node 0, group 1 near node 1
        for (int i = 0; i < INITIAL_BOIDS; i++) {
            int group = (i < INITIAL_BOIDS / 2) ? 0 : 1;
            Node home = nodes[group];
            double angle = Math.random() * Math.PI * 2;
            double r     = Math.random() * (SAFE_RADIUS - 10) + 5;
            double bx    = home.x + Math.cos(angle) * r;
            double by    = home.y + Math.sin(angle) * r;
            boids.add(new Boid(bx, by, false, group));
        }

        // Assign leaders
        assignNewLeader(0);
        assignNewLeader(1);
    }

    public void assignNewLeader(int group) {
        // Find a random non-leader boid in this group
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < boids.size(); i++) {
            if (boids.get(i).groupId == group && !boids.get(i).isLeader) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) return;
        int idx = candidates.get(rng.nextInt(candidates.size()));
        boids.get(idx).isLeader = true;
        if (group == 0) leader0 = idx;
        else            leader1 = idx;
    }

    // lx and ly are old leader's coordinates
    public void assignNewLeader(int group, double lx, double ly) {
        List<Integer> inRadius = new ArrayList<>();
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < boids.size(); i++) {
            Boid b = boids.get(i);
            if (b.groupId == group && !b.isLeader) {
                all.add(i);
                double dx = b.x - lx, dy = b.y - ly;
                if (Math.sqrt(dx * dx + dy * dy) <= LEADER_RADIUS) {
                    inRadius.add(i);
                }
            }
        }
        List<Integer> pool = inRadius.isEmpty() ? all : inRadius;
        if (pool.isEmpty()) return;
        // Pick closest from pool to old leader position
        int bestIdx = -1;
        double bestDist = Double.MAX_VALUE;
        for (int idx : pool) {
            Boid b = boids.get(idx);
            double dx = b.x - lx, dy = b.y - ly;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < bestDist) { bestDist = dist; bestIdx = idx; }
        }
        if (bestIdx < 0) return;
        boids.get(bestIdx).isLeader = true;
        if (group == 0) leader0 = bestIdx;
        else            leader1 = bestIdx;
    }

    // Called after boid list is modified to fix leader indices
    public void reindexLeaders() {
        leader0 = -1;
        leader1 = -1;
        for (int i = 0; i < boids.size(); i++) {
            if (boids.get(i).isLeader) {
                if (boids.get(i).groupId == 0) leader0 = i;
                else                            leader1 = i;
            }
        }
    }

    public void setSize(int w, int h) {
        this.width  = w;
        this.height = h;
    }

    public int getWidth()  { return width; }
    public int getHeight() { return height; }

    public void restart() {
        activeNode = 0;
        nodeTimer  = 0;
        spawnInitialBoids();
    }

    public void forceSwitch() {
        activeNode = 1 - activeNode;
        nodeTimer  = 0;
        for (Node n : nodes) { n.boosted[0] = false; n.boosted[1] = false; }
    }

    public void setSpeedMult(double mult) {
        this.speedMult = mult;
    }

    public List<Boid>        getBoids()     { return boids; }
    public List<Rectangle2D> getObstacles() { return obstacles; }
    public Node[]            getNodes()     { return nodes; }
    public int               getActiveNode(){ return activeNode; }
    public double            getNodeTimer() { return nodeTimer; }
    public double            getNodeSwitchInterval() { return NODE_SWITCH_INTERVAL; }
    public double            getLeaderRadius() { return LEADER_RADIUS; }
    public int               getLeader0()   { return leader0; }
    public int               getLeader1()   { return leader1; }

    public int countGroup(int g) {
        int c = 0; for (Boid b : boids) if (b.groupId == g) c++; return c;
    }

    // Main update, called each frame. dt = seconds elapsed since last frame
    public void step(double dt) {
        nodeTimer += dt;
        if (nodeTimer >= NODE_SWITCH_INTERVAL) {
            activeNode = 1 - activeNode;
            nodeTimer  = 0;
            nodes[activeNode].boosted[0] = false;
            nodes[activeNode].boosted[1] = false;
        }

        Node target = nodes[activeNode];
        double spd = BASE_SPEED * speedMult;

        // --- Move boids ---
        for (Boid b : boids) {
            // Fix 4: Boids inside active safe node get strong centering pull and no separation
            if (target.contains(b.x, b.y)) {
                // Find which node they're in and pull toward its center
                b.steerToward(target.x, target.y, 0.4);
                b.clampSpeed(spd * 0.3);
                b.move();
                b.resolveObstacleCollision(obstacles);
                continue;
        }

        if (b.isLeader) {
            b.steerToward(target.x, target.y, 0.15 * speedMult);
            b.applySeparation(boids, SEPARATION_DIST, 0.45 * speedMult);
            b.avoidObstacles(obstacles, 50, 0.6 * speedMult);
            b.applyEdgeTurning(width, height, EDGE_MARGIN, EDGE_TURN * speedMult);
            b.clampSpeed(spd * 1.1);
        } else {
            int li = (b.groupId == 0) ? leader0 : leader1;
            if (li >= 0 && li < boids.size()) {
                Boid leader = boids.get(li);
                double d = b.distanceTo(leader);
                double effectiveRadius = LEADER_RADIUS * speedMult;
                if (d < effectiveRadius) {
                    b.steerToward(leader.x, leader.y, 0.10 * speedMult);
                }
            }
            double distToTarget = b.distanceTo(target.x, target.y);
            if (distToTarget > SAFE_RADIUS) {
                b.steerToward(target.x, target.y, 0.03 * speedMult);
            }
            b.applySeparation(boids, SEPARATION_DIST, 0.45 * speedMult);
            b.avoidObstacles(obstacles, 50, 0.6 * speedMult);
            b.applyEdgeTurning(width, height, EDGE_MARGIN, EDGE_TURN * speedMult);
            b.clampSpeed(spd);
        }
        b.move();
        b.resolveObstacleCollision(obstacles);
    }

        // --- Death mechanic ---
        deathAccum += dt;
        if (deathAccum >= 1.0) {
            deathAccum -= 1.0;
            applyDeaths(target);
        }

        // --- Population boost: 80% at target node → multiply by 1.5 ---
        checkPopulationBoost(target);

        // --- Re-index leaders in case list changed ---
        reindexLeaders();

        // Ensure each group has a leader
        if (leader0 < 0 && countGroup(0) > 0) assignNewLeader(0);
        if (leader1 < 0 && countGroup(1) > 0) assignNewLeader(1);
    }

    private void applyDeaths(Node target) {
        double oldLeader0x = -1, oldLeader0y = -1;
        double oldLeader1x = -1, oldLeader1y = -1;
        boolean leader0Died = false, leader1Died = false;

        List<Boid> toRemove = new ArrayList<>();
        for (Boid b : boids) {
            if (isAtSafeNode(b)) continue;
            boolean nearLeader = isNearOwnLeader(b);
            double deathChance = nearLeader ? DEATH_INSIDE : DEATH_OUTSIDE;
            if (Math.random() < deathChance) toRemove.add(b);
        }
        for (Boid b : toRemove) {
            if (b.isLeader) {
                if (b.groupId == 0) { oldLeader0x = b.x; oldLeader0y = b.y; leader0Died = true; }
                else                { oldLeader1x = b.x; oldLeader1y = b.y; leader1Died = true; }
                b.isLeader = false;
            }
        }
        boids.removeAll(toRemove);
        if (leader0Died && countGroup(0) > 0) assignNewLeader(0, oldLeader0x, oldLeader0y);
        if (leader1Died && countGroup(1) > 0) assignNewLeader(1, oldLeader1x, oldLeader1y);
    }

    private boolean isAtSafeNode(Boid b) {
        for (Node n : nodes) {
            if (n.contains(b.x, b.y)) return true;
        }
        return false;
    }

    private boolean isNearOwnLeader(Boid b) {
        int li = (b.groupId == 0) ? leader0 : leader1;
        if (li < 0 || li >= boids.size()) return false;
        return b.distanceTo(boids.get(li)) < LEADER_RADIUS;
    }

    public void checkPopulationBoost(Node target) {
        int[] total  = {0, 0};
        int[] atNode = {0, 0};
        for (Boid b : boids) {
            total[b.groupId]++;
            if (target.contains(b.x, b.y)) atNode[b.groupId]++;
        }
        for (int g = 0; g < 2; g++) {
            if (total[g] == 0) continue;
            double frac = (double) atNode[g] / total[g];
            if (frac >= 0.80 && !target.boosted[g]) {
                target.boosted[g] = true;
                int toAdd = (int) Math.round(total[g] * 0.5);
                for (int i = 0; i < toAdd; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r     = Math.random() * (SAFE_RADIUS - 10) + 5;
                    double bx    = target.x + Math.cos(angle) * r;
                    double by    = target.y + Math.sin(angle) * r;
                    boids.add(new Boid(bx, by, false, g));
                }
            }
        }
    }
}
