package com.limelight.ui.gamemenu.bean;

/**
 * Description
 * Date: 2024-10-20
 * Time: 20:53
 */
public class GameMenuQuickBean {
    private String name;
    private short[] datas;

    private int code;

    private String codes;

    private String desc;

    private String id;
    //类型 1鼠标 2触控板 3摇杆 4普通按钮 5十字键
    private int btnType;
    //宽
    private int width;
    //高
    private int height;
    //左边距
    private int mLeft;
    //上边距
    private int mTop;
    //缩放比例
    private int zoom=100;

    //缩放比例 宽度&高度 仅普通按钮
    private int zoomW=100;
    private int zoomH=100;

    //0-圆形 1方形
    private int shapeType;

    //开关模式
    private boolean switchMode;

    //自由摇杆
    private boolean isFreeStick;

    //固定行程自由摇杆
    private boolean fixedStrokeFreeStick;

    //是否是手柄按键
    private boolean isGamePad;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public GameMenuQuickBean() {
    }

    public GameMenuQuickBean(String name, short[] datas) {
        this.name = name;
        this.datas = datas;
    }

    public GameMenuQuickBean(String name, int code, String desc, int btnType, boolean switchMode) {
        this.name = name;
        this.code = code;
        this.desc = desc;
        this.btnType = btnType;
        this.switchMode = switchMode;
    }

    public GameMenuQuickBean(String name, String codes, String desc, int btnType, boolean switchMode) {
        this.name = name;
        this.codes = codes;
        this.desc = desc;
        this.btnType = btnType;
        this.switchMode = switchMode;
    }

    public void setBtnType(int btnType) {
        this.btnType = btnType;
    }

    public int getBtnType() {
        return btnType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public short[] getDatas() {
        return datas;
    }

    public void setDatas(short[] datas) {
        this.datas = datas;
    }

    public String getCodes() {
        return codes;
    }

    public void setCodes(String codes) {
        this.codes = codes;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getmLeft() {
        return mLeft;
    }

    public void setmLeft(int mLeft) {
        this.mLeft = mLeft;
    }

    public int getmTop() {
        return mTop;
    }

    public void setmTop(int mTop) {
        this.mTop = mTop;
    }

    public int getZoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        this.zoom = zoom;
    }

    public int getShapeType() {
        return shapeType;
    }

    public GameMenuQuickBean setShapeType(int shapeType) {
        this.shapeType = shapeType;
        return this;
    }

    public boolean isSwitchMode() {
        return switchMode;
    }

    public void setSwitchMode(boolean switchMode) {
        this.switchMode = switchMode;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public int getZoomW() {
        return zoomW;
    }

    public void setZoomW(int zoomW) {
        this.zoomW = zoomW;
    }

    public int getZoomH() {
        return zoomH;
    }

    public void setZoomH(int zoomH) {
        this.zoomH = zoomH;
    }

    public boolean isFreeStick() {
        return isFreeStick;
    }

    public GameMenuQuickBean setFreeStick(boolean freeStick) {
        isFreeStick = freeStick;
        return this;
    }

    public boolean isGamePad() {
        return isGamePad;
    }

    public GameMenuQuickBean setGamePad(boolean gamePad) {
        isGamePad = gamePad;
        return this;
    }

    public boolean isFixedStrokeFreeStick() {
        return fixedStrokeFreeStick;
    }

    public GameMenuQuickBean setFixedStrokeFreeStick(boolean fixedStrokeFreeStick) {
        this.fixedStrokeFreeStick = fixedStrokeFreeStick;
        return this;
    }
}
