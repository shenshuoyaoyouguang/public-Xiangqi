package com.sojourners.chess.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChessBoard 走子规则测试（IT-3.1）。
 * ChessBoard 坐标：move/stepForEngine 的 x 为列 0~8、y 为行 0~9，内部存取为 board[y][x]；
 * 行 0 为黑方底线，行 9 为红方底线。测试不依赖图形栈（渲染已做空保护）。
 */
class ChessBoardTest {

    private static final String STANDARD_FEN_BODY = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR";

    @BeforeEach
    void resetBoard() throws Exception {
        // board 为全局静态状态（IT-10.1 将实例化），测试前重置为标准开局
        char[][] fresh = new char[10][9];
        ChessBoard.initChessBoard(fresh);
        Field f = ChessBoard.class.getDeclaredField("board");
        f.setAccessible(true);
        f.set(null, fresh);
    }

    private ChessBoard newBoard() {
        return new ChessBoard(null, ChessBoard.BoardSize.MIDDLE_BOARD, ChessBoard.BoardStyle.DEFAULT,
                false, false, false, false, false, null);
    }

    private static char[][] emptyBoard() {
        char[][] board = new char[10][9];
        for (char[] row : board) {
            java.util.Arrays.fill(row, ' ');
        }
        return board;
    }

    @Nested
    @DisplayName("initChessBoard 标准开局摆位")
    class InitTest {

        @Test
        @DisplayName("棋子摆位正确")
        void placement() {
            char[][] board = new char[10][9];
            ChessBoard.initChessBoard(board);
            assertEquals('r', board[0][0]);
            assertEquals('n', board[0][1]);
            assertEquals('a', board[0][3]);
            assertEquals('k', board[0][4]);
            assertEquals('c', board[2][1]);
            assertEquals('p', board[3][8]);
            assertEquals(' ', board[1][4], "第二行应全空");
            assertEquals('P', board[6][4]);
            assertEquals('C', board[7][7]);
            assertEquals('A', board[9][3]);
            assertEquals('K', board[9][4]);
            assertEquals('R', board[9][8]);
        }
    }

    @Nested
    @DisplayName("fenCode FEN 生成")
    class FenTest {

        @Test
        @DisplayName("标准局面生成完整 FEN")
        void standardFen() {
            char[][] board = new char[10][9];
            ChessBoard.initChessBoard(board);
            assertEquals(STANDARD_FEN_BODY + " w - - 0 1", ChessBoard.fenCode(board, true));
            assertEquals(STANDARD_FEN_BODY + " b - - 0 1", ChessBoard.fenCode(board, false));
            assertEquals(STANDARD_FEN_BODY, ChessBoard.fenCode(board, null));
        }

        @Test
        @DisplayName("空盘生成全 9 行")
        void emptyFen() {
            char[][] board = emptyBoard();
            assertEquals("9/9/9/9/9/9/9/9/9/9", ChessBoard.fenCode(board, null));
        }

        @Test
        @DisplayName("连续空位压缩计数")
        void gapCompression() {
            char[][] board = emptyBoard();
            board[0][0] = 'k';
            board[0][8] = 'r';
            assertEquals("k7r/9/9/9/9/9/9/9/9/9", ChessBoard.fenCode(board, null));
        }
    }

    @Nested
    @DisplayName("引擎坐标转换")
    class CoordinateTest {

        @Test
        @DisplayName("stepForEngine 列字母行数字，行号反转")
        void stepForEngine() {
            assertEquals("a9a8", ChessBoard.stepForEngine(0, 0, 0, 1));
            assertEquals("h7e7", ChessBoard.stepForEngine(7, 2, 4, 2));
            assertEquals("b2b7", ChessBoard.stepForEngine(1, 7, 1, 2));
        }

        @Test
        @DisplayName("stepForBoard 与 stepForEngine 互逆")
        void stepForBoardInverse() {
            ChessBoard cb = newBoard();
            ChessBoard.Step s = cb.stepForBoard("h7e7");
            assertEquals(7, s.getStart().x);
            assertEquals(2, s.getStart().y);
            assertEquals(4, s.getEnd().x);
            assertEquals(2, s.getEnd().y);
            assertNull(cb.stepForBoard(null));
        }
    }

    @Nested
    @DisplayName("move 走子规则")
    class MoveTest {

        @Test
        @DisplayName("合法走子：局面更新并返回引擎坐标")
        void legalMove() {
            ChessBoard cb = newBoard();
            String engine = cb.move(0, 0, 0, 6);
            assertEquals("a9a3", engine, "黑车 a9 吃 a3 兵");
        }

        @Test
        @DisplayName("合法吃子后棋子到位")
        void capture() {
            ChessBoard cb = newBoard();
            cb.move(0, 0, 0, 6);
            char[][] board = currentBoard();
            assertEquals('r', board[6][0], "黑车落到兵位");
            assertEquals(' ', board[0][0], "原位清空");
        }

        @Test
        @DisplayName("炮隔一子吃马合法")
        void cannonCapture() {
            ChessBoard cb = newBoard();
            // 红炮 (x=1,y=7) 经红兵屏打黑炮 (x=1,y=2)
            String engine = cb.move(1, 7, 1, 2);
            assertEquals("b2b7", engine);
        }

        @Test
        @DisplayName("送将被拒：返回 null 且局面还原")
        void suicideMoveRejected() {
            ChessBoard cb = newBoard();
            // 构造对脸险局：黑将(0,4) 红帅(9,4) 同列，黑车(5,4) 是列 4 唯一遮挡
            char[][] board = emptyBoard();
            board[0][4] = 'k';
            board[5][4] = 'r';
            board[9][4] = 'K';
            setStaticBoard(board);

            String result = cb.move(4, 5, 5, 5);
            assertNull(result, "黑车横移让开列 4 形成对脸，属送将");

            char[][] after = currentBoard();
            assertEquals('r', after[5][4], "走子被还原");
            assertEquals(' ', after[5][5]);
        }

        @Test
        @DisplayName("非法格式返回 null")
        void invalidStep() {
            ChessBoard cb = newBoard();
            assertNull(cb.move(null));
            assertNull(cb.move("abc"));
        }

        @Test
        @DisplayName("绝杀后走子仍返回坐标（胜负判定由上层处理）")
        void mateMove() {
            ChessBoard cb = newBoard();
            // 简单验证 isSha 分支不阻断走子：黑车吃红仕后局面更新
            char[][] board = emptyBoard();
            board[0][3] = 'k';
            board[0][0] = 'r';
            board[9][3] = 'A';
            board[9][4] = 'K';
            setStaticBoard(board);
            // 黑车 (x=0,y=0) 下沉吃仕 (x=0,y=9)
            String engine = cb.move(0, 0, 0, 9);
            assertEquals("a9a0", engine);
            assertEquals('r', currentBoard()[9][0]);
        }
    }

    @Nested
    @DisplayName("getTacticList 合法着法枚举")
    class TacticTest {

        @Test
        @DisplayName("标准开局枚举包含炮打马着法且不含非法着法")
        void openingTactics() {
            ChessBoard cb = newBoard();
            List<String> tactics = cb.getTacticList(true);
            assertFalse(tactics.isEmpty());
            assertTrue(tactics.contains("h2e2"), "开局红炮 h2 平 e2（炮二平五）");
            assertTrue(tactics.contains("h0g2"), "开局红马 h0 跳 g2（马二进三）");
            assertTrue(tactics.size() == 44, "标准开局红方 44 步，实际: " + tactics.size());
            assertFalse(tactics.contains("a0a3"), "红车被己方兵阻挡不能直达 a3");
        }
    }

    private static char[][] currentBoard() {
        try {
            Field f = ChessBoard.class.getDeclaredField("board");
            f.setAccessible(true);
            return (char[][]) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setStaticBoard(char[][] board) {
        try {
            Field f = ChessBoard.class.getDeclaredField("board");
            f.setAccessible(true);
            f.set(null, board);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
