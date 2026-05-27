package com.yourapp.summers;

public class BucketItem {

    public String variety;
    public int maxQty;
    public int enteredQty = 0;

    public BucketItem(String variety, int maxQty) {
        this.variety = variety;
        this.maxQty = maxQty;
    }
}