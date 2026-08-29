package com.sojourners.chess.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XiangqiUtils 规则测试（IT-2.2/2.3/2.4）。
 * 坐标约定：board[x][y]，x 为行 0~9（0 黑方底线，9 红方底线），y 为列 0~8；
 * 小写为黑方棋子，大写为红方棋子。
 */
class XiangqiUtilsTest {

    private static final String STANDARD_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w";

    private static char[][] emptyBoard() {
        char[][] board = new char[10][9];
        for (char[] row : board) {
            Arrays.fill(row, ' ');
        }
        return board;
    }

    private static char[][] put(char[][] board, int x, int y, char piece) {
        board[x][y] = piece;
        return board;
    }

    @Nested
    @DisplayName("canGo 走法规则")
    class CanGoTest {

        private char[][] board;
        private static final int CX = 4, CY = 4; // 中心子位置

        @BeforeEach
        void setUp() {
            board = emptyBoard();
        }

        @Test
        @DisplayName("通用：起点为空不能走")
        void emptySource() {
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 4, 7));
        }

        @Test
        @DisplayName("通用：目标为己方子不能走")
        void ownTarget() {
            put(board, CX, CY, 'r');
            put(board, 4, 7, 'p');
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 4, 7));
        }

        @Test
        @DisplayName("车：直线移动与阻挡")
        void chariot() {
            put(board, CX, CY, 'r');
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 4, 7), "直走空路径");
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 7, 4), "竖走空路径");
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 5, 5), "斜走");
            put(board, 2, 4, 'P');
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 1, 4), "路径阻挡");
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 2, 4), "吃阻挡子");
        }

        @Test
        @DisplayName("马：日字与蹩马腿")
        void knight() {
            put(board, CX, CY, 'n');
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 2, 3));
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 2, 5));
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 6, 3));
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 6, 5));
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 3, 2));
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 3, 6));
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 5, 2));
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 5, 6));
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 2, 2), "非日字");
            put(board, 3, 4, 'P');
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 2, 3), "纵向蹩腿");
            put(board, 4, 3, 'P');
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 3, 2), "横向蹩腿");
        }

        @Test
        @DisplayName("象：田字、塞象眼、不过河")
        void elephant() {
            put(board, 4, 4, 'b');
            assertTrue(XiangqiUtils.canGo(board, 4, 4, 2, 2), "黑象田字");
            assertTrue(XiangqiUtils.canGo(board, 4, 4, 2, 6), "黑象黑半场内田字");
            put(board, 3, 5, 'P');
            assertFalse(XiangqiUtils.canGo(board, 4, 4, 2, 6), "塞象眼");
            assertFalse(XiangqiUtils.canGo(board, 4, 4, 6, 4), "非田字");
            assertFalse(XiangqiUtils.canGo(board, 4, 4, 6, 2), "黑象过河 x>4");
            put(board, 4, 4, 'B');
            assertFalse(XiangqiUtils.canGo(board, 4, 4, 2, 2), "红象过河 x<5");
            put(board, 5, 4, 'B');
            assertTrue(XiangqiUtils.canGo(board, 5, 4, 7, 2), "红象田字");
        }

        @Test
        @DisplayName("士：斜一步且不出九宫")
        void advisor() {
            put(board, 0, 3, 'a');
            assertTrue(XiangqiUtils.canGo(board, 0, 3, 1, 4), "黑士斜走");
            assertFalse(XiangqiUtils.canGo(board, 0, 3, 0, 4), "直走");
            assertFalse(XiangqiUtils.canGo(board, 0, 3, 1, 2), "出宫 y<3");
            put(board, 1, 4, 'a');
            assertTrue(XiangqiUtils.canGo(board, 1, 4, 2, 5), "宫内斜走");
            put(board, 9, 3, 'A');
            assertTrue(XiangqiUtils.canGo(board, 9, 3, 8, 4), "红士斜走");
            assertFalse(XiangqiUtils.canGo(board, 9, 3, 8, 2), "红士出宫 y<3");
            put(board, 7, 3, 'A');
            assertFalse(XiangqiUtils.canGo(board, 7, 3, 6, 2), "红士过河 x<7");
        }

        @Test
        @DisplayName("将帅：横竖一步且不出九宫")
        void king() {
            put(board, 0, 4, 'k');
            assertTrue(XiangqiUtils.canGo(board, 0, 4, 0, 3), "黑将横走");
            assertTrue(XiangqiUtils.canGo(board, 0, 4, 1, 4), "黑将竖走");
            assertFalse(XiangqiUtils.canGo(board, 0, 4, 2, 4), "一次两步");
            put(board, 1, 4, 'k');
            assertFalse(XiangqiUtils.canGo(board, 1, 4, 1, 6), "出宫 y>5");
            assertFalse(XiangqiUtils.canGo(board, 1, 4, 3, 4), "出宫 x>2");
            put(board, 9, 4, 'K');
            assertTrue(XiangqiUtils.canGo(board, 9, 4, 8, 4), "红帅竖走");
            put(board, 7, 4, 'K');
            assertFalse(XiangqiUtils.canGo(board, 7, 4, 6, 4), "红帅出宫 x<7");
            assertFalse(XiangqiUtils.canGo(board, 7, 4, 8, 5), "斜走");
        }

        @Test
        @DisplayName("炮：隔空平移、隔一子吃、隔两子不吃")
        void cannon() {
            put(board, CX, CY, 'c');
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 4, 6), "平移路径空");
            put(board, 4, 7, 'p');
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 4, 8), "无屏不能吃");
            put(board, 4, 6, 'P');
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 4, 6), "有屏不能平移");
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 4, 8), "双屏不能吃");
            put(board, 4, 8, ' ');
            put(board, 4, 8, 'R');
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 4, 8), "双屏吃也不行");
            put(board, 4, 7, ' ');
            assertTrue(XiangqiUtils.canGo(board, CX, CY, 4, 8), "单屏吃子");
        }

        @Test
        @DisplayName("卒：过河前后走法")
        void pawnBlack() {
            put(board, 3, 4, 'p');
            assertTrue(XiangqiUtils.canGo(board, 3, 4, 4, 4), "未过河只进");
            assertFalse(XiangqiUtils.canGo(board, 3, 4, 3, 3), "未过河不能横走");
            assertFalse(XiangqiUtils.canGo(board, 3, 4, 2, 4), "不能后退");
            put(board, 5, 4, 'p');
            assertTrue(XiangqiUtils.canGo(board, 5, 4, 6, 4), "过河后前进");
            assertTrue(XiangqiUtils.canGo(board, 5, 4, 5, 3), "过河后横走");
            assertFalse(XiangqiUtils.canGo(board, 5, 4, 4, 4), "过河后仍不能后退");
        }

        @Test
        @DisplayName("兵：过河前后走法")
        void pawnRed() {
            put(board, 6, 4, 'P');
            assertTrue(XiangqiUtils.canGo(board, 6, 4, 5, 4), "未过河只进");
            assertFalse(XiangqiUtils.canGo(board, 6, 4, 6, 5), "未过河不能横走");
            assertFalse(XiangqiUtils.canGo(board, 6, 4, 7, 4), "不能后退");
            put(board, 4, 4, 'P');
            assertTrue(XiangqiUtils.canGo(board, 4, 4, 3, 4), "过河后前进");
            assertTrue(XiangqiUtils.canGo(board, 4, 4, 4, 5), "过河后横走");
            assertFalse(XiangqiUtils.canGo(board, 4, 4, 5, 4), "过河后仍不能后退");
        }

        @Test
        @DisplayName("非棋子字符走 false")
        void invalidPiece() {
            put(board, CX, CY, 'x');
            assertFalse(XiangqiUtils.canGo(board, CX, CY, 4, 5));
        }
    }

    @Nested
    @DisplayName("isJiang 将军判定")
    class JiangTest {

        @Test
        @DisplayName("车将：无阻挡命中，有阻挡不成立")
        void chariotCheck() {
            char[][] board = emptyBoard();
            put(board, 9, 4, 'K');
            put(board, 0, 4, 'r');
            put(board, 0, 3, 'k');
            assertTrue(XiangqiUtils.isJiang(board, true), "黑车正面将红帅");
            put(board, 5, 4, 'A');
            assertFalse(XiangqiUtils.isJiang(board, true), "红仕阻挡");
            assertFalse(XiangqiUtils.isJiang(board, false), "黑将未被攻击");
        }

        @Test
        @DisplayName("炮将：恰一屏命中，两屏不成立")
        void cannonCheck() {
            char[][] board = emptyBoard();
            put(board, 9, 4, 'K');
            put(board, 3, 4, 'c');
            put(board, 6, 4, 'A');
            put(board, 0, 3, 'k');
            assertTrue(XiangqiUtils.isJiang(board, true), "单屏炮将");
            put(board, 7, 4, 'A');
            assertFalse(XiangqiUtils.isJiang(board, true), "双屏炮不将");
        }

        @Test
        @DisplayName("马将：蹩腿判定")
        void knightCheck() {
            char[][] board = emptyBoard();
            put(board, 9, 4, 'K');
            put(board, 7, 3, 'n');
            put(board, 0, 3, 'k');
            assertTrue(XiangqiUtils.isJiang(board, true), "马将");
            put(board, 8, 3, 'A');
            assertFalse(XiangqiUtils.isJiang(board, true), "马腿被塞");
        }

        @Test
        @DisplayName("兵将：方向与阵营判定")
        void pawnCheck() {
            char[][] board = emptyBoard();
            put(board, 9, 4, 'K');
            put(board, 8, 4, 'p');
            assertTrue(XiangqiUtils.isJiang(board, true), "黑卒贴脸将红帅");
            put(board, 8, 4, 'P');
            assertFalse(XiangqiUtils.isJiang(board, true), "己方兵不将");
            put(board, 8, 4, ' ');
            put(board, 0, 4, 'k');
            put(board, 1, 4, 'P');
            assertTrue(XiangqiUtils.isJiang(board, false), "红兵贴脸将黑将");
        }

        @Test
        @DisplayName("白脸将：两将同列且中间无子")
        void facingKings() {
            char[][] board = emptyBoard();
            put(board, 9, 4, 'K');
            put(board, 0, 4, 'k');
            assertTrue(XiangqiUtils.isJiang(board, true), "对脸红帅被将");
            assertTrue(XiangqiUtils.isJiang(board, false), "对脸黑将被将");
            put(board, 5, 4, 'C');
            assertFalse(XiangqiUtils.isJiang(board, true), "中间有子不对脸");
        }
    }

    @Nested
    @DisplayName("isSha 绝杀判定")
    class ShaTest {

        @Test
        @DisplayName("双车锁杀：被将且无解")
        void doubleChariotMate() {
            char[][] board = emptyBoard();
            put(board, 9, 4, 'K');
            // 黑马 (7,3) 将帅（马腿 (8,3) 空）；黑车 (9,0) 锁 9 行 (9,3)/(9,5)；黑车 (8,0) 锁 8 行 (8,3)/(8,4)/(8,5)
            put(board, 7, 3, 'n');
            put(board, 9, 0, 'r');
            put(board, 8, 0, 'r');
            put(board, 0, 3, 'k');
            assertTrue(XiangqiUtils.isJiang(board, true), "当前被马将");
            assertTrue(XiangqiUtils.isSha(board, true), "无仕相，逃位全被锁，绝杀");
        }

        @Test
        @DisplayName("马将但可垫腿解将，非绝杀")
        void knightCheckResolvable() {
            char[][] board = emptyBoard();
            put(board, 9, 4, 'K');
            put(board, 7, 3, 'n');
            put(board, 8, 0, 'R');
            put(board, 0, 3, 'k');
            assertTrue(XiangqiUtils.isJiang(board, true), "当前被马将");
            assertFalse(XiangqiUtils.isSha(board, true), "红车占马腿解将");
        }

        @Test
        @DisplayName("黑方被绝杀：车贴脸将+保护+双兵锁底线")
        void blackMate() {
            char[][] board = emptyBoard();
            put(board, 0, 4, 'k');
            put(board, 1, 4, 'R');  // 红车贴脸将，被 (2,4) 兵保护，黑将不可吃
            put(board, 2, 4, 'P');
            put(board, 0, 5, 'P');  // 兵占 (0,5) 且受 (0,0) 车保护
            put(board, 0, 0, 'R');  // 车锁 (0,3) 与 (0,5)
            put(board, 9, 4, 'K');
            assertTrue(XiangqiUtils.isJiang(board, false), "当前被车将");
            assertTrue(XiangqiUtils.isSha(board, false), "黑将无路可解，绝杀");
        }

        @Test
        @DisplayName("未被将时不是绝杀")
        void notInCheckNotMate() {
            char[][] board = emptyBoard();
            put(board, 9, 4, 'K');
            put(board, 7, 4, 'n');
            put(board, 0, 3, 'k');
            assertFalse(XiangqiUtils.isSha(board, true), "马攻击不到帅");
        }
    }

    @Nested
    @DisplayName("fenToBoard FEN 解析")
    class FenTest {

        @Test
        @DisplayName("标准 FEN：黑上红下，不翻转")
        void standardFen() {
            char[][] board = XiangqiUtils.fenToBoard(STANDARD_FEN);
            assertEquals('r', board[0][0]);
            assertEquals('k', board[0][4]);
            assertEquals('c', board[2][1]);
            assertEquals('p', board[3][0]);
            assertEquals(' ', board[4][4], "空行");
            assertEquals('P', board[6][0]);
            assertEquals('K', board[9][4]);
            assertEquals('R', board[9][8]);
        }

        @Test
        @DisplayName("翻转 FEN：红上黑下自动翻转为黑上红下")
        void reversedFen() {
            char[][] board = XiangqiUtils.fenToBoard("7K1/9/9/9/9/9/9/9/9/1k7 w");
            assertEquals('k', board[0][7], "黑将仍解析到黑方底线");
            assertEquals('K', board[9][1], "红帅仍解析到红方底线");
        }

        @Test
        @DisplayName("非对称行翻转正确")
        void reversedAsymmetricFen() {
            // 翻转后黑方底线为 reverse("rnbakab1r") = "r bakabnr"（列 1 空、列 7 还原为 n）
            char[][] board = XiangqiUtils.fenToBoard("RN1KABNR/9/1C5C1/P1P1P1P1P/9/9/p1p1p1p1p/1c5c1/9/rnbakab1r w");
            assertEquals('R', board[9][0]);
            assertEquals('n', board[0][7]);
            assertEquals('k', board[0][4]);
            assertEquals('b', board[0][2]);
        }

        @Test
        @DisplayName("非法 FEN 不抛异常")
        void invalidFenNoThrow() {
            char[][] board = new char[10][9];
            assertDoesNotThrow(() -> XiangqiUtils.fenToBoard(board, ""));
        }

        @Test
        @DisplayName("isReverse 方向判定")
        void isReverseCases() {
            assertFalse(XiangqiUtils.isReverse(STANDARD_FEN), "黑上红下不翻转");
            assertTrue(XiangqiUtils.isReverse("7K1/9/9/9/9/9/9/9/9/1k7 w"), "K 在 k 前需翻转");
            assertFalse(XiangqiUtils.isReverse("9/9/9/9/9/9/9/9/9/9 w"), "无将不翻转");
        }
    }

    @Nested
    @DisplayName("validateChessBoard 局面校验")
    class ValidateTest {

        @Test
        @DisplayName("标准局面合法")
        void standardBoard() {
            assertTrue(XiangqiUtils.validateChessBoard(XiangqiUtils.fenToBoard(STANDARD_FEN)));
        }

        @Test
        @DisplayName("将出宫不合法")
        void kingOutOfPalace() {
            char[][] board = XiangqiUtils.fenToBoard(STANDARD_FEN);
            put(board, 4, 4, 'k');
            put(board, 0, 4, ' ');
            assertFalse(XiangqiUtils.validateChessBoard(board));
        }

        @Test
        @DisplayName("棋子超量不合法")
        void tooManyChariots() {
            char[][] board = XiangqiUtils.fenToBoard(STANDARD_FEN);
            put(board, 4, 4, 'r');
            put(board, 5, 4, ' ');
            assertFalse(XiangqiUtils.validateChessBoard(board), "黑方 3 车");
        }

        @Test
        @DisplayName("缺少将不合法")
        void missingKing() {
            char[][] board = XiangqiUtils.fenToBoard(STANDARD_FEN);
            put(board, 9, 4, ' ');
            assertFalse(XiangqiUtils.validateChessBoard(board));
        }
    }
}
