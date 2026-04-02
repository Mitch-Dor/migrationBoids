package migration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class F2PMigrationTest {

    // -------------------------------------------------------------------------
    // Test 1: checkPopulationBoost should only trigger once per node visit
    // -------------------------------------------------------------------------
    @Test
    void testPopulationBoostOnlyOnceFix() {
        MigrationSim sim = new MigrationSim();
        Node target = sim.getNodes()[sim.getActiveNode()];

        List<Boid> boids = sim.getBoids();
        boids.clear();
        int startCount = 20;
        for (int i = 0; i < startCount; i++) {
            boids.add(new Boid(target.x, target.y, false, 0));
        }

        // First call — should boost population
        sim.checkPopulationBoost(target);
        int afterFirst = (int) boids.stream().filter(b -> b.groupId == 0).count();

        // Second call — should not boost again
        sim.checkPopulationBoost(target);
        int afterSecond = (int) boids.stream().filter(b -> b.groupId == 0).count();

        assertTrue(afterFirst > startCount,
            "Population should have increased after first boost call");
        assertEquals(afterFirst, afterSecond,
            "Population should not increase on second boost call (boosted=" + afterFirst + ")");
    }

    // -------------------------------------------------------------------------
    // Test 2: When a leader dies, a boid that was inside its radius gets leader
    // -------------------------------------------------------------------------
    @Test
    void testLeaderReassignmentFromNearbyBoids() {
        int runs       = 5;
        int nearbyWins = 0;
        double leaderRadius = 120.0;

        for (int run = 0; run < runs; run++) {
            MigrationSim sim = new MigrationSim();
            List<Boid> boids = sim.getBoids();
            boids.clear();

            double lx = 800, ly = 450;
            Boid leader = new Boid(lx, ly, true, 0);
            boids.add(leader);

            Boid near1 = new Boid(lx + 30, ly,      false, 0);
            Boid near2 = new Boid(lx - 30, ly + 20, false, 0);
            boids.add(near1);
            boids.add(near2);

            for (int i = 0; i < 5; i++) {
                boids.add(new Boid(lx + leaderRadius * 2 + i * 30, ly, false, 0));
            }

            sim.reindexLeaders();
            boids.remove(leader);
            sim.reindexLeaders();
            sim.assignNewLeader(0, lx, ly);
            sim.reindexLeaders();

            int newLeaderIdx = sim.getLeader0();
            if (newLeaderIdx >= 0 && newLeaderIdx < boids.size()) {
                Boid newLeader = boids.get(newLeaderIdx);
                if (newLeader == near1 || newLeader == near2) {
                    nearbyWins++;
                }
            }
        }

        assertEquals(runs, nearbyWins,
            "Nearby boid should receive leadership in all runs (won " + nearbyWins + "/" + runs + ")");
    }

    // -------------------------------------------------------------------------
    // Test: Followers stay close to leader at high speed vs low speed
    //
    // Places a leader and followers inside its radius, runs the sim for a short
    // duration at low speed and then at high speed, and asserts that average
    // follower-to-leader distance doesn't grow disproportionately at high speed.
    // -------------------------------------------------------------------------
    @Test
    void testFollowersKeepUpAtHighSpeed() {
        double avgDistLow  = runAndMeasureAvgFollowerDist(1.0);
        double avgDistHigh = runAndMeasureAvgFollowerDist(5.0);
 
        double ratio = avgDistHigh / avgDistLow;
 
        assertTrue(ratio < 2.0,
            String.format(
                "Followers fell too far behind at high speed. " +
                "Avg dist low=%.2f high=%.2f ratio=%.2f (must be < 2.0)",
                avgDistLow, avgDistHigh, ratio));
    }
 
    private double runAndMeasureAvgFollowerDist(double speedMult) {
        MigrationSim sim = new MigrationSim();
        sim.setSpeedMult(speedMult);
 
        List<Boid> boids = sim.getBoids();
        boids.clear();
 
        // Place leader at center
        double lx = 800, ly = 450;
        Boid leader = new Boid(lx, ly, true, 0);
        boids.add(leader);
 
        // Place 10 followers inside the leader radius
        double leaderRadius = sim.getLeaderRadius();
        for (int i = 0; i < 10; i++) {
            double angle = (2 * Math.PI / 10) * i;
            double r = leaderRadius * 0.4;
            boids.add(new Boid(lx + Math.cos(angle) * r, ly + Math.sin(angle) * r, false, 0));
        }
 
        sim.reindexLeaders();
 
        // Run ~2 simulated seconds — short enough to avoid node switch at 25s
        for (int i = 0; i < 120; i++) sim.step(0.016);
 
        // Measure average distance from each follower to leader
        double leaderX = boids.get(0).x;
        double leaderY = boids.get(0).y;
        double total = 0;
        int count = 0;
        for (int i = 1; i < boids.size(); i++) {
            Boid b = boids.get(i);
            if (b.groupId == 0 && !b.isLeader) {
                total += b.distanceTo(leaderX, leaderY);
                count++;
            }
        }
 
        return count > 0 ? total / count : Double.MAX_VALUE;
    }

    // -------------------------------------------------------------------------
    // Test 4: Boids inside an ACTIVE node's safe area never leave
    //
    // Once a boid has reached the active node's safe area, it should be locked
    // in and never pushed out by separation or other forces.
    // -------------------------------------------------------------------------
    @Test
    void testBoidsStayInActiveSafeArea() {
        MigrationSim sim = new MigrationSim();
        Node activeNode = sim.getNodes()[sim.getActiveNode()];
 
        List<Boid> boids = sim.getBoids();
        boids.clear();
 
        // Pack ~30 boids into the active node's safe area
        int count = 30;
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = Math.random() * activeNode.safeRadius * 0.85;
            boids.add(new Boid(
                activeNode.x + Math.cos(angle) * r,
                activeNode.y + Math.sin(angle) * r,
                false, 0
            ));
        }
 
        sim.reindexLeaders();
 
        // Run ~2 simulated seconds
        // no boid should ever be pushed outside the safe area
        for (int i = 0; i < 120; i++) {
            sim.step(0.016);
 
            long outside = boids.stream()
                .filter(b -> !activeNode.contains(b.x, b.y))
                .count();
 
            assertEquals(0, outside,
                "At step " + i + ", " + outside + " boid(s) were pushed outside the active node's safe area");
        }
    }
}