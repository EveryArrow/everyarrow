#!/usr/bin/env python3
"""
Update SQLite PMD files to use relative paths to target.db
"""

import json
from pathlib import Path

def update_pmd_path(pmd_file):
    """Update the path in PMD file to use relative path."""
    with open(pmd_file, 'r') as f:
        pmd_data = json.load(f)
    
    # Update target server path to relative path
    if "targetResourceToProcess" in pmd_data:
        target = pmd_data["targetResourceToProcess"]
        if "server" in target and "connectionDetails" in target["server"]:
            target["server"]["connectionDetails"]["path"] = "src/test/resources/sqlite/target.db"
    
    # Write back
    with open(pmd_file, 'w') as f:
        json.dump(pmd_data, f, indent=2)
    
    return True

def main():
    pipelines_dir = Path("src/test/resources/pipelines")
    
    updated_count = 0
    error_count = 0
    
    for pipeline_dir in pipelines_dir.iterdir():
        if not pipeline_dir.is_dir():
            continue
        
        pmd_file = pipeline_dir / "local-file-csv-to-sqlite-insert-pmd.json"
        
        if not pmd_file.exists():
            continue
        
        try:
            update_pmd_path(pmd_file)
            print(f"✓ Updated: {pipeline_dir.name}")
            updated_count += 1
        except Exception as e:
            print(f"✗ Error updating {pipeline_dir.name}: {e}")
            error_count += 1
    
    print(f"\n{'='*60}")
    print(f"Summary:")
    print(f"  Files updated: {updated_count}")
    print(f"  Errors: {error_count}")
    print(f"{'='*60}")

if __name__ == "__main__":
    main()
