

import static java.lang.Math.*;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

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

	Cell[][] getMatrixCell() {
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
		int idRobotRadar = -1;
		int idRobotTrap = -1;
		Board board = new Board(in);
		Support support = new Support(board);
		Coord postrap = null;
		int idReq = 0;
		double oreCoverage = 0.0;
		double totalCoverage = 0.0;
		Coord wheretodig = support.thinkRadar2();
		;

		while (true) {
			// Parse current state of the game
			board.update(in);
			postrap = support.estimate();
			Coord[] radars = board.myRadarPos.toArray(new Coord[0]);
			support.constructRadarBoard();
			for (int i = 0; i < radars.length; i++)
				support.updateRadarBoard(radars[i]);

			// Insert your strategy here
			for (Entity robot : board.myTeam.robots) {

				if (!robot.pos.equals(new Coord(-1, -1))) {// actions only for the non dead robots

					if (board.myRadarPos.contains(wheretodig))
						idReq = -1;

					if (robot.item != EntityType.RADAR && robot.id == idRobotRadar && idReq == -1) {
						System.err.print("updating" + "\n");
						idRobotRadar = -1;
						totalCoverage = 0.0;
						for (int i = 0; i < board.height; i++)
							for (int j = 0; j < board.width; j++)
								if (support.coveredByRadar[i][j] && board.getCell(new Coord(j, i)).ore > 0
										&& !board.myTrapPos.contains(new Coord(j, i))
										&& !board.myRadarPos.contains(new Coord(j, i))
										&& support.checkHole(new Coord(j, i)))
									totalCoverage += board.getCell(new Coord(j, i)).ore;

					}
					if (robot.item != EntityType.TRAP && robot.id == idRobotTrap)
						idRobotTrap = -1;

					if ((!board.myRadarPos.contains(wheretodig) && idReq == robot.id)
							|| (board.myRadarCooldown == 0 && idRobotRadar == -1 && robot.id != idRobotTrap
									&& (totalCoverage == 0 || oreCoverage / totalCoverage < 0.3))) {
						System.err.print("entra" + "\n");
						if (board.myRadarPos.contains(wheretodig)) {
							wheretodig = support.thinkRadar2();
						}
						if (!wheretodig.equals(new Coord(-1, -1))) {
							robot.action = Action.request(EntityType.RADAR);
							idRobotRadar = robot.id;
							idReq = robot.id;
							System.err.print("entraaaaaa" + "\n");
						}

					}
					
					  if (board.myTrapCooldown == 0 && idRobotTrap == -1 && robot.id !=
					  idRobotRadar) { robot.action = Action.request(EntityType.TRAP); idRobotTrap =
					  robot.id; postrap = support.placeTrap(); }
					 

					if (idRobotRadar != robot.id && idRobotTrap != robot.id) {

						if (robot.item == EntityType.AMADEUSIUM) {
							robot.action = Action.move(new Coord(0, robot.pos.y));
							support.addHole(support.getRobotPos(robot));
							support.alreadyVisited.add(support.getPosToDig(robot));
						} else {

							if (board.myRadarPos.size() > 0) {
                            //Ho piazzato almeno un Radar 
							boolean finded=false;
							for (int j = 0; j < board.width; j++) {
								for (int i = 0; i < board.height; i++) {
										if (board.getCell(new Coord(j,i)).known && board.getCell(new Coord(j, i)).ore > 0
												&& !support.isAlreadyVisited(new Coord(j, i))
												&& !board.myTrapPos.contains(new Coord(j, i))
												&& support.checkHole(new Coord(j, i))) {
											support.addPosToDig(robot, new Coord(j, i));
											support.addPos(robot, new Coord(j, i));
											robot.action = Action.dig(new Coord(j, i));
											finded=true;
											break;
										}
									}
								if(finded) break;
								}
							if(!finded) {
								//Non ho trovato una posizione libera ( non scavata da noi ) e quindi devo ritornare su una posizione in cui ho gia scavato
								for (int j = 0; j < board.width; j++) {
									for (int i = 0; i < board.height; i++) {
											if (board.getCell(new Coord(j,i)).known && board.getCell(new Coord(j, i)).ore > 0
													&& !board.myTrapPos.contains(new Coord(j, i))
													&& support.checkHole(new Coord(j, i))) {
												support.addPosToDig(robot, new Coord(j, i));
												support.addPos(robot, new Coord(j, i));
												robot.action = Action.dig(new Coord(j, i));
												finded=true;
												break;
											}
										}
									if(finded) break;
									}
							}
							}
							else {
								//Non ho piazzato nessun radar mando i robot in una posizione stabilita tentando la fortuna
								support.addPosToDig(robot, new Coord(5, 5+(robot.id%10)));
								support.addPos(robot, new Coord(5, 5+(robot.id%10)));
								robot.action = Action.dig(new Coord(5, 5+(robot.id%10)));
							}
						}
					}

					// closest = new Coord(j, i);

					/*
					 * if(!closest.equals(new Coord(100, 100))){ robot.action = Action.dig(closest);
					 * support.addPos(robot, closest); }
					 */
					/*
					 * else robot.action = Action.dig(new Coord(15, 7)); } else robot.action =
					 * Action.move(new Coord(9, 4)); } }
					 */
					// if the robots with the radars/traps are dead
					else if (robot.id == idRobotRadar && robot.item == EntityType.RADAR)
						robot.action = Action.dig(wheretodig);
					else if (robot.id == idRobotTrap && robot.item == EntityType.TRAP && postrap != null)
						robot.action = Action.dig(postrap);

					// robot.action = Action.none();
					// robot.action.message = "Java Starter";
				} // fine if
				else if (robot.id == idRobotRadar)
					idRobotRadar = -1;
				else if (robot.id == idRobotTrap)
					idRobotTrap = -1;

			} // FINE FOR
			oreCoverage = 0.0;
			// controlliamo quanti ore sono rimasti
			for (int i = 0; i < board.height; i++)
				for (int j = 0; j < board.width; j++)
					if (support.coveredByRadar[i][j] && board.getCell(new Coord(j, i)).ore > 0
							&& !board.myTrapPos.contains(new Coord(j, i)) && !board.myRadarPos.contains(new Coord(j, i))
							&& support.checkHole(new Coord(j, i)))
						oreCoverage += board.getCell(new Coord(j, i)).ore;

			System.err.print(oreCoverage / totalCoverage + "\n");
			System.err.print(oreCoverage + "\n");
			System.err.print(totalCoverage);
			// Send your actions for this turn
			for (Entity robot : board.myTeam.robots) {
				if (robot.action != null)
					System.out.println(robot.action);
				else
					System.out.println("WAIT");
			}
		}
	}

	Coord findPos(Entity robot) {
		return null;
	}

}

