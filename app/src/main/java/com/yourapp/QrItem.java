package com.yourapp;

public class QrItem {
    private String code;
    private boolean valid;
    private int stems;
    private String serial;
    private String bucket;
    private String farm;
    private String length;
    private String block;
    private String variety;
    private String coldroom;

    public QrItem(String code, boolean valid, String serial, String bucket, String farm,
                  int stems, String length, String block, String variety, String coldroom) {
        this.code = code;
        this.valid = valid;
        this.serial = serial;
        this.bucket = bucket;
        this.farm = farm;
        this.stems = stems;
        this.length = length;
        this.block = block;
        this.variety = variety;
        this.coldroom = coldroom;
    }

    public String getCode() { return code; }
    public boolean isValid() { return valid; }
    public int getStems() { return stems; }
    public String getSerial() { return serial; }
    public String getBucket() { return bucket; }
    public String getFarm() { return farm; }
    public String getLength() { return length; }
    public String getBlock() { return block; }
    public String getVariety() { return variety; }
    public String getColdroom() { return coldroom; }

    public void setStems(int stems) { this.stems = stems; }
    public void setVariety(String variety) { this.variety = variety; }
    public void setColdroom(String coldroom) { this.coldroom = coldroom; }
}
