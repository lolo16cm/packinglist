#!/usr/bin/env python3
"""
Custom comparison function for sorting values in ascending order.
Rules:
1. Compare numerical values numerically (not lexicographically)
2. Length doesn't matter for comparison
3. Pure numbers come before values with alphabetic characters
4. Examples: 000, 0000, 100, a00, a000, b00
"""

import re
from functools import cmp_to_key


def extract_parts(value):
    """
    Extract numeric and alphabetic parts from a value.
    Returns (is_pure_number, numeric_part, alphabetic_part)
    """
    value_str = str(value)
    
    # Check if it's a pure number (only digits)
    if value_str.isdigit():
        return (True, int(value_str), "")
    
    # Extract alphabetic prefix and numeric suffix
    match = re.match(r'^([a-zA-Z]*)(\d*)$', value_str)
    if match:
        alpha_part = match.group(1)
        num_part = match.group(2)
        numeric_value = int(num_part) if num_part else 0
        return (False, numeric_value, alpha_part)
    
    # If no clear pattern, treat as alphabetic
    return (False, 0, value_str)


def compare_values(a, b):
    """
    Custom comparison function for sorting values.
    Returns: -1 if a < b, 0 if a == b, 1 if a > b
    """
    is_pure_a, num_a, alpha_a = extract_parts(a)
    is_pure_b, num_b, alpha_b = extract_parts(b)
    
    # Pure numbers always come before values with alphabetic characters
    if is_pure_a and not is_pure_b:
        return -1
    if not is_pure_a and is_pure_b:
        return 1
    
    # Both are pure numbers - compare numerically
    if is_pure_a and is_pure_b:
        if num_a < num_b:
            return -1
        elif num_a > num_b:
            return 1
        else:
            return 0
    
    # Both have alphabetic parts
    # First compare alphabetic parts
    if alpha_a < alpha_b:
        return -1
    elif alpha_a > alpha_b:
        return 1
    else:
        # Same alphabetic part, compare numeric parts
        if num_a < num_b:
            return -1
        elif num_a > num_b:
            return 1
        else:
            return 0


def compare_values_reverse(a, b):
    """
    Custom comparison function for sorting values in DESCENDING order.
    Returns: 1 if a < b, 0 if a == b, -1 if a > b (opposite of compare_values)
    """
    return -compare_values(a, b)


def sort_values(values):
    """
    Sort a list of values using the custom comparison function.
    """
    return sorted(values, key=cmp_to_key(compare_values))


def sort_values_reverse(values):
    """
    Sort a list of values in DESCENDING order using the custom comparison function.
    """
    return sorted(values, key=cmp_to_key(compare_values_reverse))


def main():
    # Test with the provided examples
    test_values = ["000", "0000", "100", "a00", "a000", "b00"]
    print("Original values:", test_values)
    
    sorted_values = sort_values(test_values)
    print("Sorted values (ascending):", sorted_values)
    
    sorted_values_desc = sort_values_reverse(test_values)
    print("Sorted values (descending):", sorted_values_desc)
    
    # Additional test cases
    print("\nAdditional test cases:")
    
    test_cases = [
        ["5", "10", "2", "100"],
        ["a1", "a10", "a2", "b1"],
        ["1", "a1", "10", "b2", "2"],
        ["z99", "a1", "100", "50", "z1"],
        ["000", "0", "00", "100", "010"]
    ]
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"Test {i}: {test_case}")
        print(f"Sorted (ascending): {sort_values(test_case)}")
        print(f"Sorted (descending): {sort_values_reverse(test_case)}")
        print()


if __name__ == "__main__":
    main()