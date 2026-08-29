package com.sojourners.chess.manual;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class TxqChessManualImpl implements ChessManualService {

    private static final System.Logger log = System.getLogger(TxqChessManualImpl.class.getName());

    @Override
    public ChessManual openChessManual(File file) {
        ObjectInputStream os = null;
        try {
            os = new ObjectInputStream(new FileInputStream(file));
            return (ChessManual) os.readObject();
        } catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "读取 TXQ 棋谱文件失败", e);
        } finally {
            try {
                if (os != null)
                    os.close();
            } catch (Exception e) {
                log.log(System.Logger.Level.WARNING, "关闭 TXQ 棋谱文件输入流失败", e);
            }
        }
        return null;
    }

    @Override
    public void saveChessManual(ChessManual cm, File file) {
        ObjectOutputStream os = null;
        try {
            os = new ObjectOutputStream(new FileOutputStream(file));
            os.writeObject(cm);
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "保存 TXQ 棋谱文件失败", e);
        } finally {
            try {
                if (os != null)
                    os.close();
            } catch (Exception e) {
                log.log(System.Logger.Level.ERROR, "关闭 TXQ 棋谱文件输出流失败", e);
            }
        }
    }
}
