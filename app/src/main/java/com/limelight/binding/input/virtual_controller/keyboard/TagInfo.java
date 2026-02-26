package com.limelight.binding.input.virtual_controller.keyboard;
import java.util.Objects;

public class TagInfo {
    public int index;        // 索引或 ID
    public boolean isGamepad; // true 为手柄，false 为键盘

    public TagInfo(int index, boolean isGamepad) {
        this.index = index;
        this.isGamepad = isGamepad;
    }

    // 重写 equals，确保 findViewWithTag 比较的是值而不是内存地址
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagInfo that = (TagInfo) o;
        return index == that.index && isGamepad == that.isGamepad;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, isGamepad);
    }
}
