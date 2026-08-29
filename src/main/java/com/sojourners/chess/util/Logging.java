package com.sojourners.chess.util;

import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * 日志基础设施（IT-3.4）：基于 System.Logger（默认后端为 JUL），应用启动时为 JUL 挂接滚动文件输出。
 * 日志文件位于 jar 同目录 logs/ 下，按大小滚动（5MB × 3 个循环覆盖），追加写入。
 * 代码中通过 {@code System.getLogger(类名.class.getName())} 取 logger 使用。
 */
public final class Logging {

    private static volatile boolean initialized;

    private Logging() {
    }

    /**
     * 初始化滚动文件输出；重复调用安全。初始化失败时降级为仅控制台输出，不阻断应用启动。
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // SimpleFormatter 单行格式：时间 级别 logger名 消息 异常栈
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$s %3$s %5$s%6$s%n");
        try {
            FileHandler fh = new FileHandler(PathUtils.getJarPath() + "logs/tchess%g.log",
                    5_000_000, 3, true);
            fh.setFormatter(new java.util.logging.SimpleFormatter());
            Logger root = Logger.getLogger("");
            root.addHandler(fh);
        } catch (Throwable t) {
            // 日志子系统自身故障的最后兜底：仅控制台，不阻断启动
            System.err.println("日志文件初始化失败，仅控制台输出: " + t);
        }
    }
}