class Support {
	ArrayList<Coord> holes = new ArrayList<Coord>();
	Board board;
	boolean[][] coveredByRadar;
	int[][] forecastMatrix;
	Queue<Coord> radPos;
	ArrayList<Coord> totRadPos;
	LinkedList<Coord> myDig, enemyDig;
	HashMap<Integer, Coord> storyRobotPos = new HashMap<Integer, Coord>();
	HashMap<Integer, Coord> coveragePos = new HashMap<Integer, Coord>();
	LinkedList<Coord> alreadyVisited = new LinkedList<Coord>();
	private static final int RANGE = 4, UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

	public Support(Board b) {
		board = b;
		myDig = new LinkedList<Coord>();
		enemyDig = new LinkedList<Coord>();
		coveredByRadar = new boolean[b.height][b.width];
		forecastMatrix = new int[b.height][b.width];
		radPos = new LinkedBlockingQueue<Coord>();
		totRadPos = new ArrayList<Coord>();

		Coord x = new Coord(4, 7);
		radPos.add(x);
		totRadPos.add(x);

		x = new Coord(9, 4);
		radPos.add(x);
		totRadPos.add(x);

		x = new Coord(9, 10);
		radPos.add(x);
		totRadPos.add(x);

		x = new Coord(17, 4);
		radPos.add(x);
		totRadPos.add(x);

		x = new Coord(17, 10);
		radPos.add(x);
		totRadPos.add(x);

		x = new Coord(25, 4);
		radPos.add(x);
		totRadPos.add(x);

		x = new Coord(25, 10);
		radPos.add(x);
		totRadPos.add(x);

	}

	public void constructRadarBoard() {
		ArrayList<Coord> radarCoord = (ArrayList<Coord>) board.myRadarPos;
		for (int i = 0; i < radarCoord.size(); i++) {
			updateRadarBoard(radarCoord.get(i));
		}
	}

	public void updateRadarBoard(Coord c) {
		for (int i = 0; i < RANGE; i++) {
			for (int j = 0; j <= RANGE - i; j++) {
				if (board.cellExist(new Coord(c.x + j, c.y + i)))
					coveredByRadar[c.y + i][c.x + j] = true;
				if (board.cellExist(new Coord(c.x - j, c.y + i)))
					coveredByRadar[c.y + i][c.x - j] = true;
				if (board.cellExist(new Coord(c.x + j, c.y - i)))
					coveredByRadar[c.y - i][c.x + j] = true;
				if (board.cellExist(new Coord(c.x - j, c.y - i)))
					coveredByRadar[c.y - i][c.x - j] = true;
			}
		}
	}

	public void findHoles() {
		for (int i = 0; i < board.getMatrixCell().length; i++) {
			for (int j = 0; j < (board.getMatrixCell())[i].length; j++) {
				if (board.getMatrixCell()[i][j].hole && !holes.contains(new Coord(i, j)))
					holes.add(new Coord(i, j));
			}
		}
	}

	public Coord calculateBestHole(Coord c) {
		if (holes.size() == 0)
			return null;
		Coord best = holes.get(0);
		for (int i = 1; i < holes.size(); i++) {
			int distance = c.distance(best);
			if (distance - (c.distance(holes.get(i))) > 0)
				distance = c.distance(holes.get(i));
		}
		return best;
	}

	public int[] countOre(Coord c) {
		int[] ore = new int[RANGE];
		for (int i = 0; i < RANGE; i++) {
			for (int j = 0; j <= RANGE - i; j++) {
				if (board.cellExist(new Coord(c.x + j, c.y - i)) && board.getCell(new Coord(c.x + j, c.y - i)).ore > 0)
					ore[UP]++;
				if (board.cellExist(new Coord(c.x - j, c.y - i)) && board.getCell(new Coord(c.x - j, c.y - i)).ore > 0)
					ore[UP]++;
				if (board.cellExist(new Coord(c.x + j, c.y + i)) && board.getCell(new Coord(c.x + j, c.y + i)).ore > 0)
					ore[DOWN]++;
				if (board.cellExist(new Coord(c.x - j, c.y + i)) && board.getCell(new Coord(c.x - j, c.y + i)).ore > 0)
					ore[DOWN]++;
			}
		}
		for (int i = 0; i < RANGE; i++) {
			for (int j = 0; j <= RANGE - i; j++) {
				if (board.cellExist(new Coord(c.x + i, c.y + j)) && board.getCell(new Coord(c.x + i, c.y + j)).ore > 0)
					ore[RIGHT]++;
				if (board.cellExist(new Coord(c.x + i, c.y - j)) && board.getCell(new Coord(c.x + i, c.y - j)).ore > 0)
					ore[RIGHT]++;
				if (board.cellExist(new Coord(c.x - i, c.y + j)) && board.getCell(new Coord(c.x - i, c.y + j)).ore > 0)
					ore[LEFT]++;
				if (board.cellExist(new Coord(c.x - i, c.y - j)) && board.getCell(new Coord(c.x - i, c.y - j)).ore > 0)
					ore[LEFT]++;
			}
		}
		return ore;
	}

	public int calculateVisible(Coord c) {
		int cont = 0;
		for (int i = 0; i < RANGE; i++) {
			for (int j = 0; j <= RANGE - i; j++) {
				if (board.cellExist(new Coord(c.x + j, c.y + i)) && coveredByRadar[c.y + i][c.x + j])
					cont++;
				if (board.cellExist(new Coord(c.x - j, c.y + i)) && coveredByRadar[c.y + i][c.x - j])
					cont++;
				if (board.cellExist(new Coord(c.x + j, c.y - i)) && coveredByRadar[c.y - i][c.x + j])
					cont++;
				if (board.cellExist(new Coord(c.x - j, c.y - i)) && coveredByRadar[c.y - i][c.x - j])
					cont++;
			}
		}
		return cont;
	}

	private Coord findPosRec(Coord c, ArrayList<Coord> visited, Object[] memory) {
		visited.add(c);
		boolean[] finded = new boolean[4];
		ArrayList<Coord> tempRad = (ArrayList<Coord>) board.myRadarPos;
		ArrayList<Coord> search = new ArrayList<Coord>();
		Coord best = null;
		int promise = 0;
		int penalty = 0;
		for (int i = 8; i > 4; i--) {
			if (!finded[RIGHT] && board.cellExist(new Coord(c.x + i, c.y))) {
				if (proximity(3, new Coord(c.x + i, c.y))) {
					finded[RIGHT] = true;
				} else {
					Coord e = new Coord(c.x + i, c.y);
					finded[RIGHT] = true;
					if ((best == null && tempRad.contains(e)) || tempRad.contains(e)) {
						if (!visited.contains(e)) {
							search.add(e);
						}
					} else if (best == null) {
						best = e;
						promise = countOre(c)[RIGHT];
						penalty = calculateVisible(e);
					} else {
						if ((promise * 3 - penalty) - (countOre(c)[RIGHT] * 3 - calculateVisible(e)) < 0) {
							best = e;
							promise = countOre(c)[RIGHT];
							penalty = calculateVisible(e);
						}
					}
					if (memory[0] == null) {
						if (tempRad.contains(e)) {
							memory[0] = e;
							memory[1] = -1;
						} else {
							memory[0] = e;
							memory[1] = countOre(c)[RIGHT] * 3 - calculateVisible(e);
						}
					} else {
						if (!tempRad.contains(e)) {
							if ((Integer) memory[1] - countOre(c)[RIGHT] * 3 - calculateVisible(e) < 0) {
								memory[0] = e;
								memory[1] = countOre(c)[RIGHT] * 3 - calculateVisible(e);
							}
						}
						;
					}
				}
			}
			if (!finded[LEFT] && board.cellExist(new Coord(c.x - i, c.y)) && (c.x - i) != 0) {
				if (proximity(3, new Coord(c.x - i, c.y))) {
					finded[LEFT] = true;
				} else {
					Coord e = new Coord(c.x - i, c.y);
					finded[LEFT] = true;
					if ((best == null && tempRad.contains(e)) || tempRad.contains(e)) {
						if (!visited.contains(e)) {
							search.add(e);
						}
					} else if (best == null) {
						best = e;
						promise = countOre(c)[LEFT];
						penalty = calculateVisible(e);
					} else {
						if ((promise * 3 - penalty) - (countOre(c)[LEFT] * 3 - calculateVisible(e)) < 0) {
							best = e;
							promise = countOre(c)[LEFT];
							penalty = calculateVisible(e);
						}
					}
					if (memory[0] == null) {
						if (tempRad.contains(e)) {
							memory[0] = e;
							memory[1] = -1;
						} else {
							memory[0] = e;
							memory[1] = countOre(c)[LEFT] * 3 - calculateVisible(e);
						}
					} else {
						if (!tempRad.contains(e)) {
							if ((Integer) memory[1] - countOre(c)[LEFT] * 3 - calculateVisible(e) < 0) {
								memory[0] = e;
								memory[1] = countOre(c)[LEFT] * 3 - calculateVisible(e);
							}
						}
						;
					}
				}
			}
			if (!finded[UP] && board.cellExist(new Coord(c.x, c.y - i))) {
				if (proximity(3, new Coord(c.x, c.y - i))) {
					finded[UP] = true;
				} else {
					Coord e = new Coord(c.x, c.y - i);
					finded[UP] = true;
					if ((best == null && tempRad.contains(e)) || tempRad.contains(e)) {
						if (!visited.contains(e)) {
							search.add(e);
						}
					} else if (best == null) {
						best = e;
						promise = countOre(c)[UP];
						penalty = calculateVisible(e);
					} else {
						if ((promise * 3 - penalty) - (countOre(c)[UP] * 3 - calculateVisible(e)) < 0) {
							best = e;
							promise = countOre(c)[UP];
							penalty = calculateVisible(e);
						}
					}
					if (memory[0] == null) {
						if (tempRad.contains(e)) {
							memory[0] = e;
							memory[1] = -1;
						} else {
							memory[0] = e;
							memory[1] = countOre(c)[UP] * 3 - calculateVisible(e);
						}
					} else {
						if (!tempRad.contains(e)) {
							if ((Integer) memory[1] - countOre(c)[UP] * 3 - calculateVisible(e) < 0) {
								memory[0] = e;
								memory[1] = countOre(c)[UP] * 3 - calculateVisible(e);
							}
						}
						;
					}
				}
			}
			if (!finded[DOWN] && board.cellExist(new Coord(c.x, c.y + i))) {
				if (proximity(3, new Coord(c.x, c.y + i))) {
					finded[DOWN] = true;
				} else {
					Coord e = new Coord(c.x, c.y + i);
					finded[DOWN] = true;
					if ((best == null && tempRad.contains(e)) || tempRad.contains(e)) {
						if (!visited.contains(e)) {
							search.add(e);
						}
					} else if (best == null) {
						best = e;
						promise = countOre(c)[DOWN];
						penalty = calculateVisible(e);
					} else {
						if ((promise * 3 - penalty) - (countOre(c)[DOWN] * 3 - calculateVisible(e)) < 0) {
							best = e;
							promise = countOre(c)[DOWN];
							penalty = calculateVisible(e);
						}
					}
					if (memory[0] == null) {
						if (tempRad.contains(e)) {
							memory[0] = e;
							memory[1] = -1;
						} else {
							memory[0] = e;
							memory[1] = countOre(c)[DOWN] * 3 - calculateVisible(e);
						}
					} else {
						if (!tempRad.contains(e)) {
							if ((Integer) memory[1] - countOre(c)[DOWN] * 3 - calculateVisible(e) < 0) {
								memory[0] = e;
								memory[1] = countOre(c)[DOWN] * 3 - calculateVisible(e);
							}
						}
						;
					}
				}
			}
		}
		while (search.size() > 0)

		{
			Coord s = search.remove(0);
			findPosRec(s, visited, memory);
		}
		if ((Integer) memory[1] != -1)
			return (Coord) memory[0];
		return null;
	}

	public Coord findPos() {
		Coord c = bestCoord((ArrayList<Coord>) board.myRadarPos);
		Object[] memory = new Object[2];
		return findPosRec(c, new ArrayList<Coord>(), memory);
	}

	public Coord thinkRadar() {
		ArrayList<Coord> radarCoord = (ArrayList<Coord>) board.myRadarPos;
		if (radarCoord.size() == 0) {
			// Il primo radar viene posizionato in una posizione centrale nella mappa
			int height = board.height;
			int width = board.width;
			return new Coord(width / 2, height / 2);
		} else {
			// C'� almeno un radar nella mappa che usiamo come punto di riferimento
			return findPos();
		}

	}

	public Coord estimate() {
		for (int j = 1; j < board.width; j++) {
			for (int i = 0; i < board.height; i++) {
				if (board.getCell(new Coord(j, i)).ore > 1 && !board.myRadarPos.contains(new Coord(j, i))
						&& this.checkHole(new Coord(j, i)) && !board.myTrapPos.contains(new Coord(j, i)))
					return new Coord(j, i);
			}
		}
		return null;
	}

	public Coord estimate(ArrayList<Entity> enemy) {
		for (int i = 0; i < enemy.size(); i++) {
			Coord c = enemy.get(i).pos;
			if (enemy.get(i).isAlive()) {
				forecastMatrix[c.y][c.x] = forecastMatrix[c.y][c.x] + 3;
				if (board.cellExist(new Coord(c.x + 1, c.y)))
					forecastMatrix[c.y][c.x + 1]++;
				if (board.cellExist(new Coord(c.x - 1, c.y)))
					forecastMatrix[c.y][c.x - 1]++;
				if (board.cellExist(new Coord(c.x, c.y + 1)))
					forecastMatrix[c.y + 1][c.x]++;
				if (board.cellExist(new Coord(c.x, c.y - 1)))
					forecastMatrix[c.y - 1][c.x]++;
				if (board.cellExist(new Coord(c.x - 1, c.y - 1)))
					forecastMatrix[c.y - 1][c.x - 1]++;
				if (board.cellExist(new Coord(c.x + 1, c.y - 1)))
					forecastMatrix[c.y - 1][c.x + 1]++;
				if (board.cellExist(new Coord(c.x + 1, c.y + 1)))
					forecastMatrix[c.y + 1][c.x + 1]++;
				if (board.cellExist(new Coord(c.x - 1, c.y + 1)))
					forecastMatrix[c.y + 1][c.x - 1]++;
			}
		}
		Coord best = null;
		int numVisit = 0;
		for (int i = 0; i < forecastMatrix.length; i++) {
			for (int j = 1; j < forecastMatrix[i].length; j++) {
				if (numVisit < forecastMatrix[i][j]) {
					numVisit = forecastMatrix[i][j];
					forecastMatrix[i][j] = 0;
					best = new Coord(j, i);
				}
			}
		}
		return best;
	}

	private boolean proximity(int range, Coord c) {
		ArrayList<Coord> ap = (ArrayList<Coord>) board.myRadarPos;
		for (int i = 1; i < range; i++) {
			if (ap.contains(new Coord(c.x + 1, c.y)) || ap.contains(new Coord(c.x - i, c.y))
					|| ap.contains(new Coord(c.x, c.y - i)) || ap.contains(new Coord(c.x, c.y + i))
					|| ap.contains(new Coord(c.x - i, c.y - i)) || ap.contains(new Coord(c.x - i, c.y + i))
					|| ap.contains(new Coord(c.x + i, c.y + i)) || ap.contains(new Coord(c.x + i, c.y - i))) {
				return true;
			}
		}
		return false;
	}

	private Coord bestCoord(ArrayList<Coord> ps) {
		Coord best = null;
		for (int i = 0; i < ps.size(); i++) {
			if (best == null)
				best = ps.get(i);
			else if ((countOre(best)[UP] + countOre(best)[DOWN] + countOre(best)[RIGHT] + countOre(best)[LEFT])
					- (countOre(ps.get(i))[UP] + countOre(ps.get(i))[DOWN] + countOre(ps.get(i))[RIGHT]
							+ countOre(ps.get(i))[LEFT]) < 0) {
				best = ps.get(i);
			}
		}
		return best;
	}

	public Coord thinkRadar2() {
		ArrayList<Coord> updatedRadarPos = (ArrayList<Coord>) board.myRadarPos;
		if (radPos.isEmpty()) {
			for (int i = 0; i < totRadPos.size(); i++) {
				if (!updatedRadarPos.contains(totRadPos.get(i))) {
					radPos.add(totRadPos.get(i));
					break;
				}
			}
		}
		if (radPos.isEmpty())
			return new Coord(-1, -1);
		else

			return radPos.remove();
	}

	public boolean checkHole(Coord c) {
		if (myDig.contains(c) || !board.getCell(c).hole)
			return true;
		return false;
	}

	public Coord placeTrap() {
		if (enemyDig.size() == 0) {
			for (int j = 1; j < board.width; j++) {
				for (int i = 0; i < board.height; i++) {
					if (board.getCell(new Coord(j, i)).hole && !myDig.contains(new Coord(j, i))
							&& !board.myRadarPos.contains(new Coord(j, i))
							&& !board.myTrapPos.contains(new Coord(j, i))) {
						enemyDig.add(new Coord(j, i));
					}
				}
			}
			Coord best = null;
			int distance = 0;
			for (int i = 0; i < enemyDig.size(); i++) {
				if (best == null) {
					best = enemyDig.get(i);
					for (int j = 0; j < enemyDig.size(); j++) {
						if (i != j) {
							distance = distance + enemyDig.get(j).distance(best);
						}
					}
				} else {
					Coord tempBest = enemyDig.get(i);
					int tempDistance = 0;
					for (int j = 0; j < enemyDig.size(); j++) {
						if (i != j) {
							tempDistance = tempDistance + enemyDig.get(j).distance(tempBest);
						}
					}
					if (distance > tempDistance) {
						distance = tempDistance;
						best = tempBest;
					}
				}
			}
			return best;
		} else {
			LinkedList<Coord> tempEnemyDig = new LinkedList<Coord>();
			for (int j = 1; j < board.width; j++) {
				for (int i = 0; i < board.height; i++) {
					if (board.getCell(new Coord(j, i)).hole && !myDig.contains(new Coord(j, i))
							&& !board.myRadarPos.contains(new Coord(j, i)) && !board.myTrapPos.contains(new Coord(j, i))
							&& !enemyDig.contains(new Coord(j, i))) {
						tempEnemyDig.add(new Coord(j, i));
					}
				}
			}
			Coord best = null;
			int distance = 0;
			for (int i = 0; i < tempEnemyDig.size(); i++) {
				if (best == null) {
					best = tempEnemyDig.get(i);
					for (int j = 0; j < tempEnemyDig.size(); j++) {
						if (i != j) {
							distance = distance + tempEnemyDig.get(j).distance(best);
						}
					}
				} else {
					Coord tempBest = tempEnemyDig.get(i);
					int tempDistance = 0;
					for (int j = 0; j < tempEnemyDig.size(); j++) {
						if (i != j) {
							tempDistance = tempDistance + tempEnemyDig.get(j).distance(tempBest);
						}
					}
					if (distance > tempDistance) {
						distance = tempDistance;
						best = tempBest;
					}
				}
			}
			for (int j = 0; j < tempEnemyDig.size(); j++) {
				enemyDig.add(tempEnemyDig.get(j));
			}
			return best;
		}
	}

	public void addHole(Coord c) {
		if (!myDig.contains(c))
			myDig.add(c);
	}

	public void addPos(Entity e, Coord c) {
		storyRobotPos.put(e.id, c);
	}

	public void containsRobot(Entity e) {
		storyRobotPos.containsKey(e.id);
	}

	public Coord getRobotPos(Entity e) {
		return storyRobotPos.get(e.id);
	}

	public boolean isAlreadyVisited(Coord c) {
		return alreadyVisited.contains(c);
	}

	public void addPosToDig(Entity e, Coord c) {
		coveragePos.put(e.id, c);
	}

	public Coord getPosToDig(Entity e) {
		return coveragePos.get(e.id);
	}

	public boolean posToDigNull(Entity e) {
		return coveragePos.get(e.id) == null;
	}

}
