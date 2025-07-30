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
            @RequestParam("weight") double weight,
            @RequestParam(value = "fileType", defaultValue = "packing") String fileType // New parameter to choose file type
    ) throws Exception {

        List<PackingEntry> entries = parseCsv(csvFile);
        String tracking = extractTrackingNumber(imageFile);
        String today = new SimpleDateFormat("yyMMdd").format(new Date());

        File outputFile;
        String fileName;
        
        if ("fob".equals(fileType)) {
            // Generate FOB CSV file
            outputFile = generateFobCsv(today, entries);
            fileName = "fob-" + today + ".csv";
        } else {
            // Generate standard packing list
            outputFile = generatePackingListCsv(today, entries, tracking, weight, boxes, rmb, rate);
            fileName = "packing-list-" + today + ".csv";
        }

        // Use FileSystemResource which properly handles cleanup and provides better support for temporary files
        Resource resource = new org.springframework.core.io.FileSystemResource(outputFile) {
            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(outputFile) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        // Delete the temporary file after the stream is closed
                        if (outputFile.exists()) {
                            outputFile.delete();
                        }
                    }
                };
            }
        };
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
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
                // Handle different possible column header formats for invoice
                String poNo = getFieldValue(record, "PO.NO", "PO/NO.");
                String itemNo = getFieldValue(record, "ITEM NO", "ITEM NO.");
                String unitValue = getFieldValue(record, "UNIT VALUE(USD)", "");
                
                // Safely parse quantity with proper error handling
                int qty = parseQuantity(record.get("QTY"));
                
                // Parse unit value for FOB file generation
                double unitValueDouble = parseUnitValue(unitValue);
                
                result.add(new PackingEntry(
                        poNo,
                        itemNo,
                        qty,
                        "", // Notes field - empty for now, will be populated as needed
                        unitValueDouble // Add unit value to model
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


    public File generatePackingListCsv(String date, List<PackingEntry> entries, String tracking, double weight, int boxes, double rmb, double rate) throws IOException {
        String arrival = "XR" + date;
        String po = "W" + date;
        double upsFreight = weight * rmb / rate;
        
        // Calculate total quantity
        int totalQty = entries.stream().mapToInt(PackingEntry::getQty).sum();

        File file = File.createTempFile("packing-list-" + date, ".csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("ARRIVAL#: " + arrival + "\n");
            writer.write("AMNT:\n");
            writer.write("DATE:\n");
            writer.write(String.format("UPS FREIGHT: %.1f KG * %.0f RMB / %.2f RATE = $%.2f\n", weight, rmb, rate, upsFreight));
            writer.write(String.format("GROSS WEIGHT: %.1f KG, %d BOXES\n", weight, boxes));
            writer.write("UPS TRACKING#: " + tracking + "\n\n");

            writer.write("P.O#: " + po + "\n");
            writer.write("PO/NO.,ITEM NO.,QTY,NOTES\n");
            for (PackingEntry entry : entries) {
                writer.write(
                        entry.getPo() + "," +
                                entry.getItemNo() + "," +
                                entry.getQty() + "," +
                                "\n" // Empty notes field
                );
            }
            // Add total row
            writer.write("TOTAL,," + totalQty + ",\n");
        }
        return file;
    }

    public File generateFobCsv(String date, List<PackingEntry> entries) throws IOException {
        File file = File.createTempFile("fob-" + date, ".csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Write header with new column names
            writer.write("PO#,ITEM#,CASE_QTY,FOB\n");
            
            // Write data rows
            for (PackingEntry entry : entries) {
                writer.write(
                        entry.getPo() + "," +
                                entry.getItemNo() + "," +
                                entry.getQty() + "," +
                                String.format("%.2f", entry.getUnitValue()) + "\n"
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

    /**
     * Safely parses a unit value string to a double with proper error handling.
     * 
     * @param unitValueString The unit value string from CSV
     * @return The parsed unit value as double, or 0.0 if parsing fails
     */
    private double parseUnitValue(String unitValueString) {
        if (unitValueString == null || unitValueString.trim().isEmpty()) {
            return 0.0;
        }
        
        try {
            // Remove any currency symbols or extra characters, keep only numbers and decimal point
            String cleanValue = unitValueString.trim().replaceAll("[^0-9.]", "");
            return Double.parseDouble(cleanValue);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid unit value '" + unitValueString + "'. Using 0.0 as default.");
            return 0.0;
        }
    }
}
