package com.sojourners.chess.linker;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompareBoardTest {

    private static char[][] initialBoard() {
        char[][] b = new char[10][9];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                b[i][j] = ' ';
            }
        }
        return b;
    }

    @Test
    void diffBoards_identicalBoards_noDifferences() {
        char[][] a = initialBoard();
        char[][] b = initialBoard();
        AbstractGraphLinker.DiffResult result = AbstractGraphLinker.diffBoards(a, b);
        assertTrue(result.diffList.isEmpty());
        assertEquals(0, result.diff1);
        assertEquals(0, result.diff2);
        assertEquals(0, result.diff3);
    }

    @Test
    void diffBoards_singleMove_classifiedCorrectly() {
        char[][] link = initialBoard();
        char[][] engine = initialBoard();
        link[0][0] = 'r';
        link[1][0] = ' ';
        engine[0][0] = ' ';
        engine[1][0] = 'r';
        AbstractGraphLinker.DiffResult result = AbstractGraphLinker.diffBoards(link, engine);
        assertEquals(2, result.diffList.size());
        assertEquals(0, result.diff1);
        assertEquals(1, result.diff2);
        assertEquals(1, result.diff3);
    }

    @Test
    void diffBoards_capture_diff1Counted() {
        char[][] link = initialBoard();
        char[][] engine = initialBoard();
        link[0][0] = 'r';
        engine[0][0] = 'R';
        AbstractGraphLinker.DiffResult result = AbstractGraphLinker.diffBoards(link, engine);
        assertEquals(1, result.diff1);
        assertEquals(0, result.diff2);
        assertEquals(0, result.diff3);
    }

    @Test
    void checkMoveLegality_flag1UsesEngineBoard() {
        char[][] link = initialBoard();
        char[][] engine = initialBoard();
        engine[0][0] = 'r';
        engine[2][0] = ' ';
        Point from = new Point(0, 0);
        Point to = new Point(2, 0);
        assertTrue(AbstractGraphLinker.checkMoveLegality(1, from, to, link, engine));
    }

    @Test
    void checkMoveLegality_flag2UsesLinkBoard() {
        char[][] link = initialBoard();
        char[][] engine = initialBoard();
        link[0][0] = 'R';
        link[3][0] = ' ';
        Point from = new Point(0, 0);
        Point to = new Point(3, 0);
        assertTrue(AbstractGraphLinker.checkMoveLegality(2, from, to, link, engine));
    }

    @Test
    void classifyAction_emptyDiff_returnsEmpty() {
        char[][] link = initialBoard();
        char[][] engine = initialBoard();
        List<Point> empty = List.of();
        List<AbstractGraphLinker.Candidate> candidates =
                AbstractGraphLinker.classifyAction(empty, link, engine, false, false);
        assertTrue(candidates.isEmpty());
    }
}