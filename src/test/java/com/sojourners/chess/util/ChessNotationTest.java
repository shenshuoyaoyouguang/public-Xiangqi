package com.sojourners.chess.util;

import com.sojourners.chess.board.ChessBoard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * XiangqiUtils 中文棋谱翻译测试（支撑 IT-3.3 覆盖率门禁；翻译域深测在口碑修复迭代补齐）。
 * 红方用汉字数字、黑方用全角数字；坐标为引擎格式（列字母 + 行数字 0~9，0 为黑方底线）。
 */
class ChessNotationTest {

    private static final String STANDARD_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w";

    private final ChessBoard board = new ChessBoard(null, ChessBoard.BoardSize.MIDDLE_BOARD,
            ChessBoard.BoardStyle.DEFAULT, false, false, false, false, false, STANDARD_FEN);

    private char[][] board() {
        return board.getBoard();
    }

    @Test
    @DisplayName("中文转坐标：炮二平五")
    void cnToStepCannon() {
        StringBuilder sb = new StringBuilder();
        XiangqiUtils.translateCnMove(board(), sb, "炮二平五");
        assertEquals("h2e2", sb.toString());
    }

    @Test
    @DisplayName("中文转坐标：马二进三")
    void cnToStepKnight() {
        StringBuilder sb = new StringBuilder();
        XiangqiUtils.translateCnMove(board(), sb, "马二进三");
        assertEquals("h0g2", sb.toString());
    }

    @Test
    @DisplayName("中文转坐标：车九进六")
    void cnToStepChariot() {
        StringBuilder sb = new StringBuilder();
        XiangqiUtils.translateCnMove(board(), sb, "车九进六");
        assertEquals("a0a6", sb.toString(), "红车 a0(引擎) 进六到 a6(引擎)");
    }

    @Test
    @DisplayName("中文转坐标：黑方炮８平５（全角数字）")
    void cnToStepBlack() {
        StringBuilder sb = new StringBuilder();
        XiangqiUtils.translateCnMove(board(), sb, "炮８平５");
        assertEquals("h7e7", sb.toString());
    }

    @Test
    @DisplayName("坐标转中文：炮二平五")
    void stepToCnCannon() {
        StringBuilder sb = new StringBuilder();
        XiangqiUtils.translate(board(), sb, "h2e2", false);
        assertEquals("炮二平五", sb.toString());
    }

    @Test
    @DisplayName("坐标转中文：马二进三")
    void stepToCnKnight() {
        StringBuilder sb = new StringBuilder();
        XiangqiUtils.translate(board(), sb, "h0g2", false);
        assertEquals("马二进三", sb.toString());
    }

    @Test
    @DisplayName("坐标转中文：黑方炮８平５")
    void stepToCnBlack() {
        StringBuilder sb = new StringBuilder();
        XiangqiUtils.translate(board(), sb, "h7e7", false);
        assertEquals("炮８平５", sb.toString());
    }
}
