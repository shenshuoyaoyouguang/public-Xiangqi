package com.sojourners.chess;

import javafx.application.Application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URL;

public class Main {

    public static void main(String[] args) {
        redirectStdio();
        Application.launch(App.class);
    }

    /**
     * 将 stdout 重定向到 log/log.log，stderr 重定向到 log/error.log。
     * 仅在从 jar 运行时生效（IDE 中不重定向，保持控制台输出）。
     * 匹配 V1.9 exe4j 启动器的日志行为，确保 jpackage 打包模式下错误可见。
     */
    private static void redirectStdio() {
        try {
            URL url = Main.class.getProtectionDomain().getCodeSource().getLocation();
            if (url == null) return;
            File codeSource = new File(url.toURI());
            if (codeSource.isDirectory()) return;
            File logDir = new File(codeSource.getParentFile(), "log");
            logDir.mkdirs();
            System.setOut(new PrintStream(new FileOutputStream(new File(logDir, "log.log"), true), true));
            System.setErr(new PrintStream(new FileOutputStream(new File(logDir, "error.log"), true), true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
