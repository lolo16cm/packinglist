#!/usr/bin/env python3
"""
Demonstration script for reverse sorting functionality.
Shows how to sort data in both ascending and descending order.
"""

from compare_values import sort_values, sort_values_reverse
from test_sorting import custom_compare, custom_compare_reverse
from functools import cmp_to_key

def demonstrate_reverse_sorting():
    """Demonstrate both ascending and descending sorting with various examples"""
    
    print("🔄 REVERSE SORTING DEMONSTRATION")
    print("=" * 60)
    
    # Example 1: Mixed alphanumeric values
    print("\n📋 Example 1: Mixed alphanumeric values")
    data1 = ["000", "0000", "100", "a00", "a000", "b00", "1002", "500"]
    print(f"Original data: {data1}")
    print(f"Ascending:     {sort_values(data1)}")
    print(f"Descending:    {sort_values_reverse(data1)}")
    
    # Example 2: Pure numbers
    print("\n📋 Example 2: Pure numbers")
    data2 = ["5", "10", "2", "100", "1", "25", "3"]
    print(f"Original data: {data2}")
    print(f"Ascending:     {sort_values(data2)}")
    print(f"Descending:    {sort_values_reverse(data2)}")
    
    # Example 3: Items with alphabetic prefixes
    print("\n📋 Example 3: Items with alphabetic prefixes")
    data3 = ["a1", "a10", "a2", "b1", "b10", "c5", "aa1"]
    print(f"Original data: {data3}")
    print(f"Ascending:     {sort_values(data3)}")
    print(f"Descending:    {sort_values_reverse(data3)}")
    
    # Example 4: Using the alternative sorting method
    print("\n📋 Example 4: Using alternative custom_compare method")
    data4 = ["z99", "a1", "100", "50", "z1", "b20"]
    print(f"Original data: {data4}")
    
    sorted_asc = sorted(data4, key=cmp_to_key(custom_compare))
    sorted_desc = sorted(data4, key=cmp_to_key(custom_compare_reverse))
    
    print(f"Ascending:     {sorted_asc}")
    print(f"Descending:    {sorted_desc}")

def interactive_reverse_demo():
    """Interactive demonstration where user can input values to sort"""
    print("\n🎯 INTERACTIVE REVERSE SORTING")
    print("=" * 60)
    print("Enter values separated by commas (or press Enter for default example):")
    
    user_input = input("Values: ").strip()
    
    if user_input:
        # Parse user input
        try:
            values = [v.strip() for v in user_input.split(",") if v.strip()]
        except:
            print("Invalid input format. Using default example.")
            values = ["10", "2", "a5", "100", "b1", "a10"]
    else:
        values = ["10", "2", "a5", "100", "b1", "a10"]
    
    print(f"\nProcessing: {values}")
    print(f"Ascending:  {sort_values(values)}")
    print(f"Descending: {sort_values_reverse(values)}")

def main():
    """Main demonstration function"""
    demonstrate_reverse_sorting()
    
    print("\n" + "=" * 60)
    print("✅ Reverse sorting functionality is now available!")
    print("\nAvailable functions:")
    print("- sort_values(list) → ascending order")
    print("- sort_values_reverse(list) → descending order")
    print("- custom_compare(a, b) → comparison for ascending")
    print("- custom_compare_reverse(a, b) → comparison for descending")
    
    # Optional interactive demo
    try:
        interactive_reverse_demo()
    except KeyboardInterrupt:
        print("\n\nDemo completed. 👋")

if __name__ == "__main__":
    main()