package com.example.avatadrone.navigation;

import java.util.*;

/** Bounded 3D A*: collision callback checks the entire swept aircraft envelope. */
public final class ReturnRoutePlanner {
    // Plain immutable values keep the shared planner usable by Java 8 targets.
    public static final class Point {
        private final double x, y, z;
        public Point(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Point)) return false;
            Point p = (Point) other;
            return Double.compare(x, p.x) == 0 && Double.compare(y, p.y) == 0 && Double.compare(z, p.z) == 0;
        }
        @Override public int hashCode() { return 31 * (31 * Double.hashCode(x) + Double.hashCode(y)) + Double.hashCode(z); }
        @Override public String toString() { return "Point[x=" + x + ", y=" + y + ", z=" + z + "]"; }
        public double distance(Point p) { return Math.sqrt(Math.pow(x-p.x,2)+Math.pow(y-p.y,2)+Math.pow(z-p.z,2)); }
    }
    public interface Corridor { boolean clear(Point from, Point to); }
    private static final class Cell {
        final int x, y, z;
        Cell(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Cell)) return false;
            Cell c = (Cell) other;
            return x == c.x && y == c.y && z == c.z;
        }
        @Override public int hashCode() { return 31 * (31 * x + y) + z; }
    }
    private static final class Entry {
        final Cell cell;
        final double cost, score;
        Entry(Cell cell, double cost, double score) { this.cell = cell; this.cost = cost; this.score = score; }
        double score() { return score; }
    }
    private static final int[][] MOVES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    public static List<Point> plan(Point start, Point goal, Corridor corridor, double step, int radius, int budget) {
        if (corridor.clear(start, goal)) return Collections.singletonList(goal);
        Cell origin = new Cell(0,0,0);
        Map<Cell, Double> costs = new HashMap<>();
        Map<Cell, Cell> parent = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingDouble(Entry::score));
        costs.put(origin, 0.0);
        open.add(new Entry(origin, 0, start.distance(goal)));
        while (!open.isEmpty() && budget-- > 0) {
            Entry entry = open.remove();
            Cell cell = entry.cell;
            if (entry.cost > costs.getOrDefault(cell, Double.POSITIVE_INFINITY)) continue;
            Point p = point(start, cell, step);
            if (p.distance(goal) <= step * 1.75 && corridor.clear(p, goal)) {
                LinkedList<Point> result = new LinkedList<>();
                result.add(goal);
                for (Cell c = cell; !c.equals(origin); c = parent.get(c)) result.addFirst(point(start, c, step));
                return result;
            }
            for (int[] move : MOVES) {
                Cell next = new Cell(cell.x+move[0],cell.y+move[1],cell.z+move[2]);
                if (Math.abs(next.x)>radius || Math.abs(next.y)>radius || Math.abs(next.z)>radius) continue;
                double cost = entry.cost + step;
                if (cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                Point q = point(start, next, step);
                if (!corridor.clear(p, q)) continue;
                costs.put(next,cost); parent.put(next,cell);
                open.add(new Entry(next,cost,cost+q.distance(goal)));
            }
        }
        // Never fly an unchecked partial route when the search budget runs out.
        return Collections.emptyList();
    }
    private static Point point(Point start, Cell c, double step) {
        return new Point(start.x+c.x*step,start.y+c.y*step,start.z+c.z*step);
    }
}
