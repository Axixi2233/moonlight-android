package com.limelight.ui.gamemenu.bean;

/**
 * Description
 * Date: 2024-10-20
 * Time: 20:53
 */
public class GameMenuQuickBean {
    private String name;
    private short[] datas;

    private String codes;

    private String desc;

    private String id;

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
}
