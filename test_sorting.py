#!/usr/bin/env python3
"""
Test script for custom sorting logic:
- Compare values from first character to end
- If first character is smaller, it goes first
- Length doesn't matter
- Numbers (0-9) come first in ascending order
- Then alphabetical order
"""

import csv
from functools import cmp_to_key

def custom_compare(a, b):
    """
    Custom comparison function for sorting
    Rules:
    1. Numbers (0-9) come before letters
    2. Within numbers: ascending order
    3. Within letters: alphabetical order
    4. Compare character by character from left to right
    5. Length doesn't matter - shorter strings can come after longer ones
    """
    str_a, str_b = str(a), str(b)
    
    # Compare character by character
    min_len = min(len(str_a), len(str_b))
    
    for i in range(min_len):
        char_a, char_b = str_a[i], str_b[i]
        
        # Both are digits
        if char_a.isdigit() and char_b.isdigit():
            if char_a != char_b:
                return int(char_a) - int(char_b)
        # Both are letters
        elif char_a.isalpha() and char_b.isalpha():
            if char_a.lower() != char_b.lower():
                return -1 if char_a.lower() < char_b.lower() else 1
        # One is digit, one is letter - digit comes first
        elif char_a.isdigit() and char_b.isalpha():
            return -1
        elif char_a.isalpha() and char_b.isdigit():
            return 1
        # Other characters
        else:
            if char_a != char_b:
                return -1 if char_a < char_b else 1
    
    # If all compared characters are equal, shorter string comes first
    return len(str_a) - len(str_b)

def custom_compare_reverse(a, b):
    """
    Custom comparison function for DESCENDING sorting
    Returns the opposite of custom_compare to reverse the order
    """
    return -custom_compare(a, b)

def test_expected_result():
    """Test with the expected result provided by user"""
    # Expected result from user
    expected = ["000", "0000", "0001", "100", "1002", "500", "6002", "a00", "a0000", "a01", "aa01", "ab0", "b001"]
    
    # Create a shuffled version to test sorting
    test_data = expected.copy()
    import random
    random.shuffle(test_data)
    
    print("Original (shuffled) data:")
    print(test_data)
    
    # Sort using our custom function
    sorted_data = sorted(test_data, key=cmp_to_key(custom_compare))
    
    print("\nSorted result (ascending):")
    print(sorted_data)
    
    # Sort in reverse order
    sorted_data_desc = sorted(test_data, key=cmp_to_key(custom_compare_reverse))
    
    print("\nSorted result (descending):")
    print(sorted_data_desc)
    
    print("\nExpected result:")
    print(expected)
    
    print(f"\nDoes our ascending result match expected? {sorted_data == expected}")
    
    return sorted_data == expected

def test_csv_data():
    """Test with the CSV data provided"""
    try:
        # Read the CSV file
        with open('sample_data.csv', 'r') as file:
            reader = csv.DictReader(file)
            rows = list(reader)
        
        # Extract unique values from different columns to test
        print("\nTesting CSV data...")
        
        # Test ITEM NO. column
        item_nos = list(set(row['ITEM NO.'] for row in rows if row['ITEM NO.']))
        if item_nos:
            print(f"\nOriginal ITEM NO. values: {item_nos}")
            
            sorted_items = sorted(item_nos, key=cmp_to_key(custom_compare))
            print(f"Sorted ITEM NO. values (ascending): {sorted_items}")
            
            sorted_items_desc = sorted(item_nos, key=cmp_to_key(custom_compare_reverse))
            print(f"Sorted ITEM NO. values (descending): {sorted_items_desc}")
        
        # Test PO/NO. column
        po_nos = list(set(row['PO/NO.'] for row in rows if row['PO/NO.']))
        if po_nos:
            print(f"\nOriginal PO/NO. values: {po_nos}")
            
            sorted_pos = sorted(po_nos, key=cmp_to_key(custom_compare))
            print(f"Sorted PO/NO. values (ascending): {sorted_pos}")
            
            sorted_pos_desc = sorted(po_nos, key=cmp_to_key(custom_compare_reverse))
            print(f"Sorted PO/NO. values (descending): {sorted_pos_desc}")
            
    except FileNotFoundError:
        print("CSV file not found. Skipping CSV test.")
    except Exception as e:
        print(f"Error reading CSV: {e}")

def main():
    print("Testing custom sorting algorithm...")
    print("=" * 50)
    
    # Test with expected result
    success = test_expected_result()
    
    # Test with CSV data
    test_csv_data()
    
    print("\n" + "=" * 50)
    if success:
        print("✅ Test PASSED! The sorting algorithm produces the expected result.")
    else:
        print("❌ Test FAILED! The sorting algorithm needs adjustment.")

if __name__ == "__main__":
    main()