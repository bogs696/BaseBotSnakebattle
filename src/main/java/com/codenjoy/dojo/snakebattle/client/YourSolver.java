package com.codenjoy.dojo.snakebattle.client;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2018 Codenjoy
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.codenjoy.dojo.services.Direction;
import com.codenjoy.dojo.services.Point;
import com.codenjoy.dojo.services.PointImpl;
import com.codenjoy.dojo.client.Solver;
import com.codenjoy.dojo.client.WebSocketRunner;
import com.codenjoy.dojo.services.Dice;
import com.codenjoy.dojo.services.RandomDice;

import com.codenjoy.dojo.services.algs.DeikstraFindWay;
import com.codenjoy.dojo.snakebattle.client.Board;

import com.codenjoy.dojo.snakebattle.client.Elements;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static com.codenjoy.dojo.services.PointImpl.pt;

/**
 * User: your name Это твой алгоритм AI для игры. Реализуй его на свое
 * усмотрение. Обрати внимание на {@see YourSolverTest} - там приготовлен
 * тестовый фреймворк для тебя.
 */
public class YourSolver implements Solver<Board> {
	/**
	 * Время действия пилюль. Так как действие пилюли начинается сразу в момент
	 * взятия, то один ход теряется впустую.
	 */
	private final int TIME_PILL = 9;
	private Dice dice;
	private DeikstraFindWay way;
	private DeikstraFindWay.Possible possible;
	private Board board;
	/**
	 * Количество съеденных и хранящихся камней
	 */
	private int amountStonesEaten;
	/**
	 * Текущий размер нашей змейки.
	 */
	private int mySize;
	/**
	 * Текущая стратегия на игру.
	 */
	private Stratege stratege;
	/**
	 * true если мы злые
	 */
	private boolean isFury;
	/**
	 * true если мы летаем.
	 */
	private boolean isFly;
	/**
	 * Номер хода на котором закончится или закончился эффект злости.
	 */
	private int endFury;
	/**
	 * Номер хода на котором закончится или закончился эффект полета.
	 */
	private int endFly;
	/**
	 * Двумерный список врагов. Первый список содержит списки врагов (можно взять
	 * количество врагов). Второй список содержит упорядоченные элементы каждого
	 * врага (голова если была найдена находится на 0 месте, хвост соответственно на
	 * последнем).
	 */
	private List<List<Point>> enemy;
	/**
	 * Подсчет количества шагов
	 */
	private static int step;
	/**
	 * Элемент который был съеден на данном ходу.
	 */
	private Elements eatenElements;
	/**
	 * Список "мертвых"(тупиковых) точек, попав в них нельзя выбраться если не
	 * использовать поворот на 180 (поворот пофиксили, пользоваться нельзя).
	 */
	private static List<Point> dethPoint;
	/**
	 * Флаг вычисляется ли в данный момент мертвые точки. На случай если мертвые
	 * точки вычисляются >1 сек что бы метод не вызывался повторно. TODO Удалить
	 * если не нужна.
	 */
	private static boolean isRunDethPoint;
	/**
	 * true что бы сбрасывать камни.
	 */
	private boolean isACT;

	public enum Stratege {
		APPLE

	}

	public YourSolver(Dice dice) {
		this.dice = dice;
		this.way = new DeikstraFindWay();
		mySize = 0;
		step = 0;
		endFly = 0;
		endFury = 0;
		eatenElements = Elements.NONE;
		isACT = true;
		dethPoint = null;
		isRunDethPoint = false;
		amountStonesEaten=0;
	}

