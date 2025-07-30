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

}
