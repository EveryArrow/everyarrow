-- PostgreSQL Source Database Initialization
-- Creates AdventureWorks-style schema with sample data

-- Create schema
CREATE SCHEMA IF NOT EXISTS production;

-- Product table with sample data
CREATE TABLE production.product (
    ProductID SERIAL PRIMARY KEY,
    Name VARCHAR(255) NOT NULL,
    ProductNumber VARCHAR(50) NOT NULL,
    Color VARCHAR(50),
    StandardCost DECIMAL(19,4) DEFAULT 0,
    ListPrice DECIMAL(19,4) DEFAULT 0,
    Size VARCHAR(10),
    Weight DECIMAL(8,2),
    ProductCategoryID INT,
    ProductModelID INT,
    SellStartDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    SellEndDate TIMESTAMP,
    DiscontinuedDate TIMESTAMP,
    ModifiedDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert 100 sample products
INSERT INTO production.product (Name, ProductNumber, Color, StandardCost, ListPrice, Size, Weight, ProductCategoryID, ProductModelID, SellStartDate)
VALUES
    ('Mountain Bike Frame - Black, 42', 'FR-M21B-42', 'Black', 250.00, 450.00, '42', 2.5, 1, 1, '2023-01-01'),
    ('Mountain Bike Frame - Silver, 44', 'FR-M21S-44', 'Silver', 260.00, 470.00, '44', 2.6, 1, 1, '2023-01-01'),
    ('Road Bike Frame - Red, 52', 'FR-R38R-52', 'Red', 320.00, 580.00, '52', 2.2, 1, 2, '2023-01-01'),
    ('Road Bike Frame - Yellow, 54', 'FR-R38Y-54', 'Yellow', 330.00, 600.00, '54', 2.3, 1, 2, '2023-01-01'),
    ('Touring Frame - Blue, 58', 'FR-T67B-58', 'Blue', 280.00, 500.00, '58', 2.8, 1, 3, '2023-01-01'),
    ('HL Mountain Tire', 'TI-M602', 'Black', 35.00, 65.00, NULL, 1.2, 2, NULL, '2023-01-01'),
    ('ML Mountain Tire', 'TI-M367', 'Black', 28.00, 50.00, NULL, 1.1, 2, NULL, '2023-01-01'),
    ('LL Mountain Tire', 'TI-M236', 'Black', 20.00, 35.00, NULL, 1.0, 2, NULL, '2023-01-01'),
    ('HL Road Tire', 'TI-R628', 'Black', 40.00, 75.00, NULL, 0.9, 2, NULL, '2023-01-01'),
    ('ML Road Tire', 'TI-R432', 'Black', 32.00, 58.00, NULL, 0.85, 2, NULL, '2023-01-01'),
    ('LL Road Tire', 'TI-R288', 'Black', 24.00, 42.00, NULL, 0.8, 2, NULL, '2023-01-01'),
    ('Sport-100 Helmet, Red', 'HL-U509-R', 'Red', 15.00, 35.00, NULL, 0.3, 3, 4, '2023-01-01'),
    ('Sport-100 Helmet, Black', 'HL-U509-B', 'Black', 15.00, 35.00, NULL, 0.3, 3, 4, '2023-01-01'),
    ('AWC Logo Cap', 'CA-1098', 'Multi', 5.00, 12.00, NULL, 0.1, 3, NULL, '2023-01-01'),
    ('Long-Sleeve Logo Jersey, S', 'LJ-0192-S', 'Multi', 22.00, 50.00, 'S', 0.2, 3, 5, '2023-01-01'),
    ('Long-Sleeve Logo Jersey, M', 'LJ-0192-M', 'Multi', 22.00, 50.00, 'M', 0.21, 3, 5, '2023-01-01'),
    ('Long-Sleeve Logo Jersey, L', 'LJ-0192-L', 'Multi', 22.00, 50.00, 'L', 0.22, 3, 5, '2023-01-01'),
    ('Mountain Pump', 'PU-M044', 'Silver', 8.00, 20.00, NULL, 0.5, 4, NULL, '2023-01-01'),
    ('Road Pump', 'PU-R048', 'Silver', 7.00, 18.00, NULL, 0.4, 4, NULL, '2023-01-01'),
    ('Patch Kit/8 Patches', 'PK-0567', 'Multi', 2.00, 5.00, NULL, 0.05, 4, NULL, '2023-01-01'),
    ('Water Bottle - 30 oz.', 'WB-H098', 'Blue', 3.00, 7.00, NULL, 0.08, 4, NULL, '2023-01-01'),
    ('Mountain Bottle Cage', 'BC-M005', 'Black', 4.00, 10.00, NULL, 0.1, 4, NULL, '2023-01-01'),
    ('Road Bottle Cage', 'BC-R205', 'Silver', 3.50, 9.00, NULL, 0.08, 4, NULL, '2023-01-01'),
    ('Fender Set - Mountain', 'FE-6654', 'Black', 12.00, 25.00, NULL, 0.6, 4, NULL, '2023-01-01'),
    ('Bike Wash - Dissolver', 'CL-9009', NULL, 5.00, 12.00, NULL, 0.5, 4, NULL, '2023-01-01'),
    ('Chain', 'CH-0234', 'Silver', 8.00, 18.00, NULL, 0.3, 5, NULL, '2023-01-01'),
    ('LL Crankset', 'CS-4759', 'Black', 45.00, 95.00, NULL, 1.5, 5, NULL, '2023-01-01'),
    ('ML Crankset', 'CS-6583', 'Black', 60.00, 125.00, NULL, 1.6, 5, NULL, '2023-01-01'),
    ('HL Crankset', 'CS-9183', 'Black', 85.00, 175.00, NULL, 1.4, 5, NULL, '2023-01-01'),
    ('Chainring Bolts', 'CB-2903', 'Silver', 2.00, 5.00, NULL, 0.02, 5, NULL, '2023-01-01'),
    ('Chainring Nut', 'CN-6137', 'Silver', 1.50, 4.00, NULL, 0.01, 5, NULL, '2023-01-01'),
    ('Front Derailleur', 'FD-2342', 'Silver', 28.00, 60.00, NULL, 0.4, 5, NULL, '2023-01-01'),
    ('Rear Derailleur', 'RD-2462', 'Silver', 35.00, 75.00, NULL, 0.5, 5, NULL, '2023-01-01'),
    ('LL Bottom Bracket', 'BB-7421', 'Black', 18.00, 40.00, NULL, 0.3, 5, NULL, '2023-01-01'),
    ('ML Bottom Bracket', 'BB-8107', 'Black', 28.00, 60.00, NULL, 0.3, 5, NULL, '2023-01-01'),
    ('HL Bottom Bracket', 'BB-9108', 'Black', 42.00, 88.00, NULL, 0.28, 5, NULL, '2023-01-01'),
    ('LL Headset', 'HS-0296', 'Black', 15.00, 35.00, NULL, 0.2, 5, NULL, '2023-01-01'),
    ('ML Headset', 'HS-2451', 'Black', 22.00, 50.00, NULL, 0.2, 5, NULL, '2023-01-01'),
    ('HL Headset', 'HS-3479', 'Black', 35.00, 75.00, NULL, 0.18, 5, NULL, '2023-01-01'),
    ('LL Hub', 'HB-T928', 'Black', 22.00, 50.00, NULL, 0.5, 5, NULL, '2023-01-01'),
    ('HL Hub', 'HB-M918', 'Black', 45.00, 95.00, NULL, 0.45, 5, NULL, '2023-01-01'),
    ('LL Mountain Pedal', 'PD-M282', 'Black', 18.00, 40.00, NULL, 0.4, 5, NULL, '2023-01-01'),
    ('ML Mountain Pedal', 'PD-M340', 'Black', 28.00, 60.00, NULL, 0.38, 5, NULL, '2023-01-01'),
    ('HL Mountain Pedal', 'PD-M563', 'Black', 42.00, 88.00, NULL, 0.35, 5, NULL, '2023-01-01'),
    ('LL Road Pedal', 'PD-R347', 'Black', 20.00, 45.00, NULL, 0.35, 5, NULL, '2023-01-01'),
    ('ML Road Pedal', 'PD-R563', 'Black', 32.00, 68.00, NULL, 0.32, 5, NULL, '2023-01-01'),
    ('HL Road Pedal', 'PD-R853', 'Black', 48.00, 100.00, NULL, 0.3, 5, NULL, '2023-01-01'),
    ('LL Mountain Seat/Saddle', 'SE-M236', 'Black', 14.00, 32.00, NULL, 0.25, 5, NULL, '2023-01-01'),
    ('ML Mountain Seat/Saddle', 'SE-M798', 'Black', 22.00, 50.00, NULL, 0.24, 5, NULL, '2023-01-01'),
    ('HL Mountain Seat/Saddle', 'SE-M940', 'Black', 35.00, 75.00, NULL, 0.22, 5, NULL, '2023-01-01');

-- Generate 50 more synthetic products
INSERT INTO production.product (Name, ProductNumber, Color, StandardCost, ListPrice, Size, Weight, ProductCategoryID, SellStartDate)
SELECT 
    'Product ' || gs || ' - ' || (ARRAY['Red','Blue','Green','Black','White','Silver'])[1 + (gs % 6)],
    'PRD-' || LPAD(gs::text, 4, '0'),
    (ARRAY['Red','Blue','Green','Black','White','Silver'])[1 + (gs % 6)],
    (10 + gs * 2)::DECIMAL(19,4),
    (25 + gs * 3.5)::DECIMAL(19,4),
    (ARRAY['S','M','L','XL'])[1 + (gs % 4)],
    (0.5 + (gs * 0.1))::DECIMAL(8,2),
    1 + (gs % 5),
    '2023-01-01'::TIMESTAMP
FROM generate_series(51, 100) AS gs;

-- Person table
CREATE TABLE production.person (
    BusinessEntityID SERIAL PRIMARY KEY,
    PersonType CHAR(2) NOT NULL,
    NameStyle BOOLEAN DEFAULT FALSE,
    Title VARCHAR(8),
    FirstName VARCHAR(50) NOT NULL,
    MiddleName VARCHAR(50),
    LastName VARCHAR(50) NOT NULL,
    Suffix VARCHAR(10),
    EmailPromotion INT DEFAULT 0,
    ModifiedDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample persons
INSERT INTO production.person (PersonType, FirstName, MiddleName, LastName, Title, EmailPromotion)
VALUES
    ('EM', 'Ken', 'J', 'Sánchez', 'Mr.', 1),
    ('EM', 'Terri', 'Lee', 'Duffy', 'Ms.', 2),
    ('EM', 'Roberto', NULL, 'Tamburello', 'Mr.', 0),
    ('EM', 'Rob', NULL, 'Walters', 'Mr.', 1),
    ('EM', 'Gail', 'A', 'Erickson', 'Ms.', 0),
    ('EM', 'Jossef', 'H', 'Goldberg', 'Mr.', 2),
    ('EM', 'Dylan', 'A', 'Miller', 'Mr.', 1),
    ('EM', 'Diane', 'L', 'Margheim', 'Ms.', 0),
    ('EM', 'Gigi', 'N', 'Matthew', 'Ms.', 1),
    ('EM', 'Michael', NULL, 'Raheem', 'Mr.', 2),
    ('SC', 'Ovidiu', 'V', 'Cracium', 'Mr.', 0),
    ('SC', 'Thierry', NULL, 'D''Hers', 'Mr.', 1),
    ('SC', 'Janice', 'M', 'Galvin', 'Ms.', 2),
    ('SC', 'Michael', 'I', 'Sullivan', 'Mr.', 0),
    ('SC', 'Sharon', 'B', 'Salavaria', 'Ms.', 1),
    ('IN', 'David', NULL, 'Bradley', 'Mr.', 2),
    ('IN', 'Kevin', 'F', 'Brown', 'Mr.', 0),
    ('IN', 'John', 'L', 'Wood', 'Mr.', 1),
    ('IN', 'Mary', 'A', 'Dempsey', 'Ms.', 2),
    ('IN', 'Wanida', 'M', 'Benshoof', 'Ms.', 0);

-- Department table
CREATE TABLE production.department (
    DepartmentID SERIAL PRIMARY KEY,
    Name VARCHAR(50) NOT NULL,
    GroupName VARCHAR(50) NOT NULL,
    ModifiedDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample departments
INSERT INTO production.department (Name, GroupName)
VALUES
    ('Engineering', 'Research and Development'),
    ('Tool Design', 'Research and Development'),
    ('Sales', 'Sales and Marketing'),
    ('Marketing', 'Sales and Marketing'),
    ('Purchasing', 'Inventory Management'),
    ('Research and Development', 'Research and Development'),
    ('Production', 'Manufacturing'),
    ('Production Control', 'Manufacturing'),
    ('Human Resources', 'Executive General and Administration'),
    ('Finance', 'Executive General and Administration'),
    ('Information Services', 'Executive General and Administration'),
    ('Document Control', 'Quality Assurance'),
    ('Quality Assurance', 'Quality Assurance'),
    ('Facilities and Maintenance', 'Executive General and Administration'),
    ('Shipping and Receiving', 'Inventory Management'),
    ('Executive', 'Executive General and Administration');

ANALYZE;
