package com.yourapp.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PriorityRequestItem {
    public int id; // unique request id
    public String farmName;
    public String varietyName;
    public String status;
    public List<PriorityLength> lengths;

    // map to store confirmed quantity per manager
    public Map<String, Integer> confirmedByManager = new HashMap<>();
}
