#!/usr/bin/env bash
find . -type f -name '*.sh' -not -path './.git/*' -print0 |
while IFS= read -r -d '' file; do
    sed -i 's/\r$//' "$file"
done