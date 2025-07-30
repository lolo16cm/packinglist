package com.example.packinglist.controller;
import org.springframework.core.io.Resource;
import com.example.packinglist.model.PackingEntry;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

@Controller
public class UploadController {

    @GetMapping("/")
    public String showForm() {
        return "upload";
    }

    @PostMapping("/upload")
    public ResponseEntity<Resource> handleUpload(
            @RequestParam("csvFile") MultipartFile csvFile,
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam("rmb") double rmb,
            @RequestParam("rate") double rate,
            @RequestParam("boxes") int boxes,
            @RequestParam("weight") double weight // ✅ New: manual input
    ) throws Exception {

        List<PackingEntry> entries = parseCsv(csvFile);
        String tracking = extractTrackingNumber(imageFile);
        String today = new SimpleDateFormat("yyMMdd").format(new Date());

        File packingList = generateCsv(today, entries, tracking, weight, boxes, rmb, rate);

        // Use FileSystemResource which properly handles cleanup and provides better support for temporary files
        Resource resource = new org.springframework.core.io.FileSystemResource(packingList) {
            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(packingList) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        // Delete the temporary file after the stream is closed
                        if (packingList.exists()) {
                            packingList.delete();
                        }
                    }
                };
            }
        };
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + packingList.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }


    public List<PackingEntry> parseCsv(MultipartFile file) throws IOException {
        List<PackingEntry> result = new ArrayList<>();
        
        // Use try-with-resources to ensure proper cleanup of streams
        try (Reader reader = new InputStreamReader(file.getInputStream())) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build();
            Iterable<CSVRecord> records = format.parse(reader);

            for (CSVRecord record : records) {
                // Handle different possible column header formats
                String poNo = getFieldValue(record, "PO.NO", "PO/NO.");
                String itemNo = getFieldValue(record, "ITEM NO", "ITEM NO.");
                String notes = getFieldValue(record, "NOTES", "");
                
                // Safely parse quantity with proper error handling
                int qty = parseQuantity(record.get("QTY"));
                
                result.add(new PackingEntry(
                        poNo,
                        itemNo,
                        qty,
                        // Safely handle NOTES field to prevent null pointer exceptions
                        (notes != null && !notes.isEmpty()) ? notes : ""
                ));
            }
        }
        return result;
    }

    /**
     * Helper method to get field value with fallback column names
     */
    private String getFieldValue(CSVRecord record, String primaryHeader, String fallbackHeader) {
        try {
            // Try primary header first
            if (record.isMapped(primaryHeader)) {
                return record.get(primaryHeader);
            }
            // Try fallback header
            if (record.isMapped(fallbackHeader)) {
                return record.get(fallbackHeader);
            }
            // Return empty string if neither exists
            return "";
        } catch (IllegalArgumentException e) {
            // If neither header exists, return empty string
            return "";
        }
    }

    public String extractTrackingNumber(MultipartFile image) throws Exception {
        ITesseract tesseract = new Tesseract();
        File temp = File.createTempFile("ups", ".png");
        try {
            image.transferTo(temp);
            String text = tesseract.doOCR(temp);
            Matcher matcher = Pattern.compile("1Z[0-9A-Z]{16}").matcher(text);
            return matcher.find() ? matcher.group() : "NOT_FOUND";
        } finally {
            // Always clean up the temporary file
            if (temp.exists()) {
                temp.delete();
            }
        }
    }


    public File generateCsv(String date, List<PackingEntry> entries, String tracking, double weight, int boxes, double rmb, double rate) throws IOException {
        String arrival = "XR" + date;
        String po = "W" + date;
        double upsFreight = weight * rmb / rate;

        File file = File.createTempFile("packing-list-" + date, ".csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("ARRIVAL#: " + arrival + "\n");
            writer.write("AMNT:\n");
            writer.write("DATE:\n");
            writer.write(String.format("UPS FREIGHT: %.1f KG * %.0f RMB / %.2f RATE = $%.2f\n", weight, rmb, rate, upsFreight));
            writer.write(String.format("GROSS WEIGHT: %.1f KG, %d BOXES\n", weight, boxes));
            writer.write("UPS TRACKING#: " + tracking + "\n\n");

            writer.write("P.O#: " + po + "\n");
            writer.write("PO/NO.,ITEM NO,QTY,NOTES\n");
            for (PackingEntry entry : entries) {
                writer.write(
                        entry.getPo() + "," +
                                entry.getItemNo() + "," +
                                entry.getQty() + "," +
                                entry.getNotes() + "\n"
                );

            }
        }
        return file;
    }

    /**
     * Safely parses a quantity string to an integer with proper error handling.
     * 
     * @param qtyString The quantity string from CSV
     * @return The parsed quantity as integer, or 0 if parsing fails
     */
    private int parseQuantity(String qtyString) {
        if (qtyString == null || qtyString.trim().isEmpty()) {
            // Return 0 for empty/null quantities
            return 0;
        }
        
        try {
            // Trim whitespace and parse
            String cleanQty = qtyString.trim();
            
            // Handle decimal numbers by parsing as double first, then converting to int
            if (cleanQty.contains(".")) {
                double doubleValue = Double.parseDouble(cleanQty);
                return (int) Math.round(doubleValue);
            }
            
            return Integer.parseInt(cleanQty);
        } catch (NumberFormatException e) {
            // Log the error and return 0 as default
            System.err.println("Warning: Invalid quantity value '" + qtyString + "'. Using 0 as default.");
            return 0;
        }
    }
}
