package com.example.packinglist;

import com.example.packinglist.controller.UploadController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TestTrackingFunction {

    @Test
    public void testTrackingExtractionFromTextFile() throws Exception {
        UploadController controller = new UploadController();
        
        // Create a mock text file with tracking number
        String trackingContent = "UPS Tracking Number: 1Z999AA12345678901\nThis is a sample tracking document for testing OCR functionality.\nThe tracking number should be extracted automatically.";
        MockMultipartFile mockFile = new MockMultipartFile(
            "imageFile", 
            "sample_tracking.txt", 
            "text/plain", 
            trackingContent.getBytes()
        );
        
        // Test tracking extraction
        String result = controller.extractTrackingNumber(mockFile);
        
        // Verify the tracking number was extracted correctly
        assertEquals("1Z999AA12345678901", result, "Tracking number should be extracted correctly from text file");
    }

    @Test
    public void testTrackingExtractionFromEmptyFile() throws Exception {
        UploadController controller = new UploadController();
        
        // Test with empty file
        MockMultipartFile emptyFile = new MockMultipartFile(
            "imageFile", 
            "empty.txt", 
            "text/plain", 
            "".getBytes()
        );
        
        String result = controller.extractTrackingNumber(emptyFile);
        
        // Should return empty string for empty file
        assertEquals("", result, "Empty file should return empty tracking number");
    }

    @Test
    public void testTrackingExtractionFromNullFile() throws Exception {
        UploadController controller = new UploadController();
        
        // Test with null file
        String result = controller.extractTrackingNumber(null);
        
        // Should return empty string for null file
        assertEquals("", result, "Null file should return empty tracking number");
    }

    @Test
    public void testTrackingExtractionWithMultipleFormats() throws Exception {
        UploadController controller = new UploadController();
        
        // Test different UPS tracking number formats
        String[] testCases = {
            "1Z999AA12345678901",
            "1Z 999AA12345678901", // with space
            "Tracking: 1Z999BB98765432109",
            "UPS: 1Z123CC45678901234"
        };
        
        String[] expectedResults = {
            "1Z999AA12345678901",
            "1Z999AA12345678901",
            "1Z999BB98765432109", 
            "1Z123CC45678901234"
        };
        
        for (int i = 0; i < testCases.length; i++) {
            MockMultipartFile mockFile = new MockMultipartFile(
                "imageFile", 
                "test" + i + ".txt", 
                "text/plain", 
                testCases[i].getBytes()
            );
            
            String result = controller.extractTrackingNumber(mockFile);
            assertEquals(expectedResults[i], result, 
                "Test case " + i + " should extract: " + expectedResults[i]);
        }
    }
}