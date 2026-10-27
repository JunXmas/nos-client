package dev.jun.nosclient.gui;

import java.util.List;

/**
 * Bảng chỉnh HUD không được đè gadget: gadget nằm đáy thì bảng lên đỉnh; đáy và đỉnh đều có gadget thì giữ
 * đáy (gadget được ưu tiên khi bấm nên vẫn kéo ra được).
 * Chạy: javac -d /tmp/hdc -cp <như WorldKeyCheck> checks/HudDockCheck.java && java -ea -cp /tmp/hdc:<như trên> dev.jun.nosclient.gui.HudDockCheck
 */
public class HudDockCheck {
    public static void main(String[] args) {
        int h = 540;
        int x0 = 200;
        int x1 = 600;
        int bottom = h - HudEditScreen.TOOLBAR_H - HudEditScreen.TOOLBAR_MARGIN;
        int top = HudEditScreen.TOOLBAR_MARGIN;
        assert HudEditScreen.dock(h, x0, x1, List.of()) == bottom : "mặc định ở đáy";
        assert HudEditScreen.dock(h, x0, x1, List.of(new float[]{10, 10, 120, 60})) == bottom : "gadget góc trên trái không ảnh hưởng";
        float[] underBar = {300, 480, 420, 520};
        assert HudEditScreen.dock(h, x0, x1, List.of(underBar)) == top : "gadget lọt vào vùng bảng đáy thì bảng phải lên đỉnh";
        float[] beside = {620, 480, 700, 520};
        assert HudEditScreen.dock(h, x0, x1, List.of(beside)) == bottom : "gadget ngoài bề ngang bảng không đẩy bảng";
        assert HudEditScreen.dock(h, x0, x1, List.of(underBar, new float[]{300, 20, 420, 60})) == bottom : "hai đầu đều có gadget thì giữ đáy";
        System.out.println("HudDock OK");
    }
}
