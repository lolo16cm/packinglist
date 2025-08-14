package com.example.packinglist.controller;
import org.springframework.core.io.Resource;
import com.example.packinglist.model.PackingEntry;
import com.example.packinglist.model.InvoiceEntry;

import java.util.ArrayList;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
public class UploadController {

    @GetMapping("/")
    public String showForm() {
        return "upload";
    }

    @PostMapping("/upload")
    public ResponseEntity<?> handleUpload(
            @RequestParam("csvFile") MultipartFile csvFile,
            @RequestParam(value = "manualTracking", required = false) String manualTracking,
            @RequestParam("rmb") double rmb,
            @RequestParam("rate") double rate,
            @RequestParam("boxes") int boxes,
            @RequestParam("weight") double weight // ✅ New: manual input
    ) {
        try {
            // Validate input files
            if (csvFile.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("CSV file is required and cannot be empty");
            }

            // Validate file types
            String csvContentType = csvFile.getContentType();
            if (csvContentType == null || (!csvContentType.equals("text/csv") && !csvContentType.equals("application/vnd.ms-excel"))) {
                return ResponseEntity.badRequest()
                    .body("Please upload a valid CSV file");
            }

            List<InvoiceEntry> invoiceEntries = parseInvoiceCsv(csvFile);
            if (invoiceEntries.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("CSV file appears to be empty or has invalid format. Please check your CSV file contains the required columns: PO/NO., ITEM NO., DESCRIPTION OF GOODS, QTY, UNIT VALUE (USD)");
            }

            // Use manual tracking number if provided
            String tracking = "";
            if (manualTracking != null && !manualTracking.trim().isEmpty()) {
                tracking = manualTracking.trim();
                System.out.println("Using manual tracking number: " + tracking);
            } else {
                System.out.println("No manual tracking number provided");
            }
            
            String today = new SimpleDateFormat("yyMMdd").format(new Date());

            // Generate both files
            File packingList = generatePackingList(today, invoiceEntries, tracking, weight, boxes, rmb, rate);
            File msdosCsv = generateMsdosCsv(today, invoiceEntries);
            File packingListHtml = generatePackingListHtml(today, invoiceEntries, tracking, weight, boxes, rmb, rate);
            
            // Create a ZIP file containing all files
            File zipFile = createZipFile(today, packingList, msdosCsv, packingListHtml);

            // Use FileSystemResource which properly handles cleanup and provides better support for temporary files
            Resource resource = new org.springframework.core.io.FileSystemResource(zipFile) {
                @Override
                public InputStream getInputStream() throws IOException {
                    return new FileInputStream(zipFile) {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            // Delete the temporary files after the stream is closed
                            if (packingList.exists()) packingList.delete();
                            if (msdosCsv.exists()) msdosCsv.delete();
                            if (packingListHtml.exists()) packingListHtml.delete(); // Added for HTML
                            if (zipFile.exists()) zipFile.delete();
                        }
                    };
                }
            };
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=packing-files-" + today + ".zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            // Log the error for debugging
            System.err.println("Error processing upload: " + e.getMessage());
            e.printStackTrace();
            
            // Return user-friendly error message
            String errorMessage = "An error occurred while processing your files. ";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("CSV") || e.getMessage().contains("parse")) {
                    errorMessage += "There was an issue parsing the CSV file. Please check the file format and ensure it contains the required columns.";
                } else {
                    errorMessage += "Please check your files and try again.";
                }
            } else {
                errorMessage += "Please check your files and try again.";
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body(errorMessage);
        }
    }


    public List<InvoiceEntry> parseInvoiceCsv(MultipartFile file) throws IOException {
        List<InvoiceEntry> result = new ArrayList<>();
        
        // Use try-with-resources to ensure proper cleanup of streams
        try (Reader reader = new InputStreamReader(file.getInputStream())) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build();
            Iterable<CSVRecord> records = format.parse(reader);

            for (CSVRecord record : records) {
                // Handle different possible column header formats
                String poNo = getFieldValue(record, "PO/NO.", "PO.NO");
                String itemNo = getFieldValue(record, "ITEM NO.", "ITEM NO");
                String description = getFieldValue(record, "DESCRIPTION OF GOODS", "DESCRIPTION");
                
                // Safely parse quantity with proper error handling
                int qty = parseQuantity(getFieldValue(record, "QTY", "QTY"));
                
                // Parse unit value (FOB)
                double unitValue = parseUnitValue(getFieldValue(record, "UNIT VALUE (USD)", "UNIT VALUE"));
                
                result.add(new InvoiceEntry(
                        poNo,
                        itemNo,
                        description,
                        qty,
                        unitValue
                ));
            }
        }
        
        // Sort by item number: lexical (alphabetical) sorting
        result.sort((a, b) -> {
            String itemA = a.getItemNo();
            String itemB = b.getItemNo();
            
            // Pure lexical comparison - treats everything as strings
            return itemA.compareTo(itemB);
        });
        
        return result;
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
        
        // Sort by item number: lexical (alphabetical) sorting
        result.sort((a, b) -> {
            String itemA = a.getItemNo();
            String itemB = b.getItemNo();
            
            // Pure lexical comparison - treats everything as strings
            return itemA.compareTo(itemB);
        });
        
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




    public File generatePackingList(String date, List<InvoiceEntry> invoiceEntries, String tracking, double weight, int boxes, double rmb, double rate) throws IOException {
        String arrival = "XR" + date;
        String po = "W" + date;
        double upsFreight = weight * rmb / rate;

        File file = File.createTempFile("packing-list-" + date, ".csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Get today's date for dynamic formatting
            String todayMonth = new SimpleDateFormat("MM").format(new Date());
            String todayDate = new SimpleDateFormat("dd").format(new Date());
            
            writer.write("ARRIVAL#: " + arrival + "\n");
            writer.write("DATE:\n");
            writer.write("P.O.#W25" + todayMonth + todayDate + "=>AMNT:\n");
            writer.write("P.O.#WONA25" + todayMonth + todayDate + ",8%DISC$321.07=>AMNT:\n");
            writer.write("\n"); // Empty row between DATE: and UPS FREIGHT:
            writer.write(String.format("UPS FREIGHT: %.0f RMB / %.2f RATE = $%.2f\n", rmb, rate, upsFreight));
            // Combine weight and boxes info in one cell with new format
            writer.write(String.format("WEIGHT & BOXES: %.1f KG || %d BOXES\n", weight, boxes));
            if (tracking != null && !tracking.isEmpty()) {
                writer.write("UPS TRACKING#: " + tracking + "\n\n");
            } else {
                writer.write("UPS TRACKING#: \n\n");
            }

            writer.write("P.O#: " + po + "\n");
            // Add empty column between QTY and NOTES
            writer.write("PO#,ITEM#,QTY,,NOTES\n");
            
            // Calculate total quantity
            int totalQty = 0;
            for (InvoiceEntry entry : invoiceEntries) {
                writer.write(
                        entry.getPoNo() + "," +
                                entry.getItemNo() + "," +
                                entry.getQty() + "," +
                                "," + // Empty column
                                "" + "\n" // Empty notes field as per requirement
                );
                totalQty += entry.getQty();
            }
            
            // Add total qty row at the end
            writer.write(",,TOTAL QTY: " + totalQty + ",,\n");
        }
        return file;
    }

    public File generatePackingListHtml(String date, List<InvoiceEntry> invoiceEntries, String tracking, double weight, int boxes, double rmb, double rate) throws IOException {
        // Configure items per page (can be made configurable via parameter)
        int itemsPerPage = 72; // 36 items per column × 2 columns per page
        return generatePackingListHtmlWithPagination(date, invoiceEntries, tracking, weight, boxes, rmb, rate, itemsPerPage);
    }

    /**
     * Generates a multi-page HTML packing list with configurable pagination.
     * Each page contains two columns: left column has first N items, right column has next N items.
     * Header information (arrival, amount, date, PO#, UPS freight) appears once at the top.
     * 
     * @param itemsPerPage Total items per page (will be split evenly between left and right columns, default 72 for 36 rows each)
     */
    public File generatePackingListHtmlWithPagination(String date, List<InvoiceEntry> invoiceEntries, String tracking, double weight, int boxes, double rmb, double rate, int itemsPerPage) throws IOException {
        String po = "W" + date;
        // Calculate arrival date as P.O.# + 7 days
        String arrival;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
            Date poDate = sdf.parse(date);
            Calendar cal = Calendar.getInstance();
            cal.setTime(poDate);
            cal.add(Calendar.DAY_OF_MONTH, 7);
            String arrivalDate = sdf.format(cal.getTime());
            arrival = "XR" + arrivalDate;
        } catch (Exception e) {
            // Fallback to original calculation if parsing fails
            arrival = "XR" + date;
        }
        double upsFreight = weight * rmb / rate;

        File file = File.createTempFile("packing-list-" + date, ".html");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("<!DOCTYPE html>\n");
            writer.write("<html>\n<head>\n");
            writer.write("<meta charset=\"UTF-8\">\n");
            writer.write("<title>Packing List - " + date + "</title>\n");
            writer.write("<style>\n");
            
            // Print styles
            writer.write("@media print {\n");
            writer.write("  @page { size: A4 portrait; margin: 0.4in 0.3in; }\n");
            writer.write("  body { margin: 0; padding: 0; }\n");
            writer.write("  .no-print { display: none; }\n");
            writer.write("  .page-break { page-break-before: always; }\n");
            writer.write("}\n");
            
            // General styles
            writer.write("body { font-family: Arial, sans-serif; margin: 0.4in 0.3in; padding: 0; font-size: 10px; line-height: 1.2; }\n");
            writer.write("table { border-collapse: collapse; }\n");
            writer.write("td, th { padding: 2px 4px; vertical-align: top; }\n");
            
            // Header section styles
            writer.write(".header-container { display: flex; justify-content: space-between; margin-bottom: 10px; }\n");
            writer.write(".header-left, .header-right { border: 2px solid #000; padding: 4px; }\n");
            writer.write(".header-left { width: 200px; }\n");
            writer.write(".header-right { width: 300px; }\n");
            writer.write(".header-row { display: flex; margin-bottom: 3px; }\n");
            writer.write(".header-label { font-weight: bold; width: 70px; }\n");
            writer.write(".header-value { flex: 1; border-bottom: 1px solid #000; margin-left: 5px; min-height: 12px; }\n");
            
            // Data tables container
            writer.write(".tables-container { display: flex; gap: 10px; }\n");
            writer.write(".table-column { flex: 1; }\n");
            writer.write(".data-table { width: 100%; border: 2px solid #000; }\n");
            writer.write(".data-table th { border: 1px solid #000; background-color: #f0f0f0; font-weight: bold; text-align: center; padding: 3px; font-size: 10px; }\n");
            writer.write(".data-table th.item-header, .data-table th.qty-header { font-size: 16px; }\n");
            writer.write(".data-table td { border: 1px solid #000; text-align: center; padding: 2px; font-size: 8px; }\n");
            writer.write(".duplicate-item { border: 3px solid red; border-radius: 50%; }\n");
            writer.write(".po-col { width: 15%; }\n");
            writer.write(".item-col { width: 25%; }\n");
            writer.write(".qty-col { width: 10%; }\n");
            writer.write(".receive-check-col { width: 12%; }\n");
            writer.write(".notes-col { width: 38%; }\n");
            
            writer.write("</style>\n");
            writer.write("</head>\n<body>\n");

            // Calculate pagination
            int totalEntries = invoiceEntries.size();
            int totalPages = (int) Math.ceil((double) totalEntries / itemsPerPage);
            
            // Calculate total quantity
            int totalQty = 0;
            for (InvoiceEntry entry : invoiceEntries) {
                totalQty += entry.getQty();
            }
            
            // Generate each page
            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                // Add page break for all pages except the first
                if (pageNum > 0) {
                    writer.write("<div class=\"page-break\"></div>\n");
                }
                
                // Header section for each page
                writePageHeader(writer, arrival, po, upsFreight, rmb, rate, weight, boxes, tracking, pageNum + 1, totalPages);
                
                // Data tables section for this page
                boolean isLastPage = (pageNum == totalPages - 1);
                writePageData(writer, invoiceEntries, pageNum, itemsPerPage, isLastPage, totalQty);
            }

            writer.write("</body>\n</html>\n");
        }
        return file;
    }

    /**
     * Writes the header section for a single page
     */
    private void writePageHeader(BufferedWriter writer, String arrival, String po, double upsFreight, 
                                double rmb, double rate, double weight, int boxes, String tracking, 
                                int currentPage, int totalPages) throws IOException {
        writer.write("<div class=\"header-container\">\n");
        
        // Left header box
        writer.write("<div class=\"header-left\">\n");
        writer.write("<div class=\"header-row\">\n");
        writer.write("<span class=\"header-label\">ARRIVAL#:</span>\n");
        writer.write("<span class=\"header-value\">" + arrival + "</span>\n");
        writer.write("</div>\n");
        // Get today's date for dynamic formatting
        String todayMonth = new SimpleDateFormat("MM").format(new Date());
        String todayDate = new SimpleDateFormat("dd").format(new Date());
        
        writer.write("<div class=\"header-row\">\n");
        writer.write("<span class=\"header-label\">DATE:</span>\n");
        writer.write("<span class=\"header-value\"></span>\n");
        writer.write("</div>\n");
        writer.write("<div class=\"header-row\">\n");
        writer.write("<span class=\"header-label\">P.O.#W25" + todayMonth + todayDate + "=>AMNT:</span>\n");
        writer.write("<span class=\"header-value\"></span>\n");
        writer.write("</div>\n");
        writer.write("<div class=\"header-row\">\n");
        writer.write("<span class=\"header-label\">P.O.#WONA25" + todayMonth + todayDate + ",8%DISC$321.07=>AMNT:</span>\n");
        writer.write("<span class=\"header-value\"></span>\n");
        writer.write("</div>\n");
        writer.write("</div>\n");
        
        // Right header box
        writer.write("<div class=\"header-right\">\n");
        writer.write("<div style=\"text-align: center; font-weight: bold; margin-bottom: 5px; font-size: 11px;\">UPS FREIGHT:</div>\n");
        writer.write("<div style=\"text-align: center; margin-bottom: 5px; font-size: 16px;\">" + String.format("%.0f RMB / %.2f RATE = $%.2f", rmb, rate, upsFreight) + "</div>\n");
        writer.write("<div class=\"header-row\">\n");
        writer.write("<span style=\"width: 80px; font-size: 9px;\">GROSS WEIGHT:</span>\n");
        writer.write("<span class=\"header-value\">" + String.format("%.1f", weight) + "</span>\n");
        writer.write("</div>\n");
        writer.write("<div class=\"header-row\">\n");
        writer.write("<span style=\"width: 80px; font-size: 9px;\">BOXES:</span>\n");
        writer.write("<span class=\"header-value\">" + boxes + "</span>\n");
        writer.write("</div>\n");
        writer.write("<div class=\"header-row\">\n");
        writer.write("<span style=\"width: 80px; font-size: 9px;\">UPS TRACKING#:</span>\n");
        writer.write("<span class=\"header-value\">" + (tracking != null ? tracking : "") + "</span>\n");
        writer.write("</div>\n");
        // Add page numbers
        if (totalPages > 1) {
            writer.write("<div style=\"text-align: center; margin-top: 5px; font-size: 9px; font-weight: bold;\">\n");
            writer.write("Page " + currentPage + " of " + totalPages + "\n");
            writer.write("</div>\n");
        }
        writer.write("</div>\n");
        writer.write("</div>\n");
    }

    /**
     * Writes the data table section for a single page with left and right columns
     */
    private void writePageData(BufferedWriter writer, List<InvoiceEntry> invoiceEntries, int pageNum, int itemsPerPage, boolean isLastPage, int totalQty) throws IOException {
        writer.write("<div class=\"tables-container\">\n");
        
        // Calculate the range of items for this page
        int startIndex = pageNum * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, invoiceEntries.size());
        int itemsOnThisPage = endIndex - startIndex;
        
        // Find duplicate item numbers on this page
        Set<String> duplicateItems = findDuplicateItemNumbers(invoiceEntries, startIndex, endIndex);
        
        // Calculate entries per column for this page (split evenly)
        int entriesPerColumn = (int) Math.ceil(itemsOnThisPage / 2.0);
        
        // Left column
        writer.write("<div class=\"table-column\">\n");
        writeTableHeader(writer);
        
        for (int i = 0; i < entriesPerColumn && (startIndex + i) < endIndex; i++) {
            InvoiceEntry entry = invoiceEntries.get(startIndex + i);
            writeTableRow(writer, entry, duplicateItems.contains(entry.getItemNo()));
        }
        
        writer.write("</table>\n");
        writer.write("</div>\n");
        
        // Right column
        writer.write("<div class=\"table-column\">\n");
        writeTableHeader(writer);
        
        for (int i = entriesPerColumn; i < itemsOnThisPage && (startIndex + i) < endIndex; i++) {
            InvoiceEntry entry = invoiceEntries.get(startIndex + i);
            writeTableRow(writer, entry, duplicateItems.contains(entry.getItemNo()));
        }
        
        // Add total quantity row at the end of the last page
        if (isLastPage) {
            writer.write("<tr style=\"border-top: 3px solid #000; font-weight: bold;\">\n");
            writer.write("<td></td>\n");
            writer.write("<td>TOTAL QTY:</td>\n");
            writer.write("<td style=\"font-size: 16px;\">" + totalQty + "</td>\n");
            writer.write("<td></td>\n");
            writer.write("<td></td>\n");
            writer.write("</tr>\n");
        }
        
        writer.write("</table>\n");
        writer.write("</div>\n");
        writer.write("</div>\n");
    }

    /**
     * Writes the table header
     */
    private void writeTableHeader(BufferedWriter writer) throws IOException {
        writer.write("<table class=\"data-table\">\n");
        writer.write("<tr>\n");
        writer.write("<th class=\"po-col\">PO/NO</th>\n");
        writer.write("<th class=\"item-col item-header\">ITEM NO.</th>\n");
        writer.write("<th class=\"qty-col qty-header\">QTY</th>\n");
        writer.write("<th class=\"receive-check-col\">RECEIVE CHECK</th>\n");
        writer.write("<th class=\"notes-col\">NOTES</th>\n");
        writer.write("</tr>\n");
    }

    /**
     * Writes a single table row
     */
    private void writeTableRow(BufferedWriter writer, InvoiceEntry entry, boolean isDuplicate) throws IOException {
        writer.write("<tr>\n");
        writer.write("<td>" + entry.getPoNo() + "</td>\n");
        String itemClass = isDuplicate ? " class=\"duplicate-item\"" : "";
        writer.write("<td" + itemClass + ">" + entry.getItemNo() + "</td>\n");
        writer.write("<td>" + entry.getQty() + "</td>\n");
        writer.write("<td><input type=\"checkbox\"></td>\n");
        writer.write("<td></td>\n");
        writer.write("</tr>\n");
    }

    /**
     * Finds duplicate item numbers within a given range
     */
    private Set<String> findDuplicateItemNumbers(List<InvoiceEntry> invoiceEntries, int startIndex, int endIndex) {
        Map<String, Integer> itemCounts = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        
        // Count occurrences of each item number in the range
        for (int i = startIndex; i < endIndex; i++) {
            String itemNo = invoiceEntries.get(i).getItemNo();
            itemCounts.put(itemNo, itemCounts.getOrDefault(itemNo, 0) + 1);
        }
        
        // Find items that appear more than once
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            if (entry.getValue() > 1) {
                duplicates.add(entry.getKey());
            }
        }
        
        return duplicates;
    }

    public File generateMsdosCsv(String date, List<InvoiceEntry> invoiceEntries) throws IOException {
        File file = File.createTempFile("import_inv-" + date, ".csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    // Write MS-DOS style CSV with headers: PO#, ITEM#, CASE_QTY, FOB
        writer.write("PO#,ITEM#,CASE_QTY,FOB\r\n"); // MS-DOS line ending
            for (InvoiceEntry entry : invoiceEntries) {
                writer.write(
                        entry.getPoNo() + "," +
                                entry.getItemNo() + "," +
                                entry.getQty() + "," +
                                String.format("%.2f", entry.getUnitValue()) + "\r\n" // MS-DOS line ending
                );
            }
        }
        return file;
    }

    public File createZipFile(String date, File packingList, File msdosCsv, File packingListHtml) throws IOException {
        File zipFile = File.createTempFile("packing-files-" + date, ".zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // Add packing list CSV to zip
            addFileToZip(zos, packingList, "packing-list-" + date + ".csv");
            
            // Add packing list HTML to zip (with bold borders)
            addFileToZip(zos, packingListHtml, "packing-list-" + date + ".html");
            
            // Add MS-DOS CSV to zip
            addFileToZip(zos, msdosCsv, "import_inv-" + date + ".csv");
        }
        
        return zipFile;
    }

    private void addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
        }
        
        zos.closeEntry();
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
            if (tracking != null && !tracking.isEmpty()) {
                writer.write("UPS TRACKING#: " + tracking + "\n\n");
            } else {
                writer.write("UPS TRACKING#: \n\n");
            }

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

    /**
     * Safely parses a unit value string to a double with proper error handling.
     * 
     * @param unitValueString The unit value string from CSV
     * @return The parsed unit value as double, or 0.0 if parsing fails
     */
    private double parseUnitValue(String unitValueString) {
        if (unitValueString == null || unitValueString.trim().isEmpty()) {
            // Return 0.0 for empty/null unit values
            return 0.0;
        }
        
        try {
            // Trim whitespace and remove any currency symbols
            String cleanValue = unitValueString.trim().replaceAll("[^0-9.-]", "");
            
            return Double.parseDouble(cleanValue);
        } catch (NumberFormatException e) {
            // Log the error and return 0.0 as default
            System.err.println("Warning: Invalid unit value '" + unitValueString + "'. Using 0.0 as default.");
            return 0.0;
        }
    }


}
