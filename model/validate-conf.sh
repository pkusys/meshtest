#!/bin/bash

SEARCH_DIR="testconf"

FAILED_FILES=()

for file in "$SEARCH_DIR"/*.yaml; do
    result=$(istioctl validate -f "$file" 2>&1)
    echo "$file"
    if [[ $result == Error* ]]; then
        FAILED_FILES+=("$file  $result")
        echo "Failed：$file"
    else
        output=$(kubectl apply -f "$file" 2>&1)
        success=true
        msg=""

        while IFS= read -r line; do
            if [[ (! $line =~ created$) && (! $line =~ configured$) && (! $line =~ ^Warning) ]]; then
                success=false
                msg=$line
                break
            fi
        done <<< "$output"

        output=$(kubectl delete -f "$file" --force 2>&1)
        if [ "$success" = false ]; then
          echo "Failed：$file $msg"
          exit 1
        fi
    fi
done

if [ ${#FAILED_FILES[@]} -ne 0 ]; then
    exit 1
else
    exit 0
fi
