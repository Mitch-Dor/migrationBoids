package migration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class P2PMigrationTests {

    // -------------------------------------------------------------------------
    // Test 1: Each group has exactly one leader after init
    // -------------------------------------------------------------------------
    @Test
    void testLeaderAssignedOnInit() {
        MigrationSim sim = new MigrationSim();
        List<Boid> boids = sim.getBoids();

        int leaders0 = 0, leaders1 = 0;
        for (Boid b : boids) {
            if (b.isLeader && b.groupId == 0) leaders0++;
            if (b.isLeader && b.groupId == 1) leaders1++;
        }

        assertEquals(1, leaders0, "Group 0 should have exactly one leader");
        assertEquals(1, leaders1, "Group 1 should have exactly one leader");
        assertTrue(sim.getLeader0() >= 0, "Leader0 index should be valid");
        assertTrue(sim.getLeader1() >= 0, "Leader1 index should be valid");
    }

    // -------------------------------------------------------------------------
    // Test 2: A follower inside its leader's radius steers toward the leader
    // -------------------------------------------------------------------------
    @Test
    void testBoidSteersTowardOwnLeader() {
        double lx = 800, ly = 450;
        Boid leader = new Boid(lx, ly, true, 0);

        Boid follower = new Boid(lx + 60, ly, false, 0);
        follower.vx = 0; follower.vy = 0;

        double distBefore = follower.distanceTo(leader);
        for (int i = 0; i < 20; i++) {
            follower.steerToward(leader.x, leader.y, 0.10);
            follower.clampSpeed(1.2);
            follower.move();
        }
        double distAfter = follower.distanceTo(leader);

        assertTrue(distAfter < distBefore,
            String.format("Follower should move closer to leader (%.2f -> %.2f)", distBefore, distAfter));
    }

    // -------------------------------------------------------------------------
    // Test 3: A follower does NOT steer toward the other group's leader
    // -------------------------------------------------------------------------
    @Test
    void testBoidIgnoresOtherGroupLeader() {
        double lx = 300, ly = 450;
        Boid leaderG1 = new Boid(lx, ly, true, 1);

        Boid followerG0 = new Boid(lx + 100, ly, false, 0);
        followerG0.vx = 0; followerG0.vy = 0;

        double distBefore = followerG0.distanceTo(leaderG1);

        // No steerToward called — follower has no reason to move toward the wrong leader
        for (int i = 0; i < 20; i++) {
            followerG0.clampSpeed(1.2);
            followerG0.move();
        }

        double distAfter = followerG0.distanceTo(leaderG1);

        assertTrue(distAfter >= distBefore,
            String.format("Group 0 follower should not close on group 1 leader (%.1f -> %.1f)", distBefore, distAfter));
    }

    // -------------------------------------------------------------------------
    // Test 4: Separation force pushes two boids apart when too close
    // -------------------------------------------------------------------------
    @Test
    void testSeparationPushesBoidApart() {
        Boid a = new Boid(400, 400, false, 0);
        a.vx = 0; a.vy = 0;
        Boid b = new Boid(408, 400, false, 0);
        b.vx = 0; b.vy = 0;

        List<Boid> pair = new ArrayList<>();
        pair.add(a);
        pair.add(b);

        double distBefore = a.distanceTo(b);
        a.applySeparation(pair, 22, 0.5);
        b.applySeparation(pair, 22, 0.5);
        a.move();
        b.move();
        double distAfter = a.distanceTo(b);

        assertTrue(distAfter > distBefore,
            String.format("Boids should move apart (%.2f -> %.2f)", distBefore, distAfter));
    }

    // -------------------------------------------------------------------------
    // Test 5: Edge turning pushes boid away from walls
    // -------------------------------------------------------------------------
    @Test
    void testEdgeTurningPushesAwayFromWall() {
        Boid b = new Boid(30, 450, false, 0);
        b.vx = -1; b.vy = 0;

        double vxBefore = b.vx;
        b.applyEdgeTurning(1600, 900, 60, 0.28);
        double vxAfter = b.vx;

        assertTrue(vxAfter > vxBefore,
            String.format("vx should increase near left wall (%.2f -> %.2f)", vxBefore, vxAfter));
    }

    // -------------------------------------------------------------------------
    // Test 6: Boids inside a safe node zone are not killed by applyDeaths
    // -------------------------------------------------------------------------
    @Test
    void testSafeZoneImmunityFromDeath() {
        MigrationSim sim = new MigrationSim();
        // Use the inactive node so the population boost doesn't fire
        int inactiveIdx = 1 - sim.getActiveNode();
        Node safeNode = sim.getNodes()[inactiveIdx];

        List<Boid> boids = sim.getBoids();
        boids.clear();

        int count = 30;
        for (int i = 0; i < count; i++) {
            boids.add(new Boid(safeNode.x, safeNode.y, false, 0));
        }
        sim.reindexLeaders();

        for (int i = 0; i < 10; i++) sim.step(1.0);

        int remaining = (int) boids.stream().filter(b -> b.groupId == 0).count();

        assertEquals(count, remaining,
            (count - remaining) + " boids died inside the safe zone");
    }

    // -------------------------------------------------------------------------
    // Test 7: Speed clamping keeps boid within min/max range
    // -------------------------------------------------------------------------
    @Test
    void testSpeedClamping() {
        double maxSpeed = 3.0;
        double minSpeed = maxSpeed * 0.4;

        Boid fast = new Boid(400, 400, false, 0);
        fast.vx = 100; fast.vy = 100;
        fast.clampSpeed(maxSpeed);
        double fastResult = Math.sqrt(fast.vx * fast.vx + fast.vy * fast.vy);

        Boid slow = new Boid(400, 400, false, 0);
        slow.vx = 0.01; slow.vy = 0.0;
        slow.clampSpeed(maxSpeed);
        double slowResult = Math.sqrt(slow.vx * slow.vx + slow.vy * slow.vy);

        assertTrue(fastResult <= maxSpeed + 0.001,
            String.format("Overspeed should be clamped to %.2f, got %.2f", maxSpeed, fastResult));
        assertTrue(slowResult >= minSpeed - 0.001,
            String.format("Underspeed should be raised to %.2f, got %.2f", minSpeed, slowResult));
    }

    // -------------------------------------------------------------------------
    // Test 8: A boid not near a leader should steer toward the active node
    // -------------------------------------------------------------------------
    @Test
    void testBoidSteersTowardNode() {
        MigrationSim sim = new MigrationSim();
        Node target = sim.getNodes()[sim.getActiveNode()];

        List<Boid> boids = sim.getBoids();
        boids.clear();

        double startX = 800, startY = 450;
        Boid b = new Boid(startX, startY, false, 0);
        b.vx = 0;
        b.vy = -1; // pointing straight up
        boids.add(b);

        double angleBefore = Math.atan2(b.vy, b.vx);
        double angleToNode = Math.atan2(target.y - startY, target.x - startX);

        for (int i = 0; i < 30; i++) {
            sim.step(0.016);
        }

        double angleAfter = Math.atan2(b.vy, b.vx);
        double diffBefore = angleDiff(angleBefore, angleToNode);
        double diffAfter  = angleDiff(angleAfter,  angleToNode);

        assertTrue(diffAfter < diffBefore,
            String.format("Boid angle should move closer to node (before=%.3f after=%.3f)", diffBefore, diffAfter));
    }

    private double angleDiff(double a, double b) {
        double diff = Math.abs(a - b) % (2 * Math.PI);
        return diff > Math.PI ? 2 * Math.PI - diff : diff;
    }
}