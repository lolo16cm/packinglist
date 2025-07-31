package com.example.packinglist;

import com.example.packinglist.controller.UploadController;
import com.example.packinglist.model.PackingEntry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class LexicalSortingIntegrationTest {

    @Test
    public void testLexicalSortingOfItemNumbers() throws IOException {
        // Arrange
        UploadController controller = new UploadController();
        
        // Create a CSV content with mixed item numbers that will test lexical sorting
        String csvContent = "PO/NO.,ITEM NO.,DESCRIPTION,QTY,UNIT VALUE\n" +
                           "PO001,1015,Item A,10,$5.00\n" +
                           "PO002,100,Item B,20,$3.00\n" +
                           "PO003,2,Item C,5,$10.00\n" +
                           "PO004,1016B,Item D,15,$4.50\n" +
                           "PO005,a8921,Item E,8,$7.25\n" +
                           "PO006,23463,Item F,12,$6.00\n" +
                           "PO007,b3549,Item G,25,$2.75\n" +
                           "PO008,1001,Item H,30,$1.50\n";
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        // Act
        List<PackingEntry> result = controller.parseCsv(file);
        
        // Assert
        assertEquals(8, result.size(), "Should have 8 entries");
        
        // Verify lexical sorting order
        String[] expectedOrder = {"100", "1001", "1015", "1016B", "2", "23463", "a8921", "b3549"};
        
        for (int i = 0; i < expectedOrder.length; i++) {
            assertEquals(expectedOrder[i], result.get(i).getItemNo(), 
                String.format("Item at position %d should be '%s' but was '%s'", 
                    i, expectedOrder[i], result.get(i).getItemNo()));
        }
        
        // Additional verification: ensure it's actually lexically sorted
        for (int i = 0; i < result.size() - 1; i++) {
            String current = result.get(i).getItemNo();
            String next = result.get(i + 1).getItemNo();
            assertTrue(current.compareTo(next) <= 0, 
                String.format("Items should be lexically sorted: '%s' should come before or equal to '%s'", 
                    current, next));
        }
        
        System.out.println("✅ Lexical sorting test PASSED!");
        System.out.println("Sorted item numbers:");
        for (int i = 0; i < result.size(); i++) {
            System.out.printf("  %d. %s\n", i + 1, result.get(i).getItemNo());
        }
    }
    
    @Test
    public void testLexicalVsNumericSortingDifference() throws IOException {
        // This test demonstrates the difference between lexical and numeric sorting
        UploadController controller = new UploadController();
        
        String csvContent = "PO/NO.,ITEM NO.,DESCRIPTION,QTY,UNIT VALUE\n" +
                           "PO001,2,Item Two,10,$5.00\n" +
                           "PO002,10,Item Ten,20,$3.00\n" +
                           "PO003,100,Item Hundred,5,$10.00\n" +
                           "PO004,20,Item Twenty,15,$4.50\n" +
                           "PO005,3,Item Three,8,$7.25\n";
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        List<PackingEntry> result = controller.parseCsv(file);
        
        // In lexical sorting: "10", "100", "2", "20", "3"
        // In numeric sorting it would be: "2", "3", "10", "20", "100"
        String[] expectedLexicalOrder = {"10", "100", "2", "20", "3"};
        
        assertEquals(5, result.size());
        
        for (int i = 0; i < expectedLexicalOrder.length; i++) {
            assertEquals(expectedLexicalOrder[i], result.get(i).getItemNo(),
                String.format("Lexical position %d should be '%s'", i, expectedLexicalOrder[i]));
        }
        
        System.out.println("✅ Lexical vs Numeric difference test PASSED!");
        System.out.println("Lexical order (what we get): " + 
            result.stream().map(PackingEntry::getItemNo).toList());
        System.out.println("Numeric order (what we DON'T get): [2, 3, 10, 20, 100]");
    }
}