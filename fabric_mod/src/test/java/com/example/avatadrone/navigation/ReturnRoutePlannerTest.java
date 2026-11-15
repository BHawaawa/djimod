package com.example.avatadrone.navigation;

import java.util.List;
import com.example.avatadrone.navigation.ReturnRoutePlanner.Point;

public class ReturnRoutePlannerTest {
    public static void main(String[] args) {
        Point start=new Point(0,0,0), home=new Point(16,0,0);
        List<Point> direct=ReturnRoutePlanner.plan(start,home,(a,b)->true,2,16,3000);
        require(direct.equals(java.util.Collections.singletonList(home)), "clear route must go directly home");
        // Mountain spans the whole z axis: only a climb can cross it.
        ReturnRoutePlanner.Corridor mountain=(a,b)-> {
            for(int i=0;i<=100;i++) {
                double t=i/100.0, x=a.x()+(b.x()-a.x())*t, y=a.y()+(b.y()-a.y())*t;
                if (x>=5 && x<=11 && y<6) return false;
            }
            return true;
        };
        List<Point> climb=ReturnRoutePlanner.plan(start,home,mountain,2,16,6000);
        validate(start,home,climb,mountain);
        require(climb.stream().anyMatch(p->p.y()>=6), "must climb over mountain");
        // Impassable ceiling makes a sideways detour necessary.
        ReturnRoutePlanner.Corridor wall=(a,b)-> {
            for(int i=0;i<=100;i++) {
                double t=i/100.0, x=a.x()+(b.x()-a.x())*t, z=a.z()+(b.z()-a.z())*t;
                if (x>=5 && x<=11 && Math.abs(z)<4) return false;
            }
            return true;
        };
        List<Point> around=ReturnRoutePlanner.plan(start,home,wall,2,16,6000);
        validate(start,home,around,wall);
        require(around.stream().anyMatch(p->Math.abs(p.z())>=4), "must go around wall");
        require(ReturnRoutePlanner.plan(start,home,(a,b)->false,2,16,50).isEmpty(), "blocked aircraft must hover");
        require(ReturnRoutePlanner.plan(start,home,mountain,2,16,1).isEmpty(), "budget exhaustion must not return unsafe route");
        System.out.println("Return route tests passed: direct, mountain, lateral detour, blocked, search budget.");
    }
    private static void validate(Point start,Point goal,List<Point> route,ReturnRoutePlanner.Corridor corridor) {
        require(!route.isEmpty() && route.get(route.size()-1).equals(goal),"route must reach home");
        Point previous=start;
        for(Point p:route) { require(corridor.clear(previous,p),"every leg must be clear"); previous=p; }
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
