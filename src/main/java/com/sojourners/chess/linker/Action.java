package com.sojourners.chess.linker;

/**
 * 连线走棋动作
 * flag： 1对方已走棋，需要同步到引擎
 *      2引擎已走棋，需要同步到目标平台
 *      3识别到新棋局
 *      4可能识别到新棋局
 * x1/x2 表示列 [0,8]，y1/y2 表示行 [0,9]
 */
public class Action {
    public int flag;
    public int x1;
    public int y1;
    public int x2;
    public int y2;

    /**
     * Constructs an action with only a flag (for new game detection or similar).
     *
     * @param flag the action type (1=opponent moved, 2=engine moved, 3=new game, 4=possibly new game)
     */
    public Action(int flag) {
        this.flag = flag;
    }

    /**
     * Constructs a move action with source and destination coordinates.
     *
     * @param flag the action type (1=opponent moved, 2=engine moved)
     * @param x1   source row [0,9]
     * @param y1   source column [0,8]
     * @param x2   destination row [0,9]
     * @param y2   destination column [0,8]
     */
    public Action(int flag, int x1, int y1, int x2, int y2) {
        this.flag = flag;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    @Override
    public String toString() {
        return "Action{" +
                "flag=" + flag +
                ", x1=" + x1 +
                ", y1=" + y1 +
                ", x2=" + x2 +
                ", y2=" + y2 +
                '}';
    }
}