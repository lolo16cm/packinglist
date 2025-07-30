package com.example.packinglist.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InvoiceEntry {
    private String poNo;        // PO/NO.
    private String itemNo;      // ITEM NO.
    private String description; // DESCRIPTION OF GOODS
    private int qty;           // QTY
    private double unitValue;  // UNIT VALUE (USD) - FOB
}