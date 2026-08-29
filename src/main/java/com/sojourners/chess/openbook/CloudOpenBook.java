package com.sojourners.chess.openbook;

import com.sojourners.chess.config.Properties;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.util.HttpUtils;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class CloudOpenBook implements OpenBook {

    // IT-9.4: 切换 HTTPS，明文请求消除
    private final static String URL = "https://www.chessdb.cn/chessdb.php";

    private static final System.Logger log = System.getLogger(CloudOpenBook.class.getName());

    @Override
    public List<BookData> get(char[][] board, boolean redGo)  {
        return null;
    }

    @Override
    public List<BookData> get(String fenCode, boolean onlyFinalPhase) {
        List<BookData> list = new ArrayList<>();
        long start = System.currentTimeMillis();
        try {
            String content = "action=queryall&board=" + URLEncoder.encode(fenCode, "UTF-8");
            String result = HttpUtils.sendByGet(URL, content, Properties.getInstance().getCloudBookTimeout());
            log.log(System.Logger.Level.DEBUG, "云库响应: fen=" + fenCode + " body=" + result);

            // IT-9.5: 云库协议响应分类，静默失败消除
            if (result == null || result.isEmpty()) {
                log.log(System.Logger.Level.WARNING, "云库响应为空 fen=" + fenCode + " 耗时=" + (System.currentTimeMillis() - start) + "ms");
                return list;
            }
            if (result.startsWith("error")) {
                log.log(System.Logger.Level.WARNING, "云库返回错误 fen=" + fenCode + " body=" + result);
                return list;
            }
            if (result.startsWith("unknown")) {
                // 数据库不认识的局面，属正常情况（非错误），不记录库招
                log.log(System.Logger.Level.INFO, "云库不认识该局面 fen=" + fenCode);
                return list;
            }

            if (result.contains("move")) {

                String[] datas = result.split("\\|");
                for (String data : datas) {

                    BookData bd = new BookData();
                    bd.setSource("云库");
                    String[] items = data.split(",");
                    boolean finalPhase = false;
                    for (String item : items) {
                        String[] kvs = item.split(":");
                        if (kvs.length > 1) {
                            if ("move".equals(kvs[0])) {
                                bd.setMove(kvs[1]);
                            } else if ("score".equals(kvs[0])) {
                                bd.setScore(Integer.parseInt(kvs[1]));
                            } else if ("winrate".equals(kvs[0])) {
                                bd.setWinRate(Double.parseDouble(kvs[1]));
                            } else if ("note".equals(kvs[0])) {
                                bd.setNote(kvs[1]);
                                if (kvs[1].contains("W") || kvs[1].contains("D") || kvs[1].contains("L")) {
                                    finalPhase = true;
                                }
                            }
                        }
                    }
                    if (!(onlyFinalPhase || Properties.getInstance().getOnlyCloudFinalPhase()) || finalPhase) {
                        list.add(bd);
                    }
                }
            }

        } catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "查询云库响应失败 fen=" + fenCode + " 耗时=" + (System.currentTimeMillis() - start) + "ms", e);
            return list;
        }
        log.log(System.Logger.Level.INFO, "云库查询完成 fen=" + fenCode + " 耗时=" + (System.currentTimeMillis() - start) + "ms 结果=" + list.size() + "条");
        return list;
    }

    @Override
    public void close() {

    }

}