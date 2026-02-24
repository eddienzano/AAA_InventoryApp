package com.yourapp.gradedstock;

import java.io.Serializable;
import java.util.HashMap;

public class VarietyModel implements Serializable {

    private int id;
    private String name;
    private String imageUrl;

    // ===== Submission state =====
    private boolean completed = false;
    private String completedDate;   // yyyy-MM-dd
    private Integer shiftId;         // nullable

    // ===== Lengths =====
    private HashMap<Integer, Integer> lengths = new HashMap<>();

    public VarietyModel(int id, String name, String imageUrl) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
    }

    // =========================
    // Getters
    // =========================

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getCompletedDate() {
        return completedDate;
    }

    public Integer getShiftId() {
        return shiftId;
    }

    public HashMap<Integer, Integer> getLengths() {
        return lengths;
    }

    // =========================
    // State helpers
    // =========================

    /**
     * Mark this variety as completed for a given date & shift
     */
    public void markCompleted(String date, Integer shiftId) {
        this.completed = true;
        this.completedDate = date;
        this.shiftId = shiftId;
    }

    /**
     * Used ONLY if you want to reset UI state (e.g. new day)
     */
    public void clearLengths() {
        lengths.clear();
        completed = false;
        completedDate = null;
        shiftId = null;
    }

    // =========================
    // Setters (kept for compatibility)
    // =========================

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setLengths(HashMap<Integer, Integer> lengths) {
        this.lengths = lengths;
    }

    // =========================
    // UI helper
    // =========================

    public String getSummary() {
        if (lengths == null || lengths.isEmpty()) return "No entries";

        StringBuilder sb = new StringBuilder();
        for (Integer len : lengths.keySet()) {
            sb.append(len)
                    .append(": ")
                    .append(lengths.get(len))
                    .append("  ");
        }
        return sb.toString();
    }
}
