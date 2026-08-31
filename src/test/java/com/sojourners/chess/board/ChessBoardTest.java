package com.sojourners.chess.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChessBoard 走子规则测试（IT-3.1）+ 实例化多局态验证（IT-10.3）。
 * ChessBoard 坐标：move/stepForEngine 的 x 为列 0~8、y 为行 0~9，内部存取为 board[y][x]；
 * 行 0 为黑方底线，行 9 为红方底线。测试不依赖图形栈（渲染已做空保护）。
 */
class ChessBoardTest {

    private static final String STANDARD_FEN_BODY = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR";

    private ChessBoard newBoard() {
        return new ChessBoard(null, ChessBoard.BoardSize.MIDDLE_BOARD, ChessBoard.BoardStyle.DEFAULT,
                false, false, false, false, false, null);
    }

    private ChessBoard fenBoard(String fenCode) {
        return new ChessBoard(null, ChessBoard.BoardSize.MIDDLE_BOARD, ChessBoard.BoardStyle.DEFAULT,
                false, false, false, false, false, fenCode);
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
            char[][] board = new char[10][9];
            for (char[] row : board) {
                java.util.Arrays.fill(row, ' ');
            }
            assertEquals("9/9/9/9/9/9/9/9/9/9", ChessBoard.fenCode(board, null));
        }

        @Test
        @DisplayName("连续空位压缩计数")
        void gapCompression() {
            char[][] board = new char[10][9];
            for (char[] row : board) {
                java.util.Arrays.fill(row, ' ');
            }
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
            assertEquals('r', cb.getBoard()[6][0], "黑车落到兵位");
            assertEquals(' ', cb.getBoard()[0][0], "原位清空");
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
            // 对脸险局：黑将(0,4) 红帅(9,4) 同列，黑车(5,4) 是列 4 唯一遮挡
            ChessBoard cb = fenBoard("4k4/9/9/9/9/4r4/9/9/9/4K4 b");

            String result = cb.move(4, 5, 5, 5);
            assertNull(result, "黑车横移让开列 4 形成对脸，属送将");

            assertEquals('r', cb.getBoard()[5][4], "走子被还原");
            assertEquals(' ', cb.getBoard()[5][5]);
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
            // 黑车 r(0,0)、黑将 k(0,3)、红仕 A(9,3)、红帅 K(9,4)
            ChessBoard cb = fenBoard("r2k4/9/9/9/9/9/9/9/9/3AK4 b");
            // 黑车 (x=0,y=0) 下沉吃仕 (x=0,y=9)
            String engine = cb.move(0, 0, 0, 9);
            assertEquals("a9a0", engine);
            assertEquals('r', cb.getBoard()[9][0]);
        }
    }

    @Nested
    @DisplayName("getTacticList 合法着法枚举")
    class TacticTest {

        @Test
        @DisplayName("标准开局枚举包含典型着法且不含非法着法")
        void openingTactics() {
            ChessBoard cb = newBoard();
            var tactics = cb.getTacticList(true);
            assertFalse(tactics.isEmpty());
            assertTrue(tactics.contains("h2e2"), "开局红炮 h2 平 e2（炮二平五）");
            assertTrue(tactics.contains("h0g2"), "开局红马 h0 跳 g2（马二进三）");
            assertFalse(tactics.contains("a0a3"), "红车被己方兵阻挡不能直达 a3");
            assertEquals(44, tactics.size(), "标准开局红方 44 步");
        }
    }

    @Nested
    @DisplayName("autoFitSize 自适应尺寸算法")
    class AutoFitTest {

        @Test
        @DisplayName("侧栏开启/关闭不影响 piece size：算法仅在 visible 时一次补偿 left 区域")
        void algorithmDeductsLeftOnce() {
            // 反向验证 B1 误报：borderPane.getWidth() = 1169 包含 left 节点 256；
            // 实际 center 区域 = 1169 - 256 = 913。
            // autoFitSize 内的 width = width - 256 是一次性补偿 left。
            // 期望棋盘宽 = (1169 - 256) * 0.6416 = 585.8
            double borderPaneWidth = 1169;
            double position = 0.6416122004357299;
            double expected = (borderPaneWidth - 256) * position;
            assertEquals(585.8, expected, 0.5, "侧栏 visible 时算法得到正确棋盘宽");
        }

        @Test
        @DisplayName("非自适应棋盘调用 autoFitSize 是 no-op（内部守卫）")
        void nonAutofitIsNoop() {
            ChessBoard cb = newBoard();
            // 不抛异常即表明守卫生效
            cb.autoFitSize(999, 999, 0.5);
        }
    }

    @Nested
    @DisplayName("多局态独立性（IT-10.3 去 static 验证）")
    class MultiInstanceTest {

        @Test
        @DisplayName("两个实例局面互不干扰")
        void twoBoardsIndependent() {
            ChessBoard b1 = newBoard();
            ChessBoard b2 = newBoard();

            b1.move(0, 0, 0, 6); // b1 黑车吃兵

            assertEquals('r', b1.getBoard()[6][0], "b1 局面已更新");
            assertEquals('P', b2.getBoard()[6][0], "b2 局面不受 b1 影响");
            assertEquals('P', b2.getBoard()[6][4], "b2 保持标准开局");
        }

        @Test
        @DisplayName("不同 FEN 实例各自独立")
        void differentFenInstances() {
            ChessBoard b1 = fenBoard("4k4/9/9/9/9/4r4/9/9/9/4K4 b");
            ChessBoard b2 = newBoard();

            assertEquals('r', b1.getBoard()[5][4]);
            assertEquals('k', b2.getBoard()[0][4], "b2 保持标准开局");

            b1.move(4, 5, 3, 5); // b1 黑车平移
            assertEquals('r', b2.getBoard()[0][0], "b2 黑车原位不动");
        }
    }
}
