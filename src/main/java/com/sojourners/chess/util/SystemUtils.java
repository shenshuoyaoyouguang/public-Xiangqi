package com.sojourners.chess.util;

import java.awt.*;
import java.net.URI;

public class SystemUtils {

    private static final System.Logger log = System.getLogger(SystemUtils.class.getName());

    public static void openBrowser(String url) {
        Thread.startVirtualThread(() -> {
            Desktop desktop = Desktop.getDesktop();
            if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    URI uri = new URI(url);
                    desktop.browse(uri);
                } catch (Exception e1) {
                    log.log(System.Logger.Level.WARNING, "调用系统浏览器打开网页失败", e1);
                }
            }
        });
    }
}
