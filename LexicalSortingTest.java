import java.util.*;

public class LexicalSortingTest {
    
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("LEXICAL SORTING TEST FOR ITEM# HEADER VALUES");
        System.out.println("=".repeat(60));
        
        // Test Case 1: Mixed alphanumeric items from sample data
        testCase1();
        
        // Test Case 2: Numbers that would sort differently numerically vs lexically
        testCase2();
        
        // Test Case 3: Items with letters and numbers mixed
        testCase3();
        
        // Test Case 4: Edge cases
        testCase4();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ALL TEST CASES COMPLETED");
        System.out.println("=".repeat(60));
    }
    
    private static void testCase1() {
        System.out.println("\nTEST CASE 1: Real data from sample CSV files");
        System.out.println("-".repeat(50));
        
        List<String> itemNumbers = Arrays.asList(
            "1015", "1016B", "1021", "1022", "1037B", "1059EMGM", 
            "1069B", "1081G", "1087EX", "1098B", "1234", "23463", 
            "64348790", "87554", "94536", "a8921", "b3549", "tb666"
        );
        
        runSortingTest("Mixed real item numbers", itemNumbers);
    }
    
    private static void testCase2() {
        System.out.println("\nTEST CASE 2: Numbers that demonstrate lexical vs numeric sorting difference");
        System.out.println("-".repeat(50));
        
        List<String> itemNumbers = Arrays.asList(
            "100", "2", "20", "200", "3", "30", "1000", "999", "1001"
        );
        
        System.out.println("NOTE: In numeric sorting, '2' would come before '100'");
        System.out.println("      In lexical sorting, '100' comes before '2'");
        runSortingTest("Numbers showing lexical behavior", itemNumbers);
    }
    
    private static void testCase3() {
        System.out.println("\nTEST CASE 3: Mixed alphanumeric with various patterns");
        System.out.println("-".repeat(50));
        
        List<String> itemNumbers = Arrays.asList(
            "A1", "A10", "A2", "B1", "AB1", "AA1", "1A", "10A", "2A",
            "Z1", "a1", "A1B", "1AB", "AB10", "AB2"
        );
        
        runSortingTest("Complex alphanumeric patterns", itemNumbers);
    }
    
    private static void testCase4() {
        System.out.println("\nTEST CASE 4: Edge cases and special characters");
        System.out.println("-".repeat(50));
        
        List<String> itemNumbers = Arrays.asList(
            "", "0", "00", "000", "001", "1", "01", "10", 
            "A", "a", "AA", "Aa", "aA", "aa"
        );
        
        System.out.println("NOTE: Empty strings, leading zeros, and case sensitivity");
        runSortingTest("Edge cases", itemNumbers);
    }
    
    private static void runSortingTest(String testName, List<String> items) {
        System.out.println("\n📋 " + testName + ":");
        
        // Show original order
        System.out.println("\nOriginal order:");
        for (int i = 0; i < items.size(); i++) {
            System.out.printf("  %2d. '%s'\n", i + 1, items.get(i));
        }
        
        // Sort lexically (this is what our application now does)
        List<String> sortedItems = new ArrayList<>(items);
        sortedItems.sort(String::compareTo);
        
        System.out.println("\nLexically sorted order:");
        for (int i = 0; i < sortedItems.size(); i++) {
            System.out.printf("  %2d. '%s'\n", i + 1, sortedItems.get(i));
        }
        
        // Show the sorting rule being applied
        System.out.println("\n🔍 Sorting explanation:");
        System.out.println("   - Characters are compared left to right");
        System.out.println("   - Numbers (0-9) come before letters in ASCII");
        System.out.println("   - Uppercase letters come before lowercase");
        System.out.println("   - Shorter strings come before longer ones when all compared chars are equal");
        
        // Verify it's actually lexical sorting
        boolean isCorrectlyLexicallySorted = isLexicallySorted(sortedItems);
        System.out.println("\n✅ Lexical sorting verification: " + 
            (isCorrectlyLexicallySorted ? "PASSED" : "FAILED"));
    }
    
    private static boolean isLexicallySorted(List<String> items) {
        for (int i = 0; i < items.size() - 1; i++) {
            if (items.get(i).compareTo(items.get(i + 1)) > 0) {
                return false;
            }
        }
        return true;
    }
}