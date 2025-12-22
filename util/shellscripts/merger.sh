#!/bin/bash

# Simplified properties merger for Grafioschtrader
# Preserves user settings (including commented/uncommented state)
# Adds new properties from updates
#
# Comment marker behavior:
# - Lines with # comments: User-controlled (preserves user's state)
# - Lines with ! comments: Developer-controlled (always uses template)

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -i|--input)
            INPUT_FILE="$2"
            shift 2
            ;;
        -s|--sample)
            SAMPLE_FILE="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        *)
            echo "Error: Unknown argument: $1"
            echo "Usage: $0 -i <input_file> -s <sample_file> -o <output_file>"
            exit 1
            ;;
    esac
done

# Validate arguments
if [[ ! -f "$INPUT_FILE" ]]; then
    echo "Error: Input file does not exist: $INPUT_FILE"
    exit 2
fi

if [[ ! -f "$SAMPLE_FILE" ]]; then
    echo "Error: Sample file does not exist: $SAMPLE_FILE"
    exit 3
fi

if [[ "$INPUT_FILE" == "$SAMPLE_FILE" ]]; then
    echo "Error: Input and Sample files are the same"
    exit 4
fi

if [[ -f "$OUTPUT_FILE" ]]; then
    echo "Error: Output file already exists: $OUTPUT_FILE"
    exit 5
fi

# Check if a line uses ! as comment marker (developer-controlled)
is_developer_controlled() {
    local line="$1"
    # Match lines that start with optional spaces, then !, then property
    if [[ "$line" =~ ^[[:space:]]*![[:space:]]*[^=]+=.* ]]; then
        return 0  # true
    fi
    return 1  # false
}

# Extract property key from a line (works for both # and ! comments)
# Returns just the key name without comment markers, whitespace, or value
extract_key() {
    local line="$1"
    # Match: optional spaces, optional comment (# or !), optional spaces, key, equals
    # Pattern: ^\s*([#!]\s*)?([^=]+)=
    if [[ "$line" =~ ^[[:space:]]*([#!][[:space:]]*)?([^=]+)= ]]; then
        # Trim whitespace from key
        echo "${BASH_REMATCH[2]}" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
    fi
}

# Find a line in the input file that matches the given key
# When duplicates exist, prefers active (uncommented) lines over commented ones
find_user_line() {
    local search_key="$1"
    local found_line=""
    local found_active_line=""

    while IFS= read -r line; do
        local line_key=$(extract_key "$line")
        if [[ -n "$line_key" && "$line_key" == "$search_key" ]]; then
            # Check if this line is active (not commented with # or !)
            if [[ ! "$line" =~ ^[[:space:]]*[#!] ]]; then
                # Active line found - prefer this over commented versions
                found_active_line="$line"
                break
            elif [[ -z "$found_line" ]]; then
                # First commented occurrence - save as fallback
                found_line="$line"
            fi
        fi
    done < "$INPUT_FILE"

    # Prefer active line if found, otherwise use commented version
    if [[ -n "$found_active_line" ]]; then
        echo "$found_active_line"
    else
        echo "$found_line"
    fi
}

# Process the sample file line by line
while IFS= read -r sample_line; do
    # Check if this line is a property (commented or not)
    sample_key=$(extract_key "$sample_line")

    if [[ -n "$sample_key" ]]; then
        # This is a property line

        # Check if developer controls this property (using ! comment)
        if is_developer_controlled "$sample_line"; then
            # Developer-controlled: Always use template version
            echo "$sample_line" >> "$OUTPUT_FILE"
        else
            # User-controlled: Check if user has this property
            user_line=$(find_user_line "$sample_key")

            if [[ -n "$user_line" ]]; then
                # User has this property - use their version (preserves commented/uncommented state)
                echo "$user_line" >> "$OUTPUT_FILE"
            else
                # New property from update - use sample version
                echo "$sample_line" >> "$OUTPUT_FILE"
            fi
        fi
    else
        # Not a property (comment, empty line, etc.) - keep from sample
        echo "$sample_line" >> "$OUTPUT_FILE"
    fi
done < "$SAMPLE_FILE"

echo "Merge completed: $OUTPUT_FILE"
