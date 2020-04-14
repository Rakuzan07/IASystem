
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
		Coord lastdug = null;
		while (true) {
			// Parse current state of the game
			board.update(in);

			// Insert your strategy here
			for (Entity robot : board.myTeam.robots) {

				if (!robot.pos.equals(new Coord(-1, -1))) {// actions only for the non dead robots

					if (robot.item != EntityType.RADAR && robot.id == idRobotRadar)
						idRobotRadar = -1;
					if (robot.item != EntityType.TRAP && robot.id == idRobotTrap)
						idRobotTrap = -1;
					if (board.myRadarCooldown == 0 && idRobotRadar == -1 && robot.id != idRobotTrap) {
						robot.action = Action.request(EntityType.RADAR);
						idRobotRadar = robot.id;
					} else if (board.myTrapCooldown == 0 && idRobotTrap == -1 && robot.id != idRobotRadar) {
						robot.action = Action.request(EntityType.TRAP);
						idRobotTrap = robot.id;
						postrap = support.estimate((ArrayList<Entity>) board.opponentTeam.robots);
					}
					if (idRobotRadar != robot.id && idRobotTrap != robot.id) {

						if (robot.item == EntityType.AMADEUSIUM)
							robot.action = Action.move(new Coord(0, robot.pos.y));
						else {
							Coord[] radars = board.myRadarPos.toArray(new Coord[0]);
							support.constructRadarBoard();
							if (radars.length > 0) {

								for (int i = 0; i < radars.length; i++)
									support.updateRadarBoard(radars[i]);

								Coord closest = new Coord(100, 100);

								for (int i = 0; i < board.height; i++)
									for (int j = 0; j < board.width; j++) {
										if (support.coveredByRadar[i][j] && board.getCell(new Coord(j, i)).ore > 0
												&& robot.pos.distance(new Coord(j, i)) < robot.pos.distance(closest)
												&& (lastdug == null || !lastdug.equals(new Coord(j, i)))) {
											closest = new Coord(j, i);
											lastdug = closest;
										}
									}
								robot.action = Action.dig(closest);
							} else
								robot.action = Action.move(new Coord(board.width / 2, board.height / 2));
						}
					}

					else if (robot.id == idRobotRadar && robot.item == EntityType.RADAR)
						robot.action = Action.dig(support.thinkRadar());
					else if (robot.id == idRobotTrap && robot.item == EntityType.TRAP)
						robot.action = Action.dig(postrap);

					// robot.action = Action.none();
					// robot.action.message = "Java Starter";
				}
			} // FINE FOR

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
	private static final int RANGE = 4, UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

	public Support(Board b) {
		board = b;
		coveredByRadar = new boolean[b.height][b.width];
		forecastMatrix = new int[b.height][b.width];
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
				if (board.cellExist(new Coord(c.x + j, c.y - i)) && coveredByRadar[c.y - i][c.x + j])
					ore[UP]++;
				if (board.cellExist(new Coord(c.x - j, c.y - i)) && coveredByRadar[c.y - i][c.x - j])
					ore[UP]++;
				if (board.cellExist(new Coord(c.x + j, c.y + i)) && coveredByRadar[c.y + i][c.x + j])
					ore[DOWN]++;
				if (board.cellExist(new Coord(c.x - j, c.y + i)) && coveredByRadar[c.y + i][c.x - j])
					ore[DOWN]++;
			}
		}
		for (int i = 0; i < RANGE; i++) {
			for (int j = 0; j <= RANGE - i; j++) {
				if (board.cellExist(new Coord(c.x + i, c.y + j)) && coveredByRadar[c.y + j][c.x + i])
					ore[RIGHT]++;
				if (board.cellExist(new Coord(c.x + i, c.y - j)) && coveredByRadar[c.y - j][c.x + i])
					ore[RIGHT]++;
				if (board.cellExist(new Coord(c.x - i, c.y + j)) && coveredByRadar[c.y + j][c.x - i])
					ore[LEFT]++;
				if (board.cellExist(new Coord(c.x - i, c.y - j)) && coveredByRadar[c.y - j][c.x - i])
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
				if (proximity(visited, 3, new Coord(c.x + i, c.y))) {
					finded[RIGHT] = true;
				}
				else {
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
				}}
			}
			if (!finded[LEFT] && board.cellExist(new Coord(c.x - i, c.y)) && (c.x - i) != 0) {
				if (proximity(visited, 3, new Coord(c.x - i, c.y))) {
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
				if (proximity(visited, 3, new Coord(c.x, c.y - i))) {
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
				if (proximity(visited, 3, new Coord(c.x, c.y + i))) {
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
		Coord c = ((ArrayList<Coord>) board.myRadarPos).get(0);
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
			// C'è almeno un radar nella mappa che usiamo come punto di riferimento
			return findPos();
		}

	}

	public Coord estimate(ArrayList<Entity> enemy) {
		for (int i = 0; i < enemy.size(); i++) {
			Coord c = enemy.get(i).pos;
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

	private boolean proximity(ArrayList<Coord> visited, int range, Coord c) {
		for (int i = 1; i < range; i++) {
			if (visited.contains(new Coord(c.x + 1, c.y)) || visited.contains(new Coord(c.x - i, c.y))
					|| visited.contains(new Coord(c.x, c.y - i)) || visited.contains(new Coord(c.x, c.y + i))
					|| visited.contains(new Coord(c.x - i, c.y - i)) || visited.contains(new Coord(c.x - i, c.y + i))
					|| visited.contains(new Coord(c.x + i, c.y + i)) || visited.contains(new Coord(c.x + i, c.y - i))) {
				return true;
			}
		}
		return false;
	}

}
