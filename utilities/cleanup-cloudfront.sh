# Copyright (c) 2026 EveryArrow, Inc.
#
# Licensed under the Apache License, Version 2.0.
# Use is subject to the LICENSE file, including
# production, SaaS, and AI restrictions.

#!/bin/bash
# cleanup-cloudfront.sh
# Automates Disabling and Deleting CloudFront distributions

# List of IDs to process (Matches your list + others)
IDS=(
"E33G1SK9ZYSRCH" # test-infra console
"E3G51NYGUL98TM" # test-infra site
"E3C42G3IX48WOT" # legacy-test console
"E6I44NG2M4MSV"  # legacy-test site
"E3ELEM9E912R02" # new-test console
"E3SOZGZ2DCYSK8" # new-test site
"E2MDN072XDK7GE" # (No name)
"EJ9ZLZTV38P6"   # (Already Disabled?)
"E2PR9PQRACXQH2" # (Already Disabled?)
)

echo "=== CloudFront Cleanup ==="

for ID in "${IDS[@]}"; do
    echo "Processing $ID..."
    
    # Get Config and ETag
    CONFIG_JSON=$(aws cloudfront get-distribution-config --id $ID)
    ETAG=$(echo "$CONFIG_JSON" | grep -o '"ETag": "[^"]*' | cut -d'"' -f4)
    ENABLED=$(echo "$CONFIG_JSON" | grep -o '"Enabled": [a-z]*' | cut -d' ' -f2)
    STATUS=$(aws cloudfront get-distribution --id $ID --query "Distribution.Status" --output text)

    echo "  Status: $STATUS, Enabled: $ENABLED"

    if [ "$ENABLED" == "true" ]; then
        echo "  -> Disabling..."
        # Extract DistributionConfig object (tricky with grep, using a temp file handling is better but let's try sed hack or just use a simplified config)
        # Actually, AWS CLI allows passing the JSON directly.
        
        # Save config to file
        aws cloudfront get-distribution-config --id $ID --query "DistributionConfig" > /tmp/cf_config_$ID.json
        
        # Modify Enabled to false using sed (simple replace)
        sed -i 's/"Enabled": true/"Enabled": false/' /tmp/cf_config_$ID.json
        
        # Update
        aws cloudfront update-distribution --id $ID --if-match $ETAG --distribution-config file:///tmp/cf_config_$ID.json > /dev/null
        echo "  -> Disabled command sent. Wait for 'Deployed' status."
    
    elif [ "$STATUS" == "Deployed" ] && [ "$ENABLED" == "false" ]; then
        echo "  -> Deleting..."
        aws cloudfront delete-distribution --id $ID --if-match $ETAG
        echo "  -> Deleted!"
    
    else
        echo "  -> Waiting for disablement to deploy (Current: $STATUS)..."
    fi
    echo ""
done

echo "Done. Run this script again in ~15 minutes to delete the disabled distributions."
