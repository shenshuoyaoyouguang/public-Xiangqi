package com.sojourners.chess.config;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.openbook.MoveRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JsonPropertiesCodec 序列化回环测试（IT-3.2）：
 * defaults() 设为非默认值 → toJson → fromJson → 逐字段比对，保证无字段丢失。
 */
class JsonPropertiesCodecTest {

    private static Properties buildNonDefault() {
        Properties p = Properties.defaults();
        p.setBoardSize(ChessBoard.BoardSize.LARGE_BOARD);
        p.setBoardStyle(ChessBoard.BoardStyle.CUSTOM);
        p.setStepTip(false);
        p.setStepSound(false);
        p.setShowNumber(true);
        p.setTopWindow(true);
        p.setManualTip(false);
        p.setShowChessNotation(false);
        p.setColorTheme(Properties.ColorTheme.DARK);
        p.setThreadNum(4);
        p.setHashSize(128);
        p.setAnalysisModel(Engine.AnalysisModel.FIXED_NODES);
        p.setAnalysisValue(8192L);
        p.setEngineName("皮卡鱼\"测试\"\\v1\n");
        p.setStageWidth(1024.5);
        p.setStageHeight(768.25);
        p.setSplitPos(0.75);
        p.setSplitPos2(0.35);
        p.setLinkScanTime(500L);
        p.setLinkThreadNum(3);
        p.setLinkAnimation(false);
        p.setLinkShowInfo(false);
        p.setLinkBackMode(true);
        p.setPonderEnable(true);
        p.setEngineDelayStart(10);
        p.setEngineDelayEnd(20);
        p.setBookDelayStart(30);
        p.setBookDelayEnd(40);
        p.setMouseClickDelay(50);
        p.setMouseMoveDelay(60);
        p.setFirstStepColor("#ff0000");
        p.setFirstStepNumberColor("#00ff00");
        p.setSecondStepColor("#0000ff");
        p.setSecondStepNumberColor("#ffff00");
        p.setBranchStepColor("#00ffff");
        p.setBranchStepNumberColor("#ff00ff");
        p.setFirstStepOpacity(0.5);
        p.setSecondStepOpacity(0.6);
        p.setBranchStepOpacity(0.7);
        p.setLocalBookFirst(true);
        p.setUseCloudBook(false);
        p.setOnlyCloudFinalPhase(true);
        p.setCloudBookTimeout(3000);
        p.setOffManualSteps(10);
        p.setBookSwitch(false);
        p.setChessManualPath("D:\\棋谱\\记录.pgn");
        p.setMoveRule(MoveRule.POSITIVE_RANDOM);
        List<String> books = new ArrayList<>();
        books.add("a.xqb");
        books.add("b.bin");
        p.setOpenBookList(books);
        return p;
    }

    @Test
    @DisplayName("全字段回环一致")
    void roundTripAllFields() {
        Properties p = buildNonDefault();
        Properties q = JsonPropertiesCodec.fromJson(JsonPropertiesCodec.toJson(p));

        assertEquals(p.getBoardSize(), q.getBoardSize());
        assertEquals(p.getBoardStyle(), q.getBoardStyle());
        assertEquals(p.isStepTip(), q.isStepTip());
        assertEquals(p.isStepSound(), q.isStepSound());
        assertEquals(p.isShowNumber(), q.isShowNumber());
        assertEquals(p.isTopWindow(), q.isTopWindow());
        assertEquals(p.isManualTip(), q.isManualTip());
        assertEquals(p.isShowChessNotation(), q.isShowChessNotation());
        assertEquals(p.getColorTheme(), q.getColorTheme());
        assertEquals(p.getThreadNum(), q.getThreadNum());
        assertEquals(p.getHashSize(), q.getHashSize());
        assertEquals(p.getAnalysisModel(), q.getAnalysisModel());
        assertEquals(p.getAnalysisValue(), q.getAnalysisValue());
        assertEquals(p.getEngineName(), q.getEngineName());
        assertEquals(p.getStageWidth(), q.getStageWidth());
        assertEquals(p.getStageHeight(), q.getStageHeight());
        assertEquals(p.getSplitPos(), q.getSplitPos());
        assertEquals(p.getSplitPos2(), q.getSplitPos2());
        assertEquals(p.getLinkScanTime(), q.getLinkScanTime());
        assertEquals(p.getLinkThreadNum(), q.getLinkThreadNum());
        assertEquals(p.isLinkAnimation(), q.isLinkAnimation());
        assertEquals(p.isLinkShowInfo(), q.isLinkShowInfo());
        assertEquals(p.isLinkBackMode(), q.isLinkBackMode());
        assertEquals(p.getEngineDelayStart(), q.getEngineDelayStart());
        assertEquals(p.getEngineDelayEnd(), q.getEngineDelayEnd());
        assertEquals(p.getBookDelayStart(), q.getBookDelayStart());
        assertEquals(p.getBookDelayEnd(), q.getBookDelayEnd());
        assertEquals(p.getMouseClickDelay(), q.getMouseClickDelay());
        assertEquals(p.getMouseMoveDelay(), q.getMouseMoveDelay());
        assertEquals(p.getFirstStepColor(), q.getFirstStepColor());
        assertEquals(p.getFirstStepNumberColor(), q.getFirstStepNumberColor());
        assertEquals(p.getSecondStepColor(), q.getSecondStepColor());
        assertEquals(p.getSecondStepNumberColor(), q.getSecondStepNumberColor());
        assertEquals(p.getBranchStepColor(), q.getBranchStepColor());
        assertEquals(p.getBranchStepNumberColor(), q.getBranchStepNumberColor());
        assertEquals(p.getFirstStepOpacity(), q.getFirstStepOpacity());
        assertEquals(p.getSecondStepOpacity(), q.getSecondStepOpacity());
        assertEquals(p.getBranchStepOpacity(), q.getBranchStepOpacity());
        assertEquals(p.getLocalBookFirst(), q.getLocalBookFirst());
        assertEquals(p.getUseCloudBook(), q.getUseCloudBook());
        assertEquals(p.getOnlyCloudFinalPhase(), q.getOnlyCloudFinalPhase());
        assertEquals(p.getCloudBookTimeout(), q.getCloudBookTimeout());
        assertEquals(p.getOffManualSteps(), q.getOffManualSteps());
        assertEquals(p.getBookSwitch(), q.getBookSwitch());
        assertEquals(p.getPonderEnable(), q.getPonderEnable());
        assertEquals(p.getChessManualPath(), q.getChessManualPath());
        assertEquals(p.getMoveRule(), q.getMoveRule());
        assertEquals(p.getOpenBookList(), q.getOpenBookList());
    }

    @Test
    @DisplayName("字符串转义回环：引号/反斜杠/换行/制表符")
    void stringEscapeRoundTrip() {
        Properties p = Properties.defaults();
        String tricky = "引号\" 反斜杠\\ 换行\n 回车\r 制表\t 中文🧩";
        p.setEngineName(tricky);
        p.setChessManualPath(tricky);
        Properties q = JsonPropertiesCodec.fromJson(JsonPropertiesCodec.toJson(p));
        assertEquals(tricky, q.getEngineName());
        assertEquals(tricky, q.getChessManualPath());
    }

    @Test
    @DisplayName("nullable 字段 null 回环保持 null")
    void nullableFieldsRoundTrip() {
        Properties p = Properties.defaults();
        p.setLocalBookFirst(null);
        p.setUseCloudBook(null);
        p.setOnlyCloudFinalPhase(null);
        p.setBookSwitch(null);
        p.setCloudBookTimeout(null);
        p.setOffManualSteps(null);
        p.setChessManualPath(null);
        p.setEngineName(null);
        Properties q = JsonPropertiesCodec.fromJson(JsonPropertiesCodec.toJson(p));
        assertNull(q.getLocalBookFirst());
        assertNull(q.getUseCloudBook());
        assertNull(q.getOnlyCloudFinalPhase());
        assertNull(q.getBookSwitch());
        assertNull(q.getCloudBookTimeout());
        assertNull(q.getOffManualSteps());
        assertNull(q.getChessManualPath());
        assertNull(q.getEngineName());
    }

    @Test
    @DisplayName("engines 列表与 options 回环")
    void enginesRoundTrip() {
        Properties p = Properties.defaults();
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        options.put("Hash", "128");
        options.put("UCI_AddonStyle", "OFF");
        List<EngineConfig> engines = new ArrayList<>();
        engines.add(new EngineConfig("engine1", "C:\\path\\engine.exe", "uci", options));
        engines.add(new EngineConfig(null, "engine2", "ucci", null));
        p.setEngineConfigList(engines);

        Properties q = JsonPropertiesCodec.fromJson(JsonPropertiesCodec.toJson(p));
        List<EngineConfig> out = q.getEngineConfigList();
        assertEquals(2, out.size());
        assertEquals("engine1", out.get(0).getName());
        assertEquals("C:\\path\\engine.exe", out.get(0).getPath());
        assertEquals("uci", out.get(0).getProtocol());
        assertEquals("128", out.get(0).getOptions().get("Hash"));
        assertEquals("OFF", out.get(0).getOptions().get("UCI_AddonStyle"));
        assertNull(out.get(1).getName());
        assertEquals("ucci", out.get(1).getProtocol());
        assertTrue(out.get(1).getOptions().isEmpty());
    }

    @Test
    @DisplayName("未知字段忽略；类型错误字段保持默认；畸形 JSON 快速失败由调用方兜底")
    void malformedJsonBehavior() {
        Properties d = Properties.defaults();
        Properties q1 = JsonPropertiesCodec.fromJson("{\"unknownField\": 123}");
        assertEquals(d.getThreadNum(), q1.getThreadNum());
        Properties q2 = JsonPropertiesCodec.fromJson("{\"threadNum\": \"bad\"}");
        assertEquals(d.getThreadNum(), q2.getThreadNum(), "类型错误字段保持默认");
        assertThrows(RuntimeException.class, () -> JsonPropertiesCodec.fromJson("not a json"));
    }
}
