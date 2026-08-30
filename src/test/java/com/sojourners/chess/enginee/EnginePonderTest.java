package com.sojourners.chess.enginee;

import com.sojourners.chess.enginee.Engine.AnalysisModel;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.ThinkData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ponder 后台思考命令流与状态机测试（IT-11.1 #68 连线模式补全）。
 * Engine 构造器会拉起真实引擎进程，这里用 Unsafe.allocateInstance 跳过构造器，
 * 注入 StringWriter 捕获引擎命令、记录型回调捕获 bestmove 转发。
 */
class EnginePonderTest {

    private static final class RecordingCb implements EngineCallBack {
        int bestMoveCount;
        String first;
        String second;

        @Override
        public void bestMove(String first, String second) {
            this.first = first;
            this.second = second;
            this.bestMoveCount++;
        }

        @Override
        public void thinkDetail(ThinkData td) {
        }

        @Override
        public void showBookResults(List<BookData> list) {
        }
    }

    private static Engine newEngine(EngineCallBack cb, StringWriter out) throws Exception {
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        Engine e = (Engine) unsafe.allocateInstance(Engine.class);
        set(e, "cb", cb);
        set(e, "writer", new BufferedWriter(out));
        return e;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = Engine.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field f = Engine.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void bestMove(Engine e, String msg) throws Exception {
        Method m = Engine.class.getDeclaredMethod("bestMove", String.class);
        m.setAccessible(true);
        m.invoke(e, msg);
    }

    private static void startPonder(Engine e, StringWriter out, AnalysisModel model, long value) throws Exception {
        set(e, "analysisModel", model);
        set(e, "analysisValue", value);
        out.getBuffer().setLength(0);
        e.startPonder("fen", List.of("h2e2"), "h9g9");
        // 生产环境中 ponder 搜索的 info 流会在 thinkDetail 里复位 stopFlag（depth<5），
        // 测试没有引擎输出流，直接模拟复位以隔离 ponder 状态机
        set(e, "stopFlag", false);
    }

    @Test
    @DisplayName("startPonder 发送 position+预测应手 与常规 go 一致的时限参数")
    void startPonderCarriesLimitsAndPonderMove() throws Exception {
        RecordingCb cb = new RecordingCb();
        StringWriter out = new StringWriter();
        Engine e = newEngine(cb, out);

        set(e, "analysisModel", AnalysisModel.FIXED_TIME);
        set(e, "analysisValue", 5000L);
        e.startPonder("fen", List.of("h2e2"), "h9g9");

        String cmds = out.toString();
        assertTrue(cmds.contains("position fen fen moves h2e2 h9g9"));
        assertTrue(cmds.contains("go ponder movetime 5000"));
        assertTrue((Boolean) get(e, "pondering"));
    }

    @Test
    @DisplayName("go ponder 时限参数跟随当前分析模型（depth/nodes/infinite）")
    void ponderLimitMatchesAnalysisModel() throws Exception {
        RecordingCb cb = new RecordingCb();
        StringWriter out = new StringWriter();
        Engine e = newEngine(cb, out);

        startPonder(e, out, AnalysisModel.FIXED_STEPS, 12);
        assertTrue(out.toString().contains("go ponder depth 12"));

        startPonder(e, out, AnalysisModel.FIXED_NODES, 4096);
        assertTrue(out.toString().contains("go ponder nodes 4096"));

        startPonder(e, out, AnalysisModel.INFINITE, 0);
        String cmds = out.toString();
        assertTrue(cmds.contains("go ponder"));
        assertFalse(cmds.contains("depth"));
        assertFalse(cmds.contains("movetime"));
        assertFalse(cmds.contains("nodes"));
    }

    @Test
    @DisplayName("ponderhit 仅在 ponder 中发送且不重复")
    void ponderhitOnlySentWhilePondering() throws Exception {
        RecordingCb cb = new RecordingCb();
        StringWriter out = new StringWriter();
        Engine e = newEngine(cb, out);

        e.ponderhit();
        assertFalse(out.toString().contains("ponderhit"));

        startPonder(e, out, AnalysisModel.FIXED_TIME, 5000L);
        e.ponderhit();
        assertTrue(out.toString().contains("ponderhit"));

        out.getBuffer().setLength(0);
        e.ponderhit();
        assertFalse(out.toString().contains("ponderhit"));

        e.stop();
        out.getBuffer().setLength(0);
        e.ponderhit();
        assertFalse(out.toString().contains("ponderhit"));
    }

    @Test
    @DisplayName("未经 ponderhit 的 ponder bestmove 属预测局面应手，丢弃")
    void spontaneousPonderBestMoveDiscarded() throws Exception {
        RecordingCb cb = new RecordingCb();
        StringWriter out = new StringWriter();
        Engine e = newEngine(cb, out);

        startPonder(e, out, AnalysisModel.FIXED_TIME, 5000L);
        bestMove(e, "bestmove h1e2 pondermove h9g9");

        assertEquals(0, cb.bestMoveCount);
        assertFalse((Boolean) get(e, "pondering"));
    }

    @Test
    @DisplayName("ponderhit 后的 bestmove 为本方回合着法，正常转发")
    void ponderhitThenBestMoveForwarded() throws Exception {
        RecordingCb cb = new RecordingCb();
        StringWriter out = new StringWriter();
        Engine e = newEngine(cb, out);

        startPonder(e, out, AnalysisModel.FIXED_TIME, 5000L);
        e.ponderhit();
        bestMove(e, "bestmove h1e2 pondermove h9g9");

        assertEquals(1, cb.bestMoveCount);
        assertEquals("h1e2", cb.first);
        assertEquals("h9g9", cb.second);
        assertFalse((Boolean) get(e, "pondering"));
    }

    @Test
    @DisplayName("stop 后引擎应答的 bestmove 走停止分支，不转发")
    void bestMoveAfterStopSwallowed() throws Exception {
        RecordingCb cb = new RecordingCb();
        StringWriter out = new StringWriter();
        Engine e = newEngine(cb, out);

        startPonder(e, out, AnalysisModel.FIXED_TIME, 5000L);
        e.stop();
        bestMove(e, "bestmove h1e2 pondermove h9g9");

        assertEquals(0, cb.bestMoveCount);
    }
}
