package com.example.packinglist.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PackingEntry {
    private String po;
    private String itemNo;
    private int qty;
    private String notes;
    private double unitValue; // USD unit value for FOB file

    // Additional constructor for backward compatibility
    public PackingEntry(String po, String itemNo, int qty, String notes) {
        this.po = po;
        this.itemNo = itemNo;
        this.qty = qty;
        this.notes = notes;
        this.unitValue = 0.0;
    }
}