	/**
	 * Вычисляет вражеских змеек. В случае наложения змеек они вычисляются не
	 * полностью или могут не совсем корректно вычислиться. Вычисление проводится
	 * либо по головам либо по хвостам змеек (чего больше на карте видно). Из-за
	 * этого голова(должен быть первый элемент списка) или хвост (должен быть
	 * последний элемент списка) могут быть не найдены.
	 * 
	 * @return Список вражеских змеек, каждая вражеская змейка состоит из списка
	 *         своих фрагментов. Голова находится на 0 месте, если была найдена.
	 */
	public List<List<Point>> checkEnemy() {
		List<List<Point>> returnValue = new LinkedList<>();
		List<Point> enemyHead = board.get(Elements.ENEMY_HEAD_DOWN, Elements.ENEMY_HEAD_LEFT, Elements.ENEMY_HEAD_RIGHT,
				Elements.ENEMY_HEAD_UP, Elements.ENEMY_HEAD_DEAD, Elements.ENEMY_HEAD_EVIL, Elements.ENEMY_HEAD_FLY,
				Elements.ENEMY_HEAD_SLEEP);
		List<Point> enemyTail = board.get(Elements.ENEMY_TAIL_END_DOWN, Elements.ENEMY_TAIL_END_LEFT,
				Elements.ENEMY_TAIL_END_RIGHT, Elements.ENEMY_TAIL_END_UP, Elements.ENEMY_TAIL_INACTIVE);
		boolean headBig = enemyHead.size() < enemyTail.size() ? false : true;

		int enemyAmount = headBig ? enemyHead.size() : enemyTail.size();
		if (headBig) {
			notTail: for (int i = 0; i < enemyAmount; i++) {
				returnValue.add(new LinkedList<>());
				returnValue.get(i).add(enemyHead.get(i));
				Point thisPoint;
				Elements thisElements;
				Point nextPoint;
				Elements nextElements;
				do {
					thisPoint = returnValue.get(i).get(returnValue.get(i).size() - 1);
					thisElements = board.getAt(thisPoint);

					switch (thisElements) {
					case ENEMY_HEAD_DOWN:
						nextPoint = Direction.UP.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_VERTICAL
								|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
								|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
								|| nextElements == Elements.ENEMY_TAIL_END_UP) {
							returnValue.get(i).add(nextPoint);
						} else {
							continue notTail;
						}

						break;
					case ENEMY_HEAD_UP:
						nextPoint = Direction.DOWN.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_VERTICAL || nextElements == Elements.ENEMY_BODY_LEFT_UP
								|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
								|| nextElements == Elements.ENEMY_TAIL_END_DOWN) {
							returnValue.get(i).add(nextPoint);
						} else {
							continue notTail;
						}
						break;
					case ENEMY_HEAD_LEFT:
						nextPoint = Direction.RIGHT.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
								|| nextElements == Elements.ENEMY_BODY_LEFT_UP
								|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
								|| nextElements == Elements.ENEMY_TAIL_END_RIGHT) {
							returnValue.get(i).add(nextPoint);
						} else {
							continue notTail;
						}
						break;
					case ENEMY_HEAD_RIGHT:
						nextPoint = Direction.LEFT.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
								|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
								|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
								|| nextElements == Elements.ENEMY_TAIL_END_LEFT) {
							returnValue.get(i).add(nextPoint);
						} else {
							continue notTail;
						}
						break;
					case ENEMY_HEAD_EVIL:
					case ENEMY_HEAD_FLY:
						nextPoint = Direction.UP.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_VERTICAL
								|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
								|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
								|| nextElements == Elements.ENEMY_TAIL_END_UP) {
							returnValue.get(i).add(nextPoint);
							break;
						}
						nextPoint = Direction.DOWN.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_VERTICAL || nextElements == Elements.ENEMY_BODY_LEFT_UP
								|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
								|| nextElements == Elements.ENEMY_TAIL_END_DOWN) {
							returnValue.get(i).add(nextPoint);
							break;
						}
						nextPoint = Direction.RIGHT.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
								|| nextElements == Elements.ENEMY_BODY_LEFT_UP
								|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
								|| nextElements == Elements.ENEMY_TAIL_END_RIGHT) {
							returnValue.get(i).add(nextPoint);
							break;
						}
						nextPoint = Direction.LEFT.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
								|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
								|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
								|| nextElements == Elements.ENEMY_TAIL_END_LEFT) {
							returnValue.get(i).add(nextPoint);
							break;
						}
						continue notTail;
					case ENEMY_BODY_HORIZONTAL:
						int varX = returnValue.get(i).get(returnValue.get(i).size() - 2).getX() - thisPoint.getX();
						boolean left = varX < 0;
						if (left) {
							nextPoint = Direction.RIGHT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_TAIL_END_RIGHT) {
								returnValue.get(i).add(nextPoint);
								break;
							}

						} else {
							nextPoint = Direction.LEFT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_TAIL_END_LEFT) {
								returnValue.get(i).add(nextPoint);
								break;
							}
						}

						continue notTail;
					case ENEMY_BODY_VERTICAL:
						int varY = returnValue.get(i).get(returnValue.get(i).size() - 2).getY() - thisPoint.getY();
						boolean up = varY < 0;
						if (up) {
							nextPoint = Direction.UP.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_TAIL_END_UP) {
								returnValue.get(i).add(nextPoint);
								break;
							}
						} else {
							nextPoint = Direction.DOWN.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_TAIL_END_DOWN) {
								returnValue.get(i).add(nextPoint);
								break;
							}

						}
						continue notTail;
					case ENEMY_BODY_LEFT_DOWN:

						if (returnValue.get(i).get(returnValue.get(i).size() - 2).getX() - thisPoint.getX() < 0) {
							nextPoint = Direction.DOWN.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_TAIL_END_DOWN) {
								returnValue.get(i).add(nextPoint);
								break;
							}
						} else {
							nextPoint = Direction.LEFT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_TAIL_END_LEFT) {
								returnValue.get(i).add(nextPoint);
								break;
							}

						}
						continue notTail;
					case ENEMY_BODY_LEFT_UP:
						if (returnValue.get(i).get(returnValue.get(i).size() - 2).getX() - thisPoint.getX() < 0) {
							nextPoint = Direction.UP.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_TAIL_END_UP) {
								returnValue.get(i).add(nextPoint);
								break;
							}
						} else {
							nextPoint = Direction.LEFT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_TAIL_END_LEFT) {
								returnValue.get(i).add(nextPoint);
								break;
							}

						}
						// TODO если произошло накладывание
						continue notTail;
					// break;
					case ENEMY_BODY_RIGHT_DOWN:
						if (returnValue.get(i).get(returnValue.get(i).size() - 2).getX() - thisPoint.getX() > 0) {
							nextPoint = Direction.DOWN.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_TAIL_END_DOWN) {
								returnValue.get(i).add(nextPoint);
								break;
							}
						} else {
							nextPoint = Direction.RIGHT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_TAIL_END_RIGHT) {
								returnValue.get(i).add(nextPoint);
								break;
							}

						}
						continue notTail;
					case ENEMY_BODY_RIGHT_UP:
						if (returnValue.get(i).get(returnValue.get(i).size() - 2).getX() - thisPoint.getX() > 0) {
							nextPoint = Direction.UP.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_TAIL_END_UP) {
								returnValue.get(i).add(nextPoint);
								break;
							}
						} else {

							nextPoint = Direction.RIGHT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_TAIL_END_RIGHT) {
								returnValue.get(i).add(nextPoint);
								break;
							}
						}
						continue notTail;


					default:
						break;
					}
				} while (thisElements != Elements.ENEMY_TAIL_END_DOWN && thisElements != Elements.ENEMY_TAIL_END_LEFT
						&& thisElements != Elements.ENEMY_TAIL_END_UP && thisElements != Elements.ENEMY_TAIL_END_RIGHT
						&& thisElements != Elements.ENEMY_TAIL_INACTIVE && thisElements != Elements.ENEMY_HEAD_DEAD
						&& thisElements != Elements.ENEMY_HEAD_SLEEP);
			}
		} else {
			notHead: for (int i = 0; i < enemyAmount; i++) {
				returnValue.add(new LinkedList<>());
				returnValue.get(i).add(enemyTail.get(i));
				Point thisPoint;
				Elements thisElements;
				Point nextPoint;
				Elements nextElements;
				do {
					thisPoint = returnValue.get(i).get(0);
					thisElements = board.getAt(thisPoint);

					switch (thisElements) {
					case ENEMY_TAIL_END_DOWN:
						nextPoint = Direction.UP.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_VERTICAL
								|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
								|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
								|| nextElements == Elements.ENEMY_HEAD_UP || nextElements == Elements.ENEMY_HEAD_EVIL
								|| nextElements == Elements.ENEMY_HEAD_FLY) {
							returnValue.get(i).add(0, nextPoint);
						} else {
							continue notHead;
						}

						break;
					case ENEMY_TAIL_END_UP:
						nextPoint = Direction.DOWN.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_VERTICAL || nextElements == Elements.ENEMY_BODY_LEFT_UP
								|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
								|| nextElements == Elements.ENEMY_HEAD_DOWN || nextElements == Elements.ENEMY_HEAD_EVIL
								|| nextElements == Elements.ENEMY_HEAD_FLY) {
							returnValue.get(i).add(0, nextPoint);
						} else {
							continue notHead;
						}
						break;
					case ENEMY_TAIL_END_LEFT:
						nextPoint = Direction.RIGHT.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
								|| nextElements == Elements.ENEMY_BODY_LEFT_UP
								|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
								|| nextElements == Elements.ENEMY_HEAD_RIGHT || nextElements == Elements.ENEMY_HEAD_EVIL
								|| nextElements == Elements.ENEMY_HEAD_FLY) {
							returnValue.get(i).add(0, nextPoint);
						} else {
							// TODO если произошло накладывание
							continue notHead;
						}
						break;
					case ENEMY_TAIL_END_RIGHT:
						nextPoint = Direction.LEFT.change(thisPoint);
						nextElements = board.getAt(nextPoint);
						if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
								|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
								|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
								|| nextElements == Elements.ENEMY_HEAD_LEFT || nextElements == Elements.ENEMY_HEAD_EVIL
								|| nextElements == Elements.ENEMY_HEAD_FLY) {
							returnValue.get(i).add(0, nextPoint);
						} else {
							continue notHead;
						}
						break;
					case ENEMY_BODY_HORIZONTAL:
						int varX = returnValue.get(i).get(0 + 1).getX() - thisPoint.getX();
						boolean left = varX < 0;
						if (left) {
							nextPoint = Direction.RIGHT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_HEAD_RIGHT
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}

						} else {
							nextPoint = Direction.LEFT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_HEAD_LEFT
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}
						}

						continue notHead;
					case ENEMY_BODY_VERTICAL:
						int varY = returnValue.get(i).get(0 + 1).getY() - thisPoint.getY();
						boolean up = varY < 0;
						if (up) {
							nextPoint = Direction.UP.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_HEAD_UP
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}
						} else {
							nextPoint = Direction.DOWN.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_HEAD_DOWN
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}

						}
						continue notHead;
					case ENEMY_BODY_LEFT_DOWN:

						if (returnValue.get(i).get(0 + 1).getX() - thisPoint.getX() < 0) {
							nextPoint = Direction.DOWN.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_HEAD_DOWN
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}
						} else {
							nextPoint = Direction.LEFT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_HEAD_LEFT
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}

						}
						continue notHead;
					case ENEMY_BODY_LEFT_UP:
						if (returnValue.get(i).get(0 + 1).getX() - thisPoint.getX() < 0) {
							nextPoint = Direction.UP.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_HEAD_UP
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}
						} else {
							nextPoint = Direction.LEFT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_HEAD_LEFT
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}

						}
						continue notHead;
					case ENEMY_BODY_RIGHT_DOWN:
						if (returnValue.get(i).get(0 + 1).getX() - thisPoint.getX() > 0) {
							nextPoint = Direction.DOWN.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_RIGHT_UP
									|| nextElements == Elements.ENEMY_HEAD_DOWN
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}
						} else {
							nextPoint = Direction.RIGHT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_HEAD_RIGHT
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}

						}
						continue notHead;
					case ENEMY_BODY_RIGHT_UP:
						if (returnValue.get(i).get(0 + 1).getX() - thisPoint.getX() > 0) {
							nextPoint = Direction.UP.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_VERTICAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_BODY_RIGHT_DOWN
									|| nextElements == Elements.ENEMY_HEAD_UP
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}
						} else {

							nextPoint = Direction.RIGHT.change(thisPoint);
							nextElements = board.getAt(nextPoint);
							if (nextElements == Elements.ENEMY_BODY_HORIZONTAL
									|| nextElements == Elements.ENEMY_BODY_LEFT_UP
									|| nextElements == Elements.ENEMY_BODY_LEFT_DOWN
									|| nextElements == Elements.ENEMY_HEAD_RIGHT
									|| nextElements == Elements.ENEMY_HEAD_EVIL
									|| nextElements == Elements.ENEMY_HEAD_FLY) {
								returnValue.get(i).add(0, nextPoint);
								break;
							}
						}
						continue notHead;


					default:
						break;
					}
				} while (thisElements != Elements.ENEMY_TAIL_INACTIVE && thisElements != Elements.ENEMY_HEAD_DOWN
						&& thisElements != Elements.ENEMY_HEAD_LEFT && thisElements != Elements.ENEMY_HEAD_RIGHT
						&& thisElements != Elements.ENEMY_HEAD_UP && thisElements != Elements.ENEMY_HEAD_DEAD
						&& thisElements != Elements.ENEMY_HEAD_EVIL && thisElements != Elements.ENEMY_HEAD_FLY
						&& thisElements != Elements.ENEMY_HEAD_SLEEP);
			}
		}
		return returnValue;
	}

	public DeikstraFindWay.Possible possible(final Board board) {
		return new DeikstraFindWay.Possible() {
			@Override
			public boolean possible(Point from, Direction where) {
				int x = from.getX();
				int y = from.getY();
				if (isBarrierAt(x, y))
					return false;

				Point newPt = where.change(from);
				int nx = newPt.getX();
				int ny = newPt.getY();

				if (board.isOutOfField(nx, ny)) {
					return false;
				}

				if (isBarrierAt(nx, ny)) {
					return false;
				}

				return true;
			}

			@Override
			public boolean possible(Point atWay) {
				return true;
			}
		};
	}

	/**
	 * Определяет находится ли на точке преграда для нас.
	 * 
	 * @param x
	 * @param y
	 * @return true если это преграда.
	 */
	private boolean isBarrierAt(int x, int y) {
		if (dethPoint != null) {
			for (Point point : dethPoint) {
				if (x == point.getX() && y == point.getY()) {
					return true;
				}
			}
		}
		// Свои части
		
		if (board.isAt(x, y,Elements.TAIL_END_DOWN, Elements.TAIL_END_LEFT, Elements.TAIL_END_RIGHT, Elements.TAIL_END_UP,
				Elements.TAIL_INACTIVE, Elements.BODY_HORIZONTAL, Elements.BODY_VERTICAL, Elements.BODY_LEFT_DOWN,
				Elements.BODY_LEFT_UP, Elements.BODY_RIGHT_DOWN, Elements.BODY_RIGHT_UP)) {
			return true;
		}
		switch (stratege) {
		case APPLE:
			return board.isAt(x, y, Elements.WALL, Elements.STONE, Elements.START_FLOOR, Elements.ENEMY_HEAD_DOWN,
					Elements.ENEMY_HEAD_LEFT, Elements.ENEMY_HEAD_RIGHT, Elements.ENEMY_HEAD_UP,
					Elements.ENEMY_HEAD_DEAD, Elements.ENEMY_HEAD_EVIL, Elements.ENEMY_HEAD_FLY,
					Elements.ENEMY_HEAD_SLEEP, Elements.ENEMY_TAIL_END_DOWN, Elements.ENEMY_TAIL_END_LEFT,
					Elements.ENEMY_TAIL_END_UP, Elements.ENEMY_TAIL_END_RIGHT, Elements.ENEMY_TAIL_INACTIVE,
					Elements.ENEMY_BODY_HORIZONTAL, Elements.ENEMY_BODY_VERTICAL, Elements.ENEMY_BODY_LEFT_DOWN,
					Elements.ENEMY_BODY_LEFT_UP, Elements.ENEMY_BODY_RIGHT_DOWN, Elements.ENEMY_BODY_RIGHT_UP);
		
		default:
			
		}
		return false;

	}

	/**
	 * Вычисляет за какими объектами идет охота
	 * 
	 * @return Список объектов за которыми охота
	 */
	private List<Point> searchTarget() {
		List<Point> returnValue = new LinkedList<Point>();
		switch (stratege) {
		case APPLE:
			returnValue.addAll(board.get(Elements.APPLE));
			break;
		
		default:
			returnValue.addAll(board.get(Elements.APPLE));
			break;
		}
		return returnValue;

	}

	public List<Direction> getWay() {
		possible = possible(board);

		Point from = board.getMe();
		List<Point> to = searchTarget();
		List<Direction> way = getWay(from, to);
		if (way.isEmpty()) {
			int distance = 0;
			Point longest = null;
			for (int x = 0; x < board.size(); x++) {
				for (int y = 0; y < board.size(); y++) {
					if (isBarrierAt(x, y))
						continue;
					Point pt = pt(x, y);
					way = this.way.getShortestWay(board.size(), from, Arrays.asList(pt), possible);
					if (distance < way.size()) {
						distance = way.size();
						longest = pt;
					}
				}
			}
			way = getWay(from, Arrays.asList(longest));
		}
		return way;
	}

	private List<Direction> getWay(Point from, List<Point> to) {
		return this.way.getShortestWay(board.size(), from, to, possible);
	}

	/**
	 * Ищет "мертвые" (тупиковые) точки основываясь на каждой не-стене клетке.
	 * 
	 * @deprecated Слишком долгая работа. Используйте {@link #new_dethPoint()}
	 * 
	 * @return Список "мертвых" точек.
	 */
	@Deprecated
	private List<Point> dethPoint() {
		List<Point> returnValue = new LinkedList<Point>();
		List<Point> freePoint = board.get(Elements.NONE, Elements.APPLE, Elements.STONE, Elements.FLYING_PILL,
				Elements.FURY_PILL, Elements.GOLD, Elements.HEAD_DOWN, Elements.HEAD_LEFT, Elements.HEAD_RIGHT,
				Elements.HEAD_UP, Elements.HEAD_DEAD, Elements.HEAD_EVIL, Elements.HEAD_FLY, Elements.HEAD_SLEEP,
				Elements.TAIL_END_DOWN, Elements.TAIL_END_LEFT, Elements.TAIL_END_UP, Elements.TAIL_END_RIGHT,
				Elements.TAIL_INACTIVE, Elements.BODY_HORIZONTAL, Elements.BODY_VERTICAL, Elements.BODY_LEFT_DOWN,
				Elements.BODY_LEFT_UP, Elements.BODY_RIGHT_DOWN, Elements.BODY_RIGHT_UP, Elements.ENEMY_HEAD_DOWN,
				Elements.ENEMY_HEAD_LEFT, Elements.ENEMY_HEAD_RIGHT, Elements.ENEMY_HEAD_UP, Elements.ENEMY_HEAD_DEAD,
				Elements.ENEMY_HEAD_EVIL, Elements.ENEMY_HEAD_FLY, Elements.ENEMY_HEAD_SLEEP,
				Elements.ENEMY_TAIL_END_DOWN, Elements.ENEMY_TAIL_END_LEFT, Elements.ENEMY_TAIL_END_UP,
				Elements.ENEMY_TAIL_END_RIGHT, Elements.ENEMY_TAIL_INACTIVE, Elements.ENEMY_BODY_HORIZONTAL,
				Elements.ENEMY_BODY_VERTICAL, Elements.ENEMY_BODY_LEFT_DOWN, Elements.ENEMY_BODY_LEFT_UP,
				Elements.ENEMY_BODY_RIGHT_DOWN, Elements.ENEMY_BODY_RIGHT_UP);

		int[] lockPoints = new int[freePoint.size()];
		boolean[] checkepPoints = new boolean[freePoint.size()];

		for (int i = 0; i < lockPoints.length; i++) {
			lockPoints[i] += 3;
			checkDethPoint(freePoint, lockPoints, checkepPoints, i);

		}
		for (int i = 0; i < lockPoints.length; i++) {
			if (lockPoints[i] <= 0) {
				returnValue.add(freePoint.get(i));
			}
		}

		return returnValue;
	}

	/**
	 * @Deprecated Слишком долгая работа. Использовался для {@link #dethPoint()}
	 * @param freePoint
	 * @param lockPoints
	 * @param checkepPoints
	 * @param index
	 */
	@Deprecated
	private void checkDethPoint(List<Point> freePoint, int[] lockPoints, boolean[] checkepPoints, int index) {
		Point varPoint = freePoint.get(index);
		boolean leftWall = board.isAt(Direction.LEFT.change(varPoint), Elements.WALL, Elements.START_FLOOR);
		boolean upWall = board.isAt(Direction.LEFT.change(varPoint), Elements.WALL, Elements.START_FLOOR);
		boolean rightWall = board.isAt(Direction.RIGHT.change(varPoint), Elements.WALL, Elements.START_FLOOR);
		boolean downWall = board.isAt(Direction.DOWN.change(varPoint), Elements.WALL, Elements.START_FLOOR);
		if (!checkepPoints[index]) {
			if (leftWall) {
				lockPoints[index]--;
			}
			if (upWall) {
				lockPoints[index]--;
			}
			if (rightWall) {
				lockPoints[index]--;
			}
			if (downWall) {
				lockPoints[index]--;
			}
			checkepPoints[index] = true;
		}

		if (lockPoints[index] <= 0) {
			if (!leftWall) {
				int indexBlockPoint = freePoint.indexOf(Direction.LEFT.change(varPoint));
				if (indexBlockPoint != -1) {
					lockPoints[indexBlockPoint]--;
					checkDethPoint(freePoint, lockPoints, checkepPoints, indexBlockPoint);
				}

			}
			if (!upWall) {
				int indexBlockPoint = freePoint.indexOf(Direction.UP.change(varPoint));
				if (indexBlockPoint != -1) {
					lockPoints[indexBlockPoint]--;
					checkDethPoint(freePoint, lockPoints, checkepPoints, indexBlockPoint);
				}
			}
			if (!rightWall) {
				int indexBlockPoint = freePoint.indexOf(Direction.RIGHT.change(varPoint));
				if (indexBlockPoint != -1) {
					lockPoints[indexBlockPoint]--;
					checkDethPoint(freePoint, lockPoints, checkepPoints, indexBlockPoint);
				}
			}
			if (!downWall) {
				int indexBlockPoint = freePoint.indexOf(Direction.DOWN.change(varPoint));
				if (indexBlockPoint != -1) {
					lockPoints[indexBlockPoint]--;
					checkDethPoint(freePoint, lockPoints, checkepPoints, indexBlockPoint);
				}
			}
		}

	}

	/**
	 * Ищет "мертвые" (тупиковые) точки основываясь на стенах.
	 * 
	 * @return Список "мертвых" точек.
	 */
	private List<Point> new_dethPoint() {
		List<Point> returnValue = new LinkedList<Point>();
		List<Point> busyPoint = board.get(Elements.WALL, Elements.START_FLOOR, Elements.HEAD_SLEEP,
				Elements.TAIL_INACTIVE, Elements.ENEMY_HEAD_SLEEP, Elements.ENEMY_TAIL_INACTIVE);
		int[] countDethPoint = new int[board.size() * board.size()];
		boolean[] isCountDethPoint = new boolean[board.size() * board.size()];
		boolean varX, varY;
		for (Point point : busyPoint) {
			varX = Direction.LEFT.change(point).getX() < 0 ? false : true;
			varX = Direction.LEFT.change(point).getX() >= 30 ? false : varX;
			varY = Direction.LEFT.change(point).getY() < 0 ? false : true;
			varY = Direction.LEFT.change(point).getY() >= 30 ? false : varY;
			if (varX && varY
					&& !board.isAt(Direction.LEFT.change(point), Elements.WALL, Elements.START_FLOOR,
							Elements.HEAD_SLEEP, Elements.TAIL_INACTIVE, Elements.ENEMY_HEAD_SLEEP,
							Elements.ENEMY_TAIL_INACTIVE)) {
				countDethPoint[Direction.LEFT.change(point).getX() * board.size()
						+ Direction.LEFT.change(point).getY()]++;

			}
			varX = Direction.UP.change(point).getX() < 0 ? false : true;
			varX = Direction.UP.change(point).getX() >= 30 ? false : varX;
			varY = Direction.UP.change(point).getY() < 0 ? false : true;
			varY = Direction.UP.change(point).getY() >= 30 ? false : varY;
			if (varX && varY
					&& !board.isAt(Direction.UP.change(point), Elements.WALL, Elements.START_FLOOR, Elements.HEAD_SLEEP,
							Elements.TAIL_INACTIVE, Elements.ENEMY_HEAD_SLEEP, Elements.ENEMY_TAIL_INACTIVE)) {
				countDethPoint[Direction.UP.change(point).getX() * board.size() + Direction.UP.change(point).getY()]++;

			}
			varX = Direction.RIGHT.change(point).getX() < 0 ? false : true;
			varX = Direction.RIGHT.change(point).getX() >= 30 ? false : varX;
			varY = Direction.RIGHT.change(point).getY() < 0 ? false : true;
			varY = Direction.RIGHT.change(point).getY() >= 30 ? false : varY;
			if (varX && varY
					&& !board.isAt(Direction.RIGHT.change(point), Elements.WALL, Elements.START_FLOOR,
							Elements.HEAD_SLEEP, Elements.TAIL_INACTIVE, Elements.ENEMY_HEAD_SLEEP,
							Elements.ENEMY_TAIL_INACTIVE)) {

				countDethPoint[Direction.RIGHT.change(point).getX() * board.size()
						+ Direction.RIGHT.change(point).getY()]++;

			}
			varX = Direction.DOWN.change(point).getX() < 0 ? false : true;
			varX = Direction.DOWN.change(point).getX() >= 30 ? false : varX;
			varY = Direction.DOWN.change(point).getY() < 0 ? false : true;
			varY = Direction.DOWN.change(point).getY() >= 30 ? false : varY;
			if (varX && varY
					&& !board.isAt(Direction.DOWN.change(point), Elements.WALL, Elements.START_FLOOR,
							Elements.HEAD_SLEEP, Elements.TAIL_INACTIVE, Elements.ENEMY_HEAD_SLEEP,
							Elements.ENEMY_TAIL_INACTIVE)) {
				countDethPoint[Direction.DOWN.change(point).getX() * board.size()
						+ Direction.DOWN.change(point).getY()]++;

			}
		}
		boolean flag = true;
		do {
			for (int i = 0; i < countDethPoint.length; i++) {
				flag = false;
				if (countDethPoint[i] >= 3 && !isCountDethPoint[i]) {
					flag = true;
					isCountDethPoint[i] = true;
					Point point = PointImpl.pt(i / 30, i % 30);
					returnValue.add(point);
					varX = Direction.LEFT.change(point).getX() < 0 ? false : true;
					varX = Direction.LEFT.change(point).getX() >= 30 ? false : varX;
					varY = Direction.LEFT.change(point).getY() < 0 ? false : true;
					varY = Direction.LEFT.change(point).getY() >= 30 ? false : varY;
					if (varX && varY
							&& !board.isAt(Direction.LEFT.change(point), Elements.WALL, Elements.START_FLOOR,
									Elements.HEAD_SLEEP, Elements.TAIL_INACTIVE, Elements.ENEMY_HEAD_SLEEP,
									Elements.ENEMY_TAIL_INACTIVE)) {
						countDethPoint[Direction.LEFT.change(point).getX() * board.size()
								+ Direction.LEFT.change(point).getY()]++;

					}
					varX = Direction.UP.change(point).getX() < 0 ? false : true;
					varX = Direction.UP.change(point).getX() >= 30 ? false : varX;
					varY = Direction.UP.change(point).getY() < 0 ? false : true;
					varY = Direction.UP.change(point).getY() >= 30 ? false : varY;
					if (varX && varY
							&& !board.isAt(Direction.UP.change(point), Elements.WALL, Elements.START_FLOOR,
									Elements.HEAD_SLEEP, Elements.TAIL_INACTIVE, Elements.ENEMY_HEAD_SLEEP,
									Elements.ENEMY_TAIL_INACTIVE)) {
						countDethPoint[Direction.UP.change(point).getX() * board.size()
								+ Direction.UP.change(point).getY()]++;

					}
					varX = Direction.RIGHT.change(point).getX() < 0 ? false : true;
					varX = Direction.RIGHT.change(point).getX() >= 30 ? false : varX;
					varY = Direction.RIGHT.change(point).getY() < 0 ? false : true;
					varY = Direction.RIGHT.change(point).getY() >= 30 ? false : varY;
					if (varX && varY
							&& !board.isAt(Direction.RIGHT.change(point), Elements.WALL, Elements.START_FLOOR,
									Elements.HEAD_SLEEP, Elements.TAIL_INACTIVE, Elements.ENEMY_HEAD_SLEEP,
									Elements.ENEMY_TAIL_INACTIVE)) {
						countDethPoint[Direction.RIGHT.change(point).getX() * board.size()
								+ Direction.RIGHT.change(point).getY()]++;

					}
					varX = Direction.DOWN.change(point).getX() < 0 ? false : true;
					varX = Direction.DOWN.change(point).getX() >= 30 ? false : varX;
					varY = Direction.DOWN.change(point).getY() < 0 ? false : true;
					varY = Direction.DOWN.change(point).getY() >= 30 ? false : varY;
					if (varX && varY
							&& !board.isAt(Direction.DOWN.change(point), Elements.WALL, Elements.START_FLOOR,
									Elements.HEAD_SLEEP, Elements.TAIL_INACTIVE, Elements.ENEMY_HEAD_SLEEP,
									Elements.ENEMY_TAIL_INACTIVE)) {
						countDethPoint[Direction.DOWN.change(point).getX() * board.size()
								+ Direction.DOWN.change(point).getY()]++;

					}
				}
			}
		} while (flag);
		return returnValue;
	}

	/**
	 * Анализирует игру и выводит какую стратегию использовать в данный ход.
	 * 
	 * @return Выбранная стратегия.
	 */
	private Stratege chooseStratege() {

		return Stratege.APPLE;
	}

	/**
	 * Проверяет состояние борда и записывает в соответствующие переменные
	 * резульаты.
	 */
	private void checkStatusBoard() {
		/**** Вычисляется наш размер ****/
		mySize = board.get(Elements.HEAD_DOWN, Elements.HEAD_EVIL, Elements.HEAD_FLY, Elements.HEAD_LEFT,
				Elements.HEAD_RIGHT, Elements.HEAD_UP, Elements.TAIL_END_DOWN, Elements.TAIL_END_LEFT,
				Elements.TAIL_END_UP, Elements.TAIL_END_RIGHT, Elements.BODY_HORIZONTAL, Elements.BODY_VERTICAL,
				Elements.BODY_LEFT_DOWN, Elements.BODY_LEFT_UP, Elements.BODY_RIGHT_DOWN, Elements.BODY_RIGHT_UP)
				.size();
		/**** Вычисляются противники ****/
		enemy = checkEnemy();
		/****
		 * Вычисляется съели мы в этот ход таблетку, если да записываем время действия
		 * (или увеличиваем его так как действие сумируется)
		 * Если съели камень увеличиваем счетчик камня на 1
		 ****/
		switch (eatenElements) {
		case FURY_PILL:
			endFury = endFury > step ? endFury + TIME_PILL : step + TIME_PILL;
			break;
		case FLYING_PILL:
			endFly = endFly > step ? endFly + TIME_PILL : step + TIME_PILL;
			break;
		case STONE:
			amountStonesEaten++;

		default:
			break;
		}
		/**** Вычисляется находимся ли мы сейчас под действием какой либо таблетки ****/
		if (endFury >= step) {
			isFury = true;
		} else {
			isFury = false;
		}
		if (endFly >= step) {
			isFly = true;
		} else {
			isFly = false;
		}
		/****
		 * Вычисляются мертвые точки. Один раз за раунд, так как карта меняется (будет
		 * меняться) только после завершения текущего раунда. Так в 1 и 2 ход происходит
		 * наложение змейки когда она выходит из стартовой точки, то вычисление на 3+
		 * ходе.
		 ****/
		if (dethPoint == null && !isRunDethPoint && step > 2) {
			isRunDethPoint = true;
			dethPoint = new_dethPoint();
			isRunDethPoint = false;
		}
		/**** Вывод интересующих параметров в консоль ****/
		
	}

	@Override
	public String get(final Board board) {
		this.board = board;
		if (board.isGameOver()) {
			return "";
		}
		if (board.getAt(board.getMe()) == Elements.HEAD_SLEEP) {
			step = -1;
			endFly = 0;
			endFury = 0;
			amountStonesEaten=0;
			dethPoint = null;

		} else {
			step++;
		}
		checkStatusBoard();
		stratege = chooseStratege();
		List<Direction> result = getWay();
		if (result.isEmpty())
			return "";
		eatenElements = board.getAt(result.get(0).change(board.getMe()));
		if(isACT && amountStonesEaten>0) {
			amountStonesEaten--;
		}
		return result.get(0).ACT(isACT);
	}

	public static void main(String[] args) {
		WebSocketRunner.runClient(
				// paste here board page url from browser after registration
				"https://game1.epam-bot-challenge.com.ua/codenjoy-contest/board/player/YOU@MAIL?code=YOU_CODE",
				new YourSolver(new RandomDice()), new Board());
	}

}
