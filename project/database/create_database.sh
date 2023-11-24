#!/bin/bash

# Database file name
DB_FILE="shopping.db"

# Check if the database file exists and delete it if it does
if [ -f "$DB_FILE" ]; then
    echo "Deleting existing database file: $DB_FILE"
    rm "$DB_FILE"
fi

# SQLite commands to create a new database
sqlite3 $DB_FILE <<EOF
PRAGMA foreign_keys = OFF;
SELECT 'DROP TABLE IF EXISTS "' || name || '";' AS sql
FROM sqlite_master
WHERE type IN ('table','index')
AND name NOT LIKE 'sqlite_%'
ORDER BY type DESC, name;

PRAGMA writable_schema = 1;
DELETE FROM sqlite_master WHERE name NOT LIKE 'sqlite_%';
PRAGMA writable_schema = 0;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS shopping_lists (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    list_uuid TEXT NOT NULL,
    list_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS list_products (
    product_id INTEGER PRIMARY KEY AUTOINCREMENT,
    list_id INTEGER NOT NULL,
    product_name TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    FOREIGN KEY (list_id) REFERENCES shopping_lists(id)
);
EOF
