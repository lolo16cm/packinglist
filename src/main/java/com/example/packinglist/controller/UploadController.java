package com.example.packinglist.controller;
import org.springframework.core.io.Resource;
import com.example.packinglist.model.PackingEntry;
import com.example.packinglist.model.InvoiceEntry;
import com.aspose.ocr.AsposeOCR;
import com.aspose.ocr.OcrInput;
import com.aspose.ocr.InputType;
import com.aspose.ocr.RecognitionResult;
import com.aspose.ocr.RecognitionSettings;
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
import java.util.regex.*;
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
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
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

            // Validate image file only if provided
            if (imageFile != null && !imageFile.isEmpty()) {
                String imageContentType = imageFile.getContentType();
                if (imageContentType == null || !imageContentType.startsWith("image/")) {
                    return ResponseEntity.badRequest()
                        .body("Please upload a valid image file");
                }
            }

            List<InvoiceEntry> invoiceEntries = parseInvoiceCsv(csvFile);
            if (invoiceEntries.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("CSV file appears to be empty or has invalid format. Please check your CSV file contains the required columns: PO/NO., ITEM NO., DESCRIPTION OF GOODS, QTY, UNIT VALUE (USD)");
            }

            String tracking = extractTrackingNumber(imageFile);
            String today = new SimpleDateFormat("yyMMdd").format(new Date());

            // Generate both files
            File packingList = generatePackingList(today, invoiceEntries, tracking, weight, boxes, rmb, rate);
            File msdosCsv = generateMsdosCsv(today, invoiceEntries);
            
            // Create a ZIP file containing both files
            File zipFile = createZipFile(today, packingList, msdosCsv);

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
                if (e.getMessage().contains("OCR") || e.getMessage().contains("recognition")) {
                    errorMessage += "There was an issue processing the image file. Please ensure it's a clear image containing a UPS tracking number.";
                } else if (e.getMessage().contains("CSV") || e.getMessage().contains("parse")) {
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
        // If no image is provided, return empty string
        if (image == null || image.isEmpty()) {
            return "";
        }
        
        File temp = File.createTempFile("ups", ".png");
        try {
            // Save uploaded image to temporary file
            image.transferTo(temp);
            
            // Initialize Aspose.OCR API
            AsposeOCR api = new AsposeOCR();
            
            // Create OCR input from the temporary file
            OcrInput input = new OcrInput(InputType.SingleImage);
            input.add(temp.getAbsolutePath());
            
            // Configure recognition settings for better accuracy
            RecognitionSettings settings = new RecognitionSettings();
            settings.setLanguage(com.aspose.ocr.Language.Eng);
            
            // Perform OCR recognition
            ArrayList<RecognitionResult> results = api.Recognize(input, settings);
            
            if (results != null && !results.isEmpty()) {
                String text = results.get(0).recognition_text;
                if (text != null) {
                    // Extract UPS tracking number pattern: 1Z followed by 16 alphanumeric characters
                    Matcher matcher = Pattern.compile("1Z[0-9A-Z]{16}").matcher(text.toUpperCase());
                    return matcher.find() ? matcher.group() : "";
                }
            }
            
            return "";
        } catch (Exception e) {
            // Log the error for debugging
            System.err.println("OCR Error: " + e.getMessage());
            e.printStackTrace();
            // Return empty string instead of throwing exception to prevent app crash
            return "";
        } finally {
            // Always clean up the temporary file
            if (temp.exists()) {
                temp.delete();
            }
        }
    }


    public File generatePackingList(String date, List<InvoiceEntry> invoiceEntries, String tracking, double weight, int boxes, double rmb, double rate) throws IOException {
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
            writer.write("PO#,ITEM#,QTY,NOTES\n");
            for (InvoiceEntry entry : invoiceEntries) {
                writer.write(
                        entry.getPoNo() + "," +
                                entry.getItemNo() + "," +
                                entry.getQty() + "," +
                                "" + "\n" // Empty notes field as per requirement
                );
            }
        }
        return file;
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

    public File createZipFile(String date, File packingList, File msdosCsv) throws IOException {
        File zipFile = File.createTempFile("packing-files-" + date, ".zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // Add packing list to zip
            addFileToZip(zos, packingList, "packing-list-" + date + ".csv");
            
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
