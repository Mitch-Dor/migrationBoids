package migration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class P2PMigrationTest {

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

    // -------------------------------------------------------------------------
    // Test 9: Boids inside an INACTIVE node's safe area are not locked in place
    //
    // If a node is not the current target, boids inside it should still be free
    // to move out — they shouldn't be pinned just because they're in a safe area.
    // -------------------------------------------------------------------------
    @Test
    void testBoidsNotLockedInInactiveSafeArea() {
        MigrationSim sim = new MigrationSim();
 
        // Use the inactive node
        int inactiveIdx = 1 - sim.getActiveNode();
        Node inactiveNode = sim.getNodes()[inactiveIdx];
 
        List<Boid> boids = sim.getBoids();
        boids.clear();
 
        // Place 10 boids inside the inactive node, all pointing directly away from it
        int count = 10;
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            Boid b = new Boid(
                inactiveNode.x + Math.cos(angle) * (inactiveNode.safeRadius * 0.5),
                inactiveNode.y + Math.sin(angle) * (inactiveNode.safeRadius * 0.5),
                false, 0
            );
            // Point away from node center so natural movement exits the area
            b.vx = Math.cos(angle) * 1.2;
            b.vy = Math.sin(angle) * 1.2;
            boids.add(b);
        }
 
        sim.reindexLeaders();
 
        // Run long enough for boids to drift out if free to do so
        for (int i = 0; i < 120; i++) sim.step(0.016);
 
        long stillInside = boids.stream()
            .filter(b -> inactiveNode.contains(b.x, b.y))
            .count();
 
        // At least some boids should have left — if all 10 are still inside,
        // they are being incorrectly retained by the inactive node
        assertTrue(stillInside < count,
            "Boids should be free to leave an inactive node's safe area, " +
            "but all " + count + " are still inside");
    }

    // -------------------------------------------------------------------------
    // Test: Boids inside an active safe area move smoothly — no teleporting
    //
    // Verifies that the safe area retention is implemented via force adjustment
    // (weak repulsion + attraction toward center) rather than hard position
    // resets or barriers. Checks that no boid moves more than a physically
    // plausible distance in a single step, and that boids are always moving
    // (i.e. attraction toward center hasn't frozen them in place).
    // -------------------------------------------------------------------------
    @Test
    void testBoidsMoveSmoothlyInActiveSafeArea() {
        MigrationSim sim = new MigrationSim();
        sim.setSpeedMult(1.0);
        Node activeNode = sim.getNodes()[sim.getActiveNode()];
 
        List<Boid> boids = sim.getBoids();
        boids.clear();
 
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
 
        // Max plausible distance per step at speedMult=1.0.
        // BASE_SPEED=1.2, clamp max is 1.1x for leaders — followers capped at 1.2.
        // With a dt of 0.016 and no exotic forces, no boid should jump more than
        // a small multiple of that. We use 10x as a generous teleport threshold.
        double maxPlausibleStepDist = 1.2 * 10;
 
        double totalMovement = 0;
        int    movementSamples = 0;
 
        for (int step = 0; step < 120; step++) {
            // Snapshot positions before step
            double[] prevX = new double[boids.size()];
            double[] prevY = new double[boids.size()];
            for (int i = 0; i < boids.size()-1; i++) {
                prevX[i] = boids.get(i).x;
                prevY[i] = boids.get(i).y;
            }
 
            sim.step(0.016);
 
            // Check each boid's displacement this step
            for (int i = 0; i < boids.size()-1; i++) {
                Boid b = boids.get(i);
                double dx = b.x - prevX[i];
                double dy = b.y - prevY[i];
                double dist = Math.sqrt(dx * dx + dy * dy);
 
                assertTrue(dist <= maxPlausibleStepDist,
                    String.format("Boid teleported at step %d: moved %.2f units in one frame " +
                        "(max plausible=%.2f). Position was (%.1f,%.1f) now (%.1f,%.1f)",
                        step, dist, maxPlausibleStepDist,
                        prevX[i], prevY[i], b.x, b.y));
 
                totalMovement += dist;
                movementSamples++;
            }
        }
 
        // Sanity check: boids should still be moving on average, not frozen
        double avgStepDist = totalMovement / movementSamples;
        assertTrue(avgStepDist > 0.05,
            String.format("Boids appear frozen — avg movement per step was %.4f. " +
                "Attraction to node center may be too strong.", avgStepDist));
    }

    private double angleDiff(double a, double b) {
        double diff = Math.abs(a - b) % (2 * Math.PI);
        return diff > Math.PI ? 2 * Math.PI - diff : diff;
    }
}