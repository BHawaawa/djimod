package com.example.avatadrone.signal;

public final class CoverageTest {
    private static void expect(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        expect(Coverage.contains(0, 0, -500, 0, -1), "500m boundary");
        expect(!Coverage.contains(0, 0, -500.01, 0, -1), "outside radius");
        expect(!Coverage.contains(100, 0, -100, 0, -1), "45 degrees off-axis is outside the 45-degree total sector");
        expect(!Coverage.contains(500, 0, 0, 0, -1), "no side coverage");
        expect(Coverage.contains(0, 0, 1, 0, -1), "local coverage behind station");
        expect(Coverage.contains(0, 2, 0, 0, -1), "directly above station");
        expect(Coverage.contains(0, -2, 0, 0, -1), "directly below station");
        expect(!Coverage.contains(0, 0, 5, 0, -1), "directional beyond local coverage");
        expect(Coverage.contains(0, 300, -400, 0, -1), "3D radius boundary");
        expect(!Coverage.contains(0, 301, -400, 0, -1), "height counts toward range");
        expect(!Coverage.contains(0, 0, -100, 0, 0), "invalid orientation");
        expect(!Coverage.contains(Double.NaN, 0, 0, 0, -1), "invalid position");
        int[][] directions = {{0,-1},{1,0},{0,1},{-1,0}};
        for (int[] d : directions) {
            expect(Coverage.contains(d[0]*500, 0, d[1]*500, d[0], d[1]), "front for each orientation");
            expect(!Coverage.contains(-d[0]*100, 0, -d[1]*100, d[0], d[1]), "back for each orientation");
            double heading = Math.atan2(d[1], d[0]);
            for (double offset : new double[]{-22.501, -22.5, 22.5, 22.501}) {
                double angle = heading + Math.toRadians(offset);
                expect(Coverage.contains(100*Math.cos(angle), 0, 100*Math.sin(angle), d[0], d[1])
                        == (Math.abs(offset) <= 22.5), "sector edge " + offset);
            }
            // Independent angular reference: each facing must reject side/rear samples.
            for (int sample=0; sample<3600; sample++) {
                double angle = Math.toRadians(sample / 10.0);
                double difference = Math.atan2(Math.sin(angle-heading), Math.cos(angle-heading));
                boolean expected = Math.abs(Math.toDegrees(difference)) <= 22.5 + 1e-9;
                expect(Coverage.contains(500*Math.cos(angle), 0, 500*Math.sin(angle), d[0], d[1])
                        == expected, "orientation " + heading + " at angle " + sample / 10.0);
            }
        }
        expect(!Coverage.contains(100, 0, 100, 0, 1)
                && !Coverage.contains(100, 0, 100, 1, 0), "four cardinal stations must not fill diagonal gaps");
        System.out.println("Coverage tests passed: 45-degree sectors, 500m radius, four facings and 14400 angle samples.");
    }
}
