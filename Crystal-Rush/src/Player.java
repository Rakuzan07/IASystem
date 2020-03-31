import java.util.*;
import java.io.*;
import java.math.*;

/**
 * Deliver more ore to hq (left side of the map) than your opponent. Use radars to find ore but beware of traps!
 **/
class Player {

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int width = in.nextInt();
        int height = in.nextInt(); // size of the map

        // game loop
        while (true) {
            int myScore = in.nextInt(); // Amount of ore delivered
            int opponentScore = in.nextInt();
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    String ore = in.next(); // amount of ore or "?" if unknown
                    int hole = in.nextInt(); // 1 if cell has a hole
                }
            }
            int entityCount = in.nextInt(); // number of entities visible to you
            int radarCooldown = in.nextInt(); // turns left until a new radar can be requested
            int trapCooldown = in.nextInt(); // turns left until a new trap can be requested
            for (int i = 0; i < entityCount; i++) {
                int entityId = in.nextInt(); // unique id of the entity
                int entityType = in.nextInt(); // 0 for your robot, 1 for other robot, 2 for radar, 3 for trap
                int x = in.nextInt();
                int y = in.nextInt(); // position of the entity
                int item = in.nextInt(); // if this entity is a robot, the item it is carrying (-1 for NONE, 2 for RADAR, 3 for TRAP, 4 for ORE)
            }
            for (int i = 0; i < 5; i++) {

                // Write an action using System.out.println()
                // To debug: System.err.println("Debug messages...");
                Robot r=new Robot();
                System.out.println("WAIT"); // WAIT|MOVE x y|DIG x y|REQUEST item
            }
        }
    }
    
    private static class Robot{
        
        private static enum State {WAIT,MOVE,DIG,REQUEST};
        
        private State previousState;
        private State currentState;
        
    
    }
    
}