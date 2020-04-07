
import static java.lang.Math.*;

import java.io.*;
import java.nio.*;
import java.util.*;


class Coord {
	final int x;
	final int y;

	Coord(int x, int y) {
		this.x = x;
		this.y = y;
	}

	Coord(Scanner in) {
		this(in.nextInt(), in.nextInt());
	}

	Coord add(Coord other) {
		return new Coord(x + other.x, y + other.y);
	}

	// Manhattan distance (for 4 directions maps)
	// see: https://en.wikipedia.org/wiki/Taxicab_geometry
	int distance(Coord other) {
		return abs(x - other.x) + abs(y - other.y);
	}
	
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = PRIME * result + x;
		result = PRIME * result + y;
		return result;
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Coord other = (Coord) obj;
		return (x == other.x) && (y == other.y);
	}

	public String toString() {
		return x + " " + y;
	}
}


class Cell {
	boolean known;
	int ore;
	boolean hole;
	
	Cell(boolean known, int ore, boolean hole) {
		this.known = known;
		this.ore = ore;
		this.hole = hole;
	}

	Cell(Scanner in) {
		String oreStr = in.next();
		if (oreStr.charAt(0) == '?') {
			known = false;
			ore = 0;
		} else {
			known = true;
			ore = Integer.parseInt(oreStr);
		}
		String holeStr = in.next();
		hole = (holeStr.charAt(0) != '0');
	}
}


class Action {
	final String command;
	final Coord pos;
	final EntityType item;
	String message;

	private Action(String command, Coord pos, EntityType item) {
		this.command = command;
		this.pos = pos;
		this.item = item;
	}

	static Action none() {
		return new Action("WAIT", null, null);
	}

	static Action move(Coord pos) {
		return new Action("MOVE", pos, null);
	}

	static Action dig(Coord pos) {
		return new Action("DIG", pos, null);
	}

	static Action request(EntityType item) {
		return new Action("REQUEST", null, item);
	}

	public String toString() {
		StringBuilder builder = new StringBuilder(command);
		if (pos != null) {
			builder.append(' ').append(pos);
		}
		if (item != null) {
			builder.append(' ').append(item);
		}
		if (message != null) {
			builder.append(' ').append(message);
		}
		return builder.toString();
	}
}


enum EntityType {
	NOTHING, ALLY_ROBOT, ENEMY_ROBOT, RADAR, TRAP, AMADEUSIUM;

	static EntityType valueOf(int id) {
		return values()[id + 1];
	}
}


class Entity {
	private static final Coord DEAD_POS = new Coord(-1, -1);

	// Updated every turn
	final int id;
	final EntityType type;
	final Coord pos;
    final EntityType item;
    
	// Computed for my robots
	Action action;

	Entity(Scanner in) {
		id = in.nextInt();
		type = EntityType.valueOf(in.nextInt());
		pos = new Coord(in);
		item = EntityType.valueOf(in.nextInt());
	}

	boolean isAlive() {
		return !DEAD_POS.equals(pos);
	}
}


class Team {
	int score;
	Collection<Entity> robots;

	void readScore(Scanner in) {
		score = in.nextInt();
		robots = new ArrayList<>();
	}
}


class Board {
	// Given at startup
	final int width;
	final int height;

	// Updated each turn
	final Team myTeam = new Team();
	final Team opponentTeam = new Team();
	private Cell[][] cells;
	int myRadarCooldown;
	int myTrapCooldown;
	Map<Integer, Entity> entitiesById;
	Collection<Coord> myRadarPos;
	Collection<Coord> myTrapPos;

	Board(Scanner in) {
		width = in.nextInt();
		height = in.nextInt();
	}

	void update(Scanner in) {
		// Read new data
		myTeam.readScore(in);
		opponentTeam.readScore(in);
		cells = new Cell[height][width];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				cells[y][x] = new Cell(in);
			}
		}
		int entityCount = in.nextInt();
		myRadarCooldown = in.nextInt();
		myTrapCooldown = in.nextInt();
		entitiesById = new HashMap<>();
		myRadarPos = new ArrayList<>();
		myTrapPos = new ArrayList<>();
		for (int i = 0; i < entityCount; i++) {
			Entity entity = new Entity(in);
			entitiesById.put(entity.id, entity);
			if (entity.type == EntityType.ALLY_ROBOT) {
				myTeam.robots.add(entity);
			} else if (entity.type == EntityType.ENEMY_ROBOT) {
				opponentTeam.robots.add(entity);
			} else if (entity.type == EntityType.RADAR) {
				myRadarPos.add(entity.pos);
			} else if (entity.type == EntityType.TRAP) {
				myTrapPos.add(entity.pos);
			}
		}
	}

	boolean cellExist(Coord pos) {
		return (pos.x >= 0) && (pos.y >= 0) && (pos.x < width) && (pos.y < height);
	}

	Cell getCell(Coord pos) {
		return cells[pos.y][pos.x];
	}
	
	Cell[][] getMatrixCell(){
		return cells;
	}
}


class Player {

	public static void main(String args[]) {
		new Player().run();
	}

	final Scanner in = new Scanner(System.in);

	void run() {
		// Parse initial conditions
		int idRobotRadar=-1;
		Board board = new Board(in);

		while (true) {
			// Parse current state of the game
			board.update(in);

			// Insert your strategy here
			for (Entity robot : board.myTeam.robots) {
				if(board.myRadarCooldown==0&&idRobotRadar==-1) {
					robot.action=Action.request(EntityType.RADAR);
					idRobotRadar=robot.id;
				}
				else if(board.myRadarCooldown>0 || board.myRadarCooldown==0&&idRobotRadar!=-1) {
					if(robot.id!=idRobotRadar) {
						
					}
					else {
						//Posizioniamo il radar secondo le direttive:
						//- Se nel range di 7 celle c'è un altro radar scartare momentaneamente la soluzione
						//-Se non è possibile avere una copertura massima allora permettiamo l'interesezione dei campi individuati dai due radar
						// selezionando quello ottimale (in cui la superficie controllata sia massima)
						//Il punto (x,y) per posizionare il radar viene individuato come segue:
						//-Se ci fossero buche, scegli la buca più vicina
						//-Altrimenti trova il punto più vicino che permette una massima copertura
						
					}
				}
				robot.action = Action.none();
				robot.action.message = "Java Starter";
			}

			// Send your actions for this turn
			for (Entity robot : board.myTeam.robots) {
				System.out.println(robot.action);
			}
		}
	}
	
	Coord findPos(Entity robot) {
		return null;
	}
}

class Support{
	ArrayList<Coord> holes=new ArrayList<Coord>();
	Board board;
	boolean coveredByRadar[][];
	private static final int RANGE=4, UP=0,DOWN=1, LEFT=2, RIGHT=3;
	
	public Support(Board b) {
		board=b;
		coveredByRadar=new boolean[b.height][b.width];
	}
	
	public void constructRadarBoard() {
		ArrayList<Coord> radarCoord=(ArrayList<Coord>) board.myRadarPos;
		for(int i=0;i<radarCoord.size();i++) {
			updateRadarBoard(radarCoord.get(i));
		}
	}
	
	public void updateRadarBoard(Coord c) {
		for(int i=0;i<RANGE;i++) {
			for(int j=0;j<=RANGE-i;j++) {
			    if(board.cellExist(new Coord(c.x+j,c.y+i)))coveredByRadar[c.y+i][c.x+j]=true;
			    if(board.cellExist(new Coord(c.x-j,c.y+i)))coveredByRadar[c.y+i][c.x-j]=true;
			    if(board.cellExist(new Coord(c.x+j,c.y-i)))coveredByRadar[c.y-i][c.x+j]=true;
			    if(board.cellExist(new Coord(c.x-j,c.y-i)))coveredByRadar[c.y-i][c.x-j]=true;
			}
		}
	}
	
	public void findHoles() {
		for(int i=0;i<board.getMatrixCell().length;i++) {
			for(int j=0;j<(board.getMatrixCell())[i].length;j++) {
				if(board.getMatrixCell()[i][j].hole&&!holes.contains(new Coord(i,j))) holes.add(new Coord(i,j));
			}
		}
	}
	
	public Coord calculateBestHole(Coord c) {
		if(holes.size()==0) return null;
		Coord best=holes.get(0);
		for(int i=1;i<holes.size();i++) {
			int distance=c.distance(best);
			if(distance-(c.distance(holes.get(i)))>0) distance=c.distance(holes.get(i));
		}
		return best;
	}
	
	public int[] countOre(Coord c) {
		int[] ore=new int[RANGE];
		for(int i=0;i<RANGE;i++) {
			for(int j=0;j<=RANGE-i;j++) {
			    if(board.cellExist(new Coord(c.x+j,c.y+i))&& coveredByRadar[c.y+i][c.x+j])ore[UP]++;
			    if(board.cellExist(new Coord(c.x-j,c.y+i))&& coveredByRadar[c.y+i][c.x-j])ore[UP]++;
			    if(board.cellExist(new Coord(c.x+j,c.y-i))&& coveredByRadar[c.y-i][c.x+j])ore[DOWN]++;
			    if(board.cellExist(new Coord(c.x-j,c.y-i))&& coveredByRadar[c.y-i][c.x-j])ore[DOWN]++;
			}
		}
		for(int i=0;i<RANGE;i++) {
			for(int j=0;j<=RANGE-i;j++) {
			    if(board.cellExist(new Coord(c.x+i,c.y+j))&& coveredByRadar[c.y+j][c.x+i])ore[RIGHT]++;
			    if(board.cellExist(new Coord(c.x-i,c.y+j))&& coveredByRadar[c.y+j][c.x-i])ore[RIGHT]++;
			    if(board.cellExist(new Coord(c.x+i,c.y-j))&& coveredByRadar[c.y-j][c.x+i])ore[LEFT]++;
			    if(board.cellExist(new Coord(c.x-i,c.y-j))&& coveredByRadar[c.y-j][c.x-i])ore[LEFT]++;
			}
		}
		return ore;
	}
	
	public int calculateVisible(Coord c) {
		int cont=0;
		for(int i=0;i<RANGE;i++) {
			for(int j=0;j<=RANGE-i;j++) {
			    if(board.cellExist(new Coord(c.x+j,c.y+i))&& coveredByRadar[c.y+i][c.x+j])cont++;
			    if(board.cellExist(new Coord(c.x-j,c.y+i))&& coveredByRadar[c.y+i][c.x-j])cont++;
			    if(board.cellExist(new Coord(c.x+j,c.y-i))&& coveredByRadar[c.y-i][c.x+j])cont++;
			    if(board.cellExist(new Coord(c.x-j,c.y-i))&& coveredByRadar[c.y-i][c.x-j])cont++;
			}
		}
		return cont;
	}
	
	public void thinkRadar(Coord c) {
		ArrayList<Coord> radarCoord=(ArrayList<Coord>) board.myRadarPos;
		if(radarCoord.size()==0) {
			//Il primo radar viene posizionato in una posizione centrale nella mappa
			int height=board.height;
			int width=board.width;
			System.out.println("DIVE "+width/2+" "+height/2);
		}
		else {
			//C'è almeno un radar nella mappa che usiamo come punto di riferimento
			Object[] best=new Object[3];
			for(int i=0;i<radarCoord.size();i++) {
				if(i==0) {
					best[0]=radarCoord.get(i);
					
				}
			}
		}
	}
}
