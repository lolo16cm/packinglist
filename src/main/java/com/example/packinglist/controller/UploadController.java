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

        InputStreamResource resource = new InputStreamResource(new FileInputStream(packingList));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + packingList.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }


    public List<PackingEntry> parseCsv(MultipartFile file) throws IOException {
        Reader reader = new InputStreamReader(file.getInputStream());
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();
        Iterable<CSVRecord> records = format.parse(reader);

        List<PackingEntry> result = new ArrayList<>();

        for (CSVRecord record : records) {
            result.add(new PackingEntry(
                    record.get("PO.NO"),
                    record.get("ITEM NO"),
                    Integer.parseInt(record.get("QTY")),
                    record.get("NOTES").isEmpty() ? "" : record.get("NOTES")
            ));
        }
        return result;
    }

    public String extractTrackingNumber(MultipartFile image) throws Exception {
        ITesseract tesseract = new Tesseract();
        File temp = File.createTempFile("ups", ".png");
        image.transferTo(temp);
        String text = tesseract.doOCR(temp);
        Matcher matcher = Pattern.compile("1Z[0-9A-Z]{16}").matcher(text);
        return matcher.find() ? matcher.group() : "NOT_FOUND";
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
}
