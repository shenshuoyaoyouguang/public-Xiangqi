package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.util.DialogUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;


public class TimeSettingController {

    @FXML
    private RadioButton fixTimeButton;

    @FXML
    private TextField timeText;

    @FXML
    private RadioButton fixDepthButton;

    @FXML
    private TextField depthText;

    @FXML
    private RadioButton fixNodeButton;

    @FXML
    private TextField nodeText;

    @FXML
    private TextField engineDelayStart;

    @FXML
    private TextField engineDelayEnd;

    @FXML
    private TextField bookDelayStart;

    @FXML
    private TextField bookDelayEnd;


    private Properties prop;

    @FXML
    void cancelButtonClick(ActionEvent e) {
        App.closeTimeSetting();
    }

    @FXML
    void okButtonClick(ActionEvent e) {
        if (fixDepthButton.isSelected()) {
            String txt = depthText.getText();
            if (!isPositiveInt(txt)) {
                DialogUtils.showErrorDialog("失败", "层数错误");
                return;
            }
            prop.setAnalysisModel(Engine.AnalysisModel.FIXED_STEPS);
            prop.setAnalysisValue(Long.parseLong(txt));
        } else if (fixNodeButton.isSelected()) {
            String txt = nodeText.getText();
            if (!isPositiveInt(txt)) {
                DialogUtils.showErrorDialog("失败", "节点数错误");
                return;
            }
            prop.setAnalysisModel(Engine.AnalysisModel.FIXED_NODES);
            prop.setAnalysisValue(Long.parseLong(txt));
        } else {
            String txt = timeText.getText();
            if (!isPositiveInt(txt)) {
                DialogUtils.showErrorDialog("失败", "时间错误");
                return;
            }
            prop.setAnalysisModel(Engine.AnalysisModel.FIXED_TIME);
            prop.setAnalysisValue(Long.parseLong(txt));
        }

        String txt = engineDelayStart.getText();
        if (!isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入引擎出招延迟错误");
            return;
        }
        prop.setEngineDelayStart(Integer.parseInt(txt));
        txt = engineDelayEnd.getText();
        if (!isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入引擎出招延迟错误");
            return;
        }
        prop.setEngineDelayEnd(Integer.parseInt(txt));

        txt = bookDelayStart.getText();
        if (!isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入库招出招延迟错误");
            return;
        }
        prop.setBookDelayStart(Integer.parseInt(txt));
        txt = bookDelayEnd.getText();
        if (!isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入库招出招延迟错误");
            return;
        }
        prop.setBookDelayEnd(Integer.parseInt(txt));

        App.closeTimeSetting();
    }



    private static boolean isPositiveInt(String s) {
        try { return Integer.parseInt(s) > 0; } catch (NumberFormatException e) { return false; }
    }

    private static boolean isNonNegativeInt(String s) {
        try { return Integer.parseInt(s) >= 0; } catch (NumberFormatException e) { return false; }
    }

    public void initialize() {

        ToggleGroup group = new ToggleGroup();
        fixTimeButton.setToggleGroup(group);
        fixDepthButton.setToggleGroup(group);
        fixNodeButton.setToggleGroup(group);

        prop = Properties.getInstance();
        if (prop.getAnalysisModel() == Engine.AnalysisModel.FIXED_TIME) {
            fixTimeButton.setSelected(true);
            timeText.setText(String.valueOf(prop.getAnalysisValue()));
        } else if (prop.getAnalysisModel() == Engine.AnalysisModel.FIXED_NODES) {
            fixNodeButton.setSelected(true);
            nodeText.setText(String.valueOf(prop.getAnalysisValue()));
        } else {
            fixDepthButton.setSelected(true);
            depthText.setText(String.valueOf(prop.getAnalysisValue()));
        }

        engineDelayStart.setText(String.valueOf(prop.getEngineDelayStart()));
        engineDelayEnd.setText(String.valueOf(prop.getEngineDelayEnd()));

        bookDelayStart.setText(String.valueOf(prop.getBookDelayStart()));
        bookDelayEnd.setText(String.valueOf(prop.getBookDelayEnd()));

    }

}
