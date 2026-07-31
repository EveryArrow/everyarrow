-- MySQL Target Database Initialization
-- Creates empty schema matching source (no data)

-- Product table (empty)
CREATE TABLE product (
    ProductID INT AUTO_INCREMENT PRIMARY KEY,
    Name VARCHAR(255) NOT NULL,
    ProductNumber VARCHAR(50) NOT NULL,
    Color VARCHAR(50),
    StandardCost DECIMAL(19,4) DEFAULT 0,
    ListPrice DECIMAL(19,4) DEFAULT 0,
    Size VARCHAR(10),
    Weight DECIMAL(8,2),
    ProductCategoryID INT,
    ProductModelID INT,
    SellStartDate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    SellEndDate DATETIME,
    DiscontinuedDate DATETIME,
    ModifiedDate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Person table (empty)
CREATE TABLE person (
    BusinessEntityID INT AUTO_INCREMENT PRIMARY KEY,
    PersonType CHAR(2) NOT NULL,
    NameStyle BOOLEAN DEFAULT FALSE,
    Title VARCHAR(8),
    FirstName VARCHAR(50) NOT NULL,
    MiddleName VARCHAR(50),
    LastName VARCHAR(50) NOT NULL,
    Suffix VARCHAR(10),
    EmailPromotion INT DEFAULT 0,
    ModifiedDate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Department table (empty)
CREATE TABLE department (
    DepartmentID INT AUTO_INCREMENT PRIMARY KEY,
    Name VARCHAR(50) NOT NULL,
    GroupName VARCHAR(50) NOT NULL,
    ModifiedDate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
