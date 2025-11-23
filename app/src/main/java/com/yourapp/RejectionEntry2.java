package com.yourapp;

public class RejectionEntry2 {
    public String farmName;
    public int farmId;
    public String varietyName;
    public int varietyId;
    public int stems;
    public String cellNo;
    public String tableNo;
    public String rejectionReasonName;
    public int rejectionReasonId;

    public RejectionEntry2(String farmName, int farmId,
                           String varietyName, int varietyId,
                           int stems,
                           String rejectionReasonName, int rejectionReasonId) {
        this.farmName = farmName;
        this.farmId = farmId;
        this.varietyName = varietyName;
        this.varietyId = varietyId;
        this.stems = stems;
        this.cellNo = cellNo;
        this.tableNo = tableNo;
        this.rejectionReasonName = rejectionReasonName;
        this.rejectionReasonId = rejectionReasonId;
    }
}
