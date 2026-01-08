package com.yourapp.models;

import java.util.List;

public class PriorityItem {
    public int farmId;
    public String farmName;
    public int varietyId;
    public String varietyName;
    public String status;
    public List<LengthRow> lengths;

    public static class LengthRow {
        public int length;
        public int needed;
        public int received;
    }
}
