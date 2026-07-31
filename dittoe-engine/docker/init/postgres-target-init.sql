-- PostgreSQL Target Database Initialization
-- Creates empty schema matching source (no data)

-- Create schema
CREATE SCHEMA IF NOT EXISTS production;

-- Product table (empty)
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

-- Person table (empty)
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

-- Department table (empty)
CREATE TABLE production.department (
    DepartmentID SERIAL PRIMARY KEY,
    Name VARCHAR(50) NOT NULL,
    GroupName VARCHAR(50) NOT NULL,
    ModifiedDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
