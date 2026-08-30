package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.DialogUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;


public class LinkSettingController {

    @FXML
    private TextField linkScanTime;
    @FXML
    private TextField linkThreadNum;

    @FXML
    private TextField mouseClickDelay;

    @FXML
    private TextField mouseMoveDelay;
    @FXML
    private TextField linkMoveTime;
    @FXML
    private CheckBox linkManualMoveBox;
    @FXML
    private CheckBox ponderBox;

    private Properties prop;

    @FXML
    void cancelButtonClick(ActionEvent e) {
        App.closeLinkSetting();
    }

    @FXML
    void okButtonClick(ActionEvent e) {

        String txt = linkScanTime.getText();
        if (!isPositiveInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入扫描时间错误");
            return;
        }
        prop.setLinkScanTime(Long.parseLong(txt));
        txt = linkThreadNum.getText();
        if (!isPositiveInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入扫描扫描线程数量错误");
            return;
        }
        prop.setLinkThreadNum(Integer.parseInt(txt));

        txt = mouseClickDelay.getText();
        if (!isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入鼠标点击延迟错误");
            return;
        }
        prop.setMouseClickDelay(Integer.parseInt(txt));
        txt = mouseMoveDelay.getText();
        if (!isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入鼠标走子延迟错误");
            return;
        }
        prop.setMouseMoveDelay(Integer.parseInt(txt));
        txt = linkMoveTime.getText();
        if (!isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入引擎步时错误");
            return;
        }
        prop.setLinkMoveTime(txt.isEmpty() ? null : Integer.parseInt(txt));
        prop.setLinkManualMove(linkManualMoveBox.isSelected());
        prop.setPonderEnable(ponderBox.isSelected());

        App.closeLinkSetting();
    }


    private static boolean isPositiveInt(String s) {
        try { return Integer.parseInt(s) > 0; } catch (NumberFormatException e) { return false; }
    }

    private static boolean isNonNegativeInt(String s) {
        try { return Integer.parseInt(s) >= 0; } catch (NumberFormatException e) { return false; }
    }

    public void initialize() {

        prop = Properties.getInstance();

        linkScanTime.setText(String.valueOf(prop.getLinkScanTime()));
        linkThreadNum.setText(String.valueOf(prop.getLinkThreadNum()));

        mouseClickDelay.setText(String.valueOf(prop.getMouseClickDelay()));
        mouseMoveDelay.setText(String.valueOf(prop.getMouseMoveDelay()));
        linkMoveTime.setText(prop.getLinkMoveTime() == null ? "0" : String.valueOf(prop.getLinkMoveTime()));
        linkManualMoveBox.setSelected(Boolean.TRUE.equals(prop.getLinkManualMove()));
        ponderBox.setSelected(Boolean.TRUE.equals(prop.getPonderEnable()));

    }

}
