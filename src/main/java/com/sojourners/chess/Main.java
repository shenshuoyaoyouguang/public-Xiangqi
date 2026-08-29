package com.sojourners.chess;

import com.sojourners.chess.util.Logging;
import javafx.application.Application;

public class Main {

    public static void main(String[] args) {
        Logging.init();
        Application.launch(App.class);
    }
}
