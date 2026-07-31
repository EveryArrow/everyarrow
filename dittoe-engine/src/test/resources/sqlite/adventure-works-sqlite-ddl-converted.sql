-- addresstype definition

CREATE TABLE addresstype (
	"AddressTypeID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "AddressType_target_pkey" PRIMARY KEY ("AddressTypeID")
);


-- awbuildversion definition

CREATE TABLE awbuildversion (
	"SystemInformationID" INTEGER NOT NULL,
	"Database" text NOT NULL,
	"VersionDate" TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "AWBuildVersion_target_pkey" PRIMARY KEY ("SystemInformationID")
);


-- businessentity definition

CREATE TABLE businessentity (
	"BusinessEntityID" INTEGER NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "BusinessEntity_target_pkey" PRIMARY KEY ("BusinessEntityID")
);


-- contacttype definition

CREATE TABLE contacttype (
	"ContactTypeID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ContactType_target_pkey" PRIMARY KEY ("ContactTypeID")
);


-- countryregion definition

CREATE TABLE countryregion (
	"CountryRegionCode" TEXT NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "CountryRegion_target_pkey" PRIMARY KEY ("CountryRegionCode")
);


-- creditcard definition

CREATE TABLE creditcard (
	"CreditCardID" INTEGER NOT NULL,
	"CardType" TEXT NOT NULL,
	"CardNumber" TEXT NOT NULL,
	"ExpMonth" INTEGER NOT NULL,
	"ExpYear" INTEGER NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "CreditCard_target_pkey" PRIMARY KEY ("CreditCardID")
);


-- culture definition

CREATE TABLE culture (
	"CultureID" TEXT(12) NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Culture_target_pkey" PRIMARY KEY ("CultureID")
);


-- currency definition

CREATE TABLE currency (
	"CurrencyCode" TEXT(6) NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Currency_target_pkey" PRIMARY KEY ("CurrencyCode")
);


-- databaselog definition

CREATE TABLE databaselog (
	"DatabaseLogID" INTEGER NOT NULL,
	"PostTime" TEXT NOT NULL,
	"DatabaseUser" TEXT NOT NULL,
	"Event" TEXT NOT NULL,
	"Schema" TEXT NULL,
	"Object" TEXT NULL,
	"TSQL" text NOT NULL,
	"XmlEvent" TEXT NOT NULL,
	CONSTRAINT "DatabaseLog_target_pkey" PRIMARY KEY ("DatabaseLogID")
);


-- department definition

CREATE TABLE department (
	"DepartmentID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"GroupName" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Department_target_pkey" PRIMARY KEY ("DepartmentID")
);


-- errorlog definition

CREATE TABLE errorlog (
	"ErrorLogID" INTEGER NOT NULL,
	"ErrorTime" TEXT NOT NULL,
	"UserName" TEXT NOT NULL,
	"ErrorNumber" INTEGER NOT NULL,
	"ErrorSeverity" INTEGER NULL,
	"ErrorState" INTEGER NULL,
	"ErrorProcedure" TEXT NULL,
	"ErrorLine" INTEGER NULL,
	"ErrorMessage" TEXT NOT NULL,
	CONSTRAINT "ErrorLog_target_pkey" PRIMARY KEY ("ErrorLogID")
);


-- illustration definition

CREATE TABLE illustration (
	"IllustrationID" INTEGER NOT NULL,
	"Diagram" TEXT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Illustration_target_pkey" PRIMARY KEY ("IllustrationID")
);


-- location definition

CREATE TABLE "location" (
	"LocationID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"CostRate" REAL NOT NULL,
	"Availability" REAL NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Location_target_pkey" PRIMARY KEY ("LocationID")
);


-- phonenumbertype definition

CREATE TABLE phonenumbertype (
	"PhoneNumberTypeID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "PhoneNumberType_target_pkey" PRIMARY KEY ("PhoneNumberTypeID")
);


-- productcategory definition

CREATE TABLE productcategory (
	"ProductCategoryID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductCategory_target_pkey" PRIMARY KEY ("ProductCategoryID")
);


-- productdescription definition

CREATE TABLE productdescription (
	"ProductDescriptionID" INTEGER NOT NULL,
	"Description" TEXT NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductDescription_target_pkey" PRIMARY KEY ("ProductDescriptionID")
);


-- productmodel definition

CREATE TABLE productmodel (
	"ProductModelID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"CatalogDescription" TEXT NULL,
	"Instructions" TEXT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductModel_target_pkey" PRIMARY KEY ("ProductModelID")
);


-- productphoto definition

CREATE TABLE productphoto (
	"ProductPhotoID" INTEGER NOT NULL,
	"ThumbNailPhoto" BLOB NULL,
	"ThumbnailPhotoFileName" TEXT NULL,
	"LargePhoto" BLOB NULL,
	"LargePhotoFileName" TEXT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductPhoto_target_pkey" PRIMARY KEY ("ProductPhotoID")
);


-- salesreason definition

CREATE TABLE salesreason (
	"SalesReasonID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"ReasonType" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SalesReason_target_pkey" PRIMARY KEY ("SalesReasonID")
);


-- scrapreason definition

CREATE TABLE scrapreason (
	"ScrapReasonID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ScrapReason_target_pkey" PRIMARY KEY ("ScrapReasonID")
);


-- shift definition

CREATE TABLE shift (
	"ShiftID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"StartTime" TEXT NOT NULL,
	"EndTime" TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Shift_target_pkey" PRIMARY KEY ("ShiftID")
);


-- shipmethod definition

CREATE TABLE shipmethod (
	"ShipMethodID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"ShipBase" REAL NOT NULL,
	"ShipRate" REAL NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ShipMethod_target_pkey" PRIMARY KEY ("ShipMethodID")
);


-- specialoffer definition

CREATE TABLE specialoffer (
	"SpecialOfferID" INTEGER NOT NULL,
	"Description" TEXT NOT NULL,
	"DiscountPct" REAL NOT NULL,
	"Type" TEXT NOT NULL,
	"Category" TEXT NOT NULL,
	"StartDate" TEXT NOT NULL,
	"EndDate" TEXT NOT NULL,
	"MinQty" INTEGER NOT NULL,
	"MaxQty" INTEGER NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SpecialOffer_target_pkey" PRIMARY KEY ("SpecialOfferID")
);


-- specialofferproduct definition

CREATE TABLE specialofferproduct (
	"SpecialOfferID" INTEGER NOT NULL,
	"ProductID" INTEGER NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SpecialOfferProduct_target_pkey" PRIMARY KEY ("SpecialOfferID", "ProductID")
);


-- transactionhistoryarchive definition

CREATE TABLE transactionhistoryarchive (
	"TransactionID" INTEGER NOT NULL,
	"ProductID" INTEGER NOT NULL,
	"ReferenceOrderID" INTEGER NOT NULL,
	"ReferenceOrderLineID" INTEGER NOT NULL,
	"TransactionDate" TEXT NOT NULL,
	"TransactionType" TEXT(2) NOT NULL,
	"Quantity" INTEGER NOT NULL,
	"ActualCost" REAL NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "TransactionHistoryArchive_target_pkey" PRIMARY KEY ("TransactionID")
);


-- unitmeasure definition

CREATE TABLE unitmeasure (
	"UnitMeasureCode" TEXT(6) NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "UnitMeasure_target_pkey" PRIMARY KEY ("UnitMeasureCode")
);


-- countryregioncurrency definition

CREATE TABLE countryregioncurrency (
	"CountryRegionCode" TEXT NOT NULL,
	"CurrencyCode" TEXT(6) NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "CountryRegionCurrency_target_pkey" PRIMARY KEY ("CountryRegionCode", "CurrencyCode"),
	CONSTRAINT "FK_CountryRegionCurrency_CountryRegion_CountryRegionCode" FOREIGN KEY ("CountryRegionCode") REFERENCES countryregion("CountryRegionCode"),
	CONSTRAINT "FK_CountryRegionCurrency_Currency_CurrencyCode" FOREIGN KEY ("CurrencyCode") REFERENCES currency("CurrencyCode")
);


-- currencyrate definition

CREATE TABLE currencyrate (
	"CurrencyRateID" INTEGER NOT NULL,
	"CurrencyRateDate" TEXT NOT NULL,
	"FromCurrencyCode" TEXT(6) NOT NULL,
	"ToCurrencyCode" TEXT(6) NOT NULL,
	"AverageRate" REAL NOT NULL,
	"EndOfDayRate" REAL NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "CurrencyRate_target_pkey" PRIMARY KEY ("CurrencyRateID"),
	CONSTRAINT "FK_CurrencyRate_Currency_FromCurrencyCode" FOREIGN KEY ("FromCurrencyCode") REFERENCES currency("CurrencyCode"),
	CONSTRAINT "FK_CurrencyRate_Currency_ToCurrencyCode" FOREIGN KEY ("ToCurrencyCode") REFERENCES currency("CurrencyCode")
);


-- person definition

CREATE TABLE person (
	"BusinessEntityID" INTEGER NOT NULL,
	"PersonType" TEXT(4) NOT NULL,
	"NameStyle" text NOT NULL,
	"Title" TEXT NULL,
	"FirstName" text NOT NULL,
	"MiddleName" text NULL,
	"LastName" text NOT NULL,
	"Suffix" TEXT NULL,
	"EmailPromotion" INTEGER NOT NULL,
	"AdditionalContactInfo" TEXT NULL,
	"Demographics" TEXT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Person_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Person_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID")
);


-- personcreditcard definition

CREATE TABLE personcreditcard (
	"BusinessEntityID" INTEGER NOT NULL,
	"CreditCardID" INTEGER NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "PersonCreditCard_target_pkey" PRIMARY KEY ("BusinessEntityID", "CreditCardID"),
	CONSTRAINT "FK_PersonCreditCard_CreditCard_CreditCardID" FOREIGN KEY ("CreditCardID") REFERENCES creditcard("CreditCardID"),
	CONSTRAINT "FK_PersonCreditCard_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID")
);


-- personphone definition

CREATE TABLE personphone (
	"BusinessEntityID" INTEGER NOT NULL,
	"PhoneNumber" text NOT NULL,
	"PhoneNumberTypeID" INTEGER NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "PersonPhone_target_pkey" PRIMARY KEY ("BusinessEntityID", "PhoneNumber", "PhoneNumberTypeID"),
	CONSTRAINT "FK_PersonPhone_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID"),
	CONSTRAINT "FK_PersonPhone_PhoneNumberType_PhoneNumberTypeID" FOREIGN KEY ("PhoneNumberTypeID") REFERENCES phonenumbertype("PhoneNumberTypeID")
);


-- productmodelillustration definition

CREATE TABLE productmodelillustration (
	"ProductModelID" INTEGER NOT NULL,
	"IllustrationID" INTEGER NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductModelIllustration_target_pkey" PRIMARY KEY ("ProductModelID", "IllustrationID"),
	CONSTRAINT "FK_ProductModelIllustration_Illustration_IllustrationID" FOREIGN KEY ("IllustrationID") REFERENCES illustration("IllustrationID"),
	CONSTRAINT "FK_ProductModelIllustration_ProductModel_ProductModelID" FOREIGN KEY ("ProductModelID") REFERENCES productmodel("ProductModelID")
);


-- productmodelproductdescriptionculture definition

CREATE TABLE productmodelproductdescriptionculture (
	"ProductModelID" INTEGER NOT NULL,
	"ProductDescriptionID" INTEGER NOT NULL,
	"CultureID" TEXT(12) NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductModelProductDescriptionCulture_target_pkey" PRIMARY KEY ("ProductModelID", "ProductDescriptionID", "CultureID"),
	CONSTRAINT "FK_ProductModelProductDescriptionCulture_Culture_CultureID" FOREIGN KEY ("CultureID") REFERENCES culture("CultureID"),
	CONSTRAINT "FK_ProductModelProductDescriptionCulture_ProductDescription_Pro" FOREIGN KEY ("ProductDescriptionID") REFERENCES productdescription("ProductDescriptionID"),
	CONSTRAINT "FK_ProductModelProductDescriptionCulture_ProductModel_ProductMo" FOREIGN KEY ("ProductModelID") REFERENCES productmodel("ProductModelID")
);


-- productsubcategory definition

CREATE TABLE productsubcategory (
	"ProductSubcategoryID" INTEGER NOT NULL,
	"ProductCategoryID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductSubcategory_target_pkey" PRIMARY KEY ("ProductSubcategoryID"),
	CONSTRAINT "FK_ProductSubcategory_ProductCategory_ProductCategoryID" FOREIGN KEY ("ProductCategoryID") REFERENCES productcategory("ProductCategoryID")
);


-- salesterritory definition

CREATE TABLE salesterritory (
	"TerritoryID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"CountryRegionCode" TEXT NOT NULL,
	"Group" TEXT NOT NULL,
	"SalesYTD" REAL NOT NULL,
	"SalesLastYear" REAL NOT NULL,
	"CostYTD" REAL NOT NULL,
	"CostLastYear" REAL NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SalesTerritory_target_pkey" PRIMARY KEY ("TerritoryID"),
	CONSTRAINT "FK_SalesTerritory_CountryRegion_CountryRegionCode" FOREIGN KEY ("CountryRegionCode") REFERENCES countryregion("CountryRegionCode")
);


-- stateprovince definition

CREATE TABLE stateprovince (
	"StateProvinceID" INTEGER NOT NULL,
	"StateProvinceCode" TEXT(6) NOT NULL,
	"CountryRegionCode" TEXT NOT NULL,
	"IsOnlyStateProvinceFlag" text NOT NULL,
	"Name" text NOT NULL,
	"TerritoryID" INTEGER NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "StateProvince_target_pkey" PRIMARY KEY ("StateProvinceID"),
	CONSTRAINT "FK_StateProvince_CountryRegion_CountryRegionCode" FOREIGN KEY ("CountryRegionCode") REFERENCES countryregion("CountryRegionCode"),
	CONSTRAINT "FK_StateProvince_SalesTerritory_TerritoryID" FOREIGN KEY ("TerritoryID") REFERENCES salesterritory("TerritoryID")
);


-- vendor definition

CREATE TABLE vendor (
	"BusinessEntityID" INTEGER NOT NULL,
	"AccountNumber" text NOT NULL,
	"Name" text NOT NULL,
	"CreditRating" INTEGER NOT NULL,
	"PreferredVendorStatus" text NOT NULL,
	"ActiveFlag" text NOT NULL,
	"PurchasingWebServiceURL" TEXT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Vendor_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Vendor_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID")
);


-- address definition

CREATE TABLE address (
	"AddressID" INTEGER NOT NULL,
	"AddressLine1" TEXT NOT NULL,
	"AddressLine2" TEXT NULL,
	"City" TEXT NOT NULL,
	"StateProvinceID" INTEGER NOT NULL,
	"PostalCode" TEXT NOT NULL,
	"SpatialLocation" text NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Address_target_pkey" PRIMARY KEY ("AddressID"),
	CONSTRAINT "FK_Address_StateProvince_StateProvinceID" FOREIGN KEY ("StateProvinceID") REFERENCES stateprovince("StateProvinceID")
);


-- businessentityaddress definition

CREATE TABLE businessentityaddress (
	"BusinessEntityID" INTEGER NOT NULL,
	"AddressID" INTEGER NOT NULL,
	"AddressTypeID" INTEGER NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "BusinessEntityAddress_target_pkey" PRIMARY KEY ("BusinessEntityID", "AddressID", "AddressTypeID"),
	CONSTRAINT "FK_BusinessEntityAddress_AddressType_AddressTypeID" FOREIGN KEY ("AddressTypeID") REFERENCES addresstype("AddressTypeID"),
	CONSTRAINT "FK_BusinessEntityAddress_Address_AddressID" FOREIGN KEY ("AddressID") REFERENCES address("AddressID"),
	CONSTRAINT "FK_BusinessEntityAddress_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID")
);


-- businessentitycontact definition

CREATE TABLE businessentitycontact (
	"BusinessEntityID" INTEGER NOT NULL,
	"PersonID" INTEGER NOT NULL,
	"ContactTypeID" INTEGER NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "BusinessEntityContact_target_pkey" PRIMARY KEY ("BusinessEntityID", "PersonID", "ContactTypeID"),
	CONSTRAINT "FK_BusinessEntityContact_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID"),
	CONSTRAINT "FK_BusinessEntityContact_ContactType_ContactTypeID" FOREIGN KEY ("ContactTypeID") REFERENCES contacttype("ContactTypeID"),
	CONSTRAINT "FK_BusinessEntityContact_Person_PersonID" FOREIGN KEY ("PersonID") REFERENCES person("BusinessEntityID")
);


-- emailaddress definition

CREATE TABLE emailaddress (
	"BusinessEntityID" INTEGER NOT NULL,
	"EmailAddressID" INTEGER NOT NULL,
	"EmailAddress" TEXT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "EmailAddress_target_pkey" PRIMARY KEY ("BusinessEntityID", "EmailAddressID"),
	CONSTRAINT "FK_EmailAddress_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID")
);


-- employee definition

CREATE TABLE employee (
	"BusinessEntityID" INTEGER NOT NULL,
	"NationalIDNumber" TEXT NOT NULL,
	"LoginID" TEXT NOT NULL,
	"OrganizationNode" TEXT NULL,
	"OrganizationLevel" INTEGER NULL,
	"JobTitle" TEXT NOT NULL,
	"BirthDate" TEXT NOT NULL,
	"MaritalStatus" TEXT(2) NOT NULL,
	"Gender" TEXT(2) NOT NULL,
	"HireDate" TEXT NOT NULL,
	"SalariedFlag" text NOT NULL,
	"VacationHours" INTEGER NOT NULL,
	"SickLeaveHours" INTEGER NOT NULL,
	"CurrentFlag" text NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Employee_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Employee_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID")
);


-- employeedepartmenthistory definition

CREATE TABLE employeedepartmenthistory (
	"BusinessEntityID" INTEGER NOT NULL,
	"DepartmentID" INTEGER NOT NULL,
	"ShiftID" INTEGER NOT NULL,
	"StartDate" TEXT NOT NULL,
	"EndDate" TEXT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "EmployeeDepartmentHistory_target_pkey" PRIMARY KEY ("BusinessEntityID", "DepartmentID", "ShiftID", "StartDate"),
	CONSTRAINT "FK_EmployeeDepartmentHistory_Department_DepartmentID" FOREIGN KEY ("DepartmentID") REFERENCES department("DepartmentID"),
	CONSTRAINT "FK_EmployeeDepartmentHistory_Employee_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES employee("BusinessEntityID"),
	CONSTRAINT "FK_EmployeeDepartmentHistory_Shift_ShiftID" FOREIGN KEY ("ShiftID") REFERENCES shift("ShiftID")
);


-- employeepayhistory definition

CREATE TABLE employeepayhistory (
	"BusinessEntityID" INTEGER NOT NULL,
	"RateChangeDate" TEXT NOT NULL,
	"Rate" REAL NOT NULL,
	"PayFrequency" INTEGER NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "EmployeePayHistory_target_pkey" PRIMARY KEY ("BusinessEntityID", "RateChangeDate"),
	CONSTRAINT "FK_EmployeePayHistory_Employee_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES employee("BusinessEntityID")
);


-- jobcandidate definition

CREATE TABLE jobcandidate (
	"JobCandidateID" INTEGER NOT NULL,
	"BusinessEntityID" INTEGER NULL,
	"Resume" TEXT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "JobCandidate_target_pkey" PRIMARY KEY ("JobCandidateID"),
	CONSTRAINT "FK_JobCandidate_Employee_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES employee("BusinessEntityID")
);


-- password definition

CREATE TABLE "password" (
	"BusinessEntityID" INTEGER NOT NULL,
	"PasswordHash" TEXT NOT NULL,
	"PasswordSalt" TEXT NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Password_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Password_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID")
);


-- product definition

CREATE TABLE product (
	"ProductID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"ProductNumber" TEXT NOT NULL,
	"MakeFlag" text NOT NULL,
	"FinishedGoodsFlag" text NOT NULL,
	"Color" TEXT NULL,
	"SafetyStockLevel" INTEGER NOT NULL,
	"ReorderPoint" INTEGER NOT NULL,
	"StandardCost" REAL NOT NULL,
	"ListPrice" REAL NOT NULL,
	"Size" TEXT NULL,
	"SizeUnitMeasureCode" TEXT(6) NULL,
	"WeightUnitMeasureCode" TEXT(6) NULL,
	"Weight" REAL NULL,
	"DaysToManufacture" INTEGER NOT NULL,
	"ProductLine" TEXT(4) NULL,
	"Class" TEXT(4) NULL,
	"Style" TEXT(4) NULL,
	"ProductSubcategoryID" INTEGER NULL,
	"ProductModelID" INTEGER NULL,
	"SellStartDate" TEXT NOT NULL,
	"SellEndDate" TEXT NULL,
	"DiscontinuedDate" TEXT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Product_target_pkey" PRIMARY KEY ("ProductID"),
	CONSTRAINT "FK_Product_ProductModel_ProductModelID" FOREIGN KEY ("ProductModelID") REFERENCES productmodel("ProductModelID"),
	CONSTRAINT "FK_Product_ProductSubcategory_ProductSubcategoryID" FOREIGN KEY ("ProductSubcategoryID") REFERENCES productsubcategory("ProductSubcategoryID"),
	CONSTRAINT "FK_Product_UnitMeasure_SizeUnitMeasureCode" FOREIGN KEY ("SizeUnitMeasureCode") REFERENCES unitmeasure("UnitMeasureCode"),
	CONSTRAINT "FK_Product_UnitMeasure_WeightUnitMeasureCode" FOREIGN KEY ("WeightUnitMeasureCode") REFERENCES unitmeasure("UnitMeasureCode")
);


-- productcosthistory definition

CREATE TABLE productcosthistory (
	"ProductID" INTEGER NOT NULL,
	"StartDate" TEXT NOT NULL,
	"EndDate" TEXT NULL,
	"StandardCost" REAL NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductCostHistory_target_pkey" PRIMARY KEY ("ProductID", "StartDate"),
	CONSTRAINT "FK_ProductCostHistory_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productinventory definition

CREATE TABLE productinventory (
	"ProductID" INTEGER NOT NULL,
	"LocationID" INTEGER NOT NULL,
	"Shelf" TEXT NOT NULL,
	"Bin" INTEGER NOT NULL,
	"Quantity" INTEGER NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductInventory_target_pkey" PRIMARY KEY ("ProductID", "LocationID"),
	CONSTRAINT "FK_ProductInventory_Location_LocationID" FOREIGN KEY ("LocationID") REFERENCES "location"("LocationID"),
	CONSTRAINT "FK_ProductInventory_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productlistpricehistory definition

CREATE TABLE productlistpricehistory (
	"ProductID" INTEGER NOT NULL,
	"StartDate" TEXT NOT NULL,
	"EndDate" TEXT NULL,
	"ListPrice" REAL NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductListPriceHistory_target_pkey" PRIMARY KEY ("ProductID", "StartDate"),
	CONSTRAINT "FK_ProductListPriceHistory_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productproductphoto definition

CREATE TABLE productproductphoto (
	"ProductID" INTEGER NOT NULL,
	"ProductPhotoID" INTEGER NOT NULL,
	"Primary" text NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductProductPhoto_target_pkey" PRIMARY KEY ("ProductID", "ProductPhotoID"),
	CONSTRAINT "FK_ProductProductPhoto_ProductPhoto_ProductPhotoID" FOREIGN KEY ("ProductPhotoID") REFERENCES productphoto("ProductPhotoID"),
	CONSTRAINT "FK_ProductProductPhoto_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productreview definition

CREATE TABLE productreview (
	"ProductReviewID" INTEGER NOT NULL,
	"ProductID" INTEGER NOT NULL,
	"ReviewerName" text NOT NULL,
	"ReviewDate" TEXT NOT NULL,
	"EmailAddress" TEXT NOT NULL,
	"Rating" INTEGER NOT NULL,
	"Comments" TEXT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductReview_target_pkey" PRIMARY KEY ("ProductReviewID"),
	CONSTRAINT "FK_ProductReview_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productvendor definition

CREATE TABLE productvendor (
	"ProductID" INTEGER NOT NULL,
	"BusinessEntityID" INTEGER NOT NULL,
	"AverageLeadTime" INTEGER NOT NULL,
	"StandardPrice" REAL NOT NULL,
	"LastReceiptCost" REAL NULL,
	"LastReceiptDate" TEXT NULL,
	"MinOrderQty" INTEGER NOT NULL,
	"MaxOrderQty" INTEGER NOT NULL,
	"OnOrderQty" INTEGER NULL,
	"UnitMeasureCode" TEXT(6) NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductVendor_target_pkey" PRIMARY KEY ("ProductID", "BusinessEntityID"),
	CONSTRAINT "FK_ProductVendor_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_ProductVendor_UnitMeasure_UnitMeasureCode" FOREIGN KEY ("UnitMeasureCode") REFERENCES unitmeasure("UnitMeasureCode"),
	CONSTRAINT "FK_ProductVendor_Vendor_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES vendor("BusinessEntityID")
);


-- purchaseorderheader definition

CREATE TABLE purchaseorderheader (
	"PurchaseOrderID" INTEGER NOT NULL,
	"RevisionNumber" INTEGER NOT NULL,
	"Status" INTEGER NOT NULL,
	"EmployeeID" INTEGER NOT NULL,
	"VendorID" INTEGER NOT NULL,
	"ShipMethodID" INTEGER NOT NULL,
	"OrderDate" TEXT NOT NULL,
	"ShipDate" TEXT NULL,
	"SubTotal" REAL NOT NULL,
	"TaxAmt" REAL NOT NULL,
	"Freight" REAL NOT NULL,
	"TotalDue" REAL NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "PurchaseOrderHeader_target_pkey" PRIMARY KEY ("PurchaseOrderID"),
	CONSTRAINT "FK_PurchaseOrderHeader_Employee_EmployeeID" FOREIGN KEY ("EmployeeID") REFERENCES employee("BusinessEntityID"),
	CONSTRAINT "FK_PurchaseOrderHeader_ShipMethod_ShipMethodID" FOREIGN KEY ("ShipMethodID") REFERENCES shipmethod("ShipMethodID"),
	CONSTRAINT "FK_PurchaseOrderHeader_Vendor_VendorID" FOREIGN KEY ("VendorID") REFERENCES vendor("BusinessEntityID")
);


-- salesperson definition

CREATE TABLE salesperson (
	"BusinessEntityID" INTEGER NOT NULL,
	"TerritoryID" INTEGER NULL,
	"SalesQuota" REAL NULL,
	"Bonus" REAL NOT NULL,
	"CommissionPct" REAL NOT NULL,
	"SalesYTD" REAL NOT NULL,
	"SalesLastYear" REAL NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SalesPerson_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_SalesPerson_Employee_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES employee("BusinessEntityID"),
	CONSTRAINT "FK_SalesPerson_SalesTerritory_TerritoryID" FOREIGN KEY ("TerritoryID") REFERENCES salesterritory("TerritoryID")
);


-- salespersonquotahistory definition

CREATE TABLE salespersonquotahistory (
	"BusinessEntityID" INTEGER NOT NULL,
	"QuotaDate" TEXT NOT NULL,
	"SalesQuota" REAL NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SalesPersonQuotaHistory_target_pkey" PRIMARY KEY ("BusinessEntityID", "QuotaDate"),
	CONSTRAINT "FK_SalesPersonQuotaHistory_SalesPerson_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES salesperson("BusinessEntityID")
);


-- salestaxrate definition

CREATE TABLE salestaxrate (
	"SalesTaxRateID" INTEGER NOT NULL,
	"StateProvinceID" INTEGER NOT NULL,
	"TaxType" INTEGER NOT NULL,
	"TaxRate" REAL NOT NULL,
	"Name" text NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SalesTaxRate_target_pkey" PRIMARY KEY ("SalesTaxRateID"),
	CONSTRAINT "FK_SalesTaxRate_StateProvince_StateProvinceID" FOREIGN KEY ("StateProvinceID") REFERENCES stateprovince("StateProvinceID")
);


-- salesterritoryhistory definition

CREATE TABLE salesterritoryhistory (
	"BusinessEntityID" INTEGER NOT NULL,
	"TerritoryID" INTEGER NOT NULL,
	"StartDate" TEXT NOT NULL,
	"EndDate" TEXT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SalesTerritoryHistory_target_pkey" PRIMARY KEY ("BusinessEntityID", "TerritoryID", "StartDate"),
	CONSTRAINT "FK_SalesTerritoryHistory_SalesPerson_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES salesperson("BusinessEntityID"),
	CONSTRAINT "FK_SalesTerritoryHistory_SalesTerritory_TerritoryID" FOREIGN KEY ("TerritoryID") REFERENCES salesterritory("TerritoryID")
);


-- shoppingcartitem definition

CREATE TABLE shoppingcartitem (
	"ShoppingCartItemID" INTEGER NOT NULL,
	"ShoppingCartID" TEXT NOT NULL,
	"Quantity" INTEGER NOT NULL,
	"ProductID" INTEGER NOT NULL,
	"DateCreated" TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ShoppingCartItem_target_pkey" PRIMARY KEY ("ShoppingCartItemID"),
	CONSTRAINT "FK_ShoppingCartItem_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- store definition

CREATE TABLE store (
	"BusinessEntityID" INTEGER NOT NULL,
	"Name" text NOT NULL,
	"SalesPersonID" INTEGER NULL,
	"Demographics" TEXT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Store_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Store_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID"),
	CONSTRAINT "FK_Store_SalesPerson_SalesPersonID" FOREIGN KEY ("SalesPersonID") REFERENCES salesperson("BusinessEntityID")
);


-- transactionhistory definition

CREATE TABLE transactionhistory (
	"TransactionID" INTEGER NOT NULL,
	"ProductID" INTEGER NOT NULL,
	"ReferenceOrderID" INTEGER NOT NULL,
	"ReferenceOrderLineID" INTEGER NOT NULL,
	"TransactionDate" TEXT NOT NULL,
	"TransactionType" TEXT(2) NOT NULL,
	"Quantity" INTEGER NOT NULL,
	"ActualCost" REAL NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "TransactionHistory_target_pkey" PRIMARY KEY ("TransactionID"),
	CONSTRAINT "FK_TransactionHistory_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- workorder definition

CREATE TABLE workorder (
	"WorkOrderID" INTEGER NOT NULL,
	"ProductID" INTEGER NOT NULL,
	"OrderQty" INTEGER NOT NULL,
	"StockedQty" INTEGER NOT NULL,
	"ScrappedQty" INTEGER NOT NULL,
	"StartDate" TEXT NOT NULL,
	"EndDate" TEXT NULL,
	"DueDate" TEXT NOT NULL,
	"ScrapReasonID" INTEGER NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "WorkOrder_target_pkey" PRIMARY KEY ("WorkOrderID"),
	CONSTRAINT "FK_WorkOrder_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_WorkOrder_ScrapReason_ScrapReasonID" FOREIGN KEY ("ScrapReasonID") REFERENCES scrapreason("ScrapReasonID")
);


-- workorderrouting definition

CREATE TABLE workorderrouting (
	"WorkOrderID" INTEGER NOT NULL,
	"ProductID" INTEGER NOT NULL,
	"OperationSequence" INTEGER NOT NULL,
	"LocationID" INTEGER NOT NULL,
	"ScheduledStartDate" TEXT NOT NULL,
	"ScheduledEndDate" TEXT NOT NULL,
	"ActualStartDate" TEXT NULL,
	"ActualEndDate" TEXT NULL,
	"ActualResourceHrs" REAL NULL,
	"PlannedCost" REAL NOT NULL,
	"ActualCost" REAL NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "WorkOrderRouting_target_pkey" PRIMARY KEY ("WorkOrderID", "ProductID", "OperationSequence"),
	CONSTRAINT "FK_WorkOrderRouting_Location_LocationID" FOREIGN KEY ("LocationID") REFERENCES "location"("LocationID"),
	CONSTRAINT "FK_WorkOrderRouting_WorkOrder_WorkOrderID" FOREIGN KEY ("WorkOrderID") REFERENCES workorder("WorkOrderID")
);


-- billofmaterials definition

CREATE TABLE billofmaterials (
	"BillOfMaterialsID" INTEGER NOT NULL,
	"ProductAssemblyID" INTEGER NULL,
	"ComponentID" INTEGER NOT NULL,
	"StartDate" TEXT NOT NULL,
	"EndDate" TEXT NULL,
	"UnitMeasureCode" TEXT(6) NOT NULL,
	"BOMLevel" INTEGER NOT NULL,
	"PerAssemblyQty" REAL NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "BillOfMaterials_target_pkey" PRIMARY KEY ("BillOfMaterialsID"),
	CONSTRAINT "FK_BillOfMaterials_Product_ComponentID" FOREIGN KEY ("ComponentID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_BillOfMaterials_Product_ProductAssemblyID" FOREIGN KEY ("ProductAssemblyID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_BillOfMaterials_UnitMeasure_UnitMeasureCode" FOREIGN KEY ("UnitMeasureCode") REFERENCES unitmeasure("UnitMeasureCode")
);


-- customer definition

CREATE TABLE customer (
	"CustomerID" INTEGER NOT NULL,
	"PersonID" INTEGER NULL,
	"StoreID" INTEGER NULL,
	"TerritoryID" INTEGER NULL,
	"AccountNumber" TEXT NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Customer_target_pkey" PRIMARY KEY ("CustomerID"),
	CONSTRAINT "FK_Customer_Person_PersonID" FOREIGN KEY ("PersonID") REFERENCES person("BusinessEntityID"),
	CONSTRAINT "FK_Customer_SalesTerritory_TerritoryID" FOREIGN KEY ("TerritoryID") REFERENCES salesterritory("TerritoryID"),
	CONSTRAINT "FK_Customer_Store_StoreID" FOREIGN KEY ("StoreID") REFERENCES store("BusinessEntityID")
);


-- document definition

CREATE TABLE "document" (
	"DocumentNode" varchar NOT NULL,
	"DocumentLevel" INTEGER NULL,
	"Title" TEXT NOT NULL,
	"Owner" INTEGER NOT NULL,
	"FolderFlag" INTEGER NOT NULL,
	"FileName" TEXT NOT NULL,
	"FileExtension" TEXT NOT NULL,
	"Revision" TEXT(10) NOT NULL,
	"ChangeNumber" INTEGER NOT NULL,
	"Status" INTEGER NOT NULL,
	"DocumentSummary" text NULL,
	"Document" BLOB NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "Document_target_pkey" PRIMARY KEY ("DocumentNode"),
	CONSTRAINT "FK_Document_Employee_Owner" FOREIGN KEY ("Owner") REFERENCES employee("BusinessEntityID")
);


-- productdocument definition

CREATE TABLE productdocument (
	"ProductID" INTEGER NOT NULL,
	"DocumentNode" varchar NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "ProductDocument_target_pkey" PRIMARY KEY ("ProductID", "DocumentNode"),
	CONSTRAINT "FK_ProductDocument_Document_DocumentNode" FOREIGN KEY ("DocumentNode") REFERENCES "document"("DocumentNode"),
	CONSTRAINT "FK_ProductDocument_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- purchaseorderdetail definition

CREATE TABLE purchaseorderdetail (
	"PurchaseOrderID" INTEGER NOT NULL,
	"PurchaseOrderDetailID" INTEGER NOT NULL,
	"DueDate" TEXT NOT NULL,
	"OrderQty" INTEGER NOT NULL,
	"ProductID" INTEGER NOT NULL,
	"UnitPrice" REAL NOT NULL,
	"LineTotal" REAL NOT NULL,
	"ReceivedQty" REAL NOT NULL,
	"RejectedQty" REAL NOT NULL,
	"StockedQty" REAL NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "PurchaseOrderDetail_target_pkey" PRIMARY KEY ("PurchaseOrderID", "PurchaseOrderDetailID"),
	CONSTRAINT "FK_PurchaseOrderDetail_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_PurchaseOrderDetail_PurchaseOrderHeader_PurchaseOrderID" FOREIGN KEY ("PurchaseOrderID") REFERENCES purchaseorderheader("PurchaseOrderID")
);


-- salesorderheader definition

CREATE TABLE salesorderheader (
	"SalesOrderID" INTEGER NOT NULL,
	"RevisionNumber" INTEGER NOT NULL,
	"OrderDate" TEXT NOT NULL,
	"DueDate" TEXT NOT NULL,
	"ShipDate" TEXT NULL,
	"Status" INTEGER NOT NULL,
	"OnlineOrderFlag" text NOT NULL,
	"SalesOrderNumber" TEXT NOT NULL,
	"PurchaseOrderNumber" text NULL,
	"AccountNumber" text NULL,
	"CustomerID" INTEGER NOT NULL,
	"SalesPersonID" INTEGER NULL,
	"TerritoryID" INTEGER NULL,
	"BillToAddressID" INTEGER NOT NULL,
	"ShipToAddressID" INTEGER NOT NULL,
	"ShipMethodID" INTEGER NOT NULL,
	"CreditCardID" INTEGER NULL,
	"CreditCardApprovalCode" TEXT NULL,
	"CurrencyRateID" INTEGER NULL,
	"SubTotal" REAL NOT NULL,
	"TaxAmt" REAL NOT NULL,
	"Freight" REAL NOT NULL,
	"TotalDue" REAL NOT NULL,
	"Comment" TEXT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SalesOrderHeader_target_pkey" PRIMARY KEY ("SalesOrderID"),
	CONSTRAINT "FK_SalesOrderHeader_Address_BillToAddressID" FOREIGN KEY ("BillToAddressID") REFERENCES address("AddressID"),
	CONSTRAINT "FK_SalesOrderHeader_Address_ShipToAddressID" FOREIGN KEY ("ShipToAddressID") REFERENCES address("AddressID"),
	CONSTRAINT "FK_SalesOrderHeader_CreditCard_CreditCardID" FOREIGN KEY ("CreditCardID") REFERENCES creditcard("CreditCardID"),
	CONSTRAINT "FK_SalesOrderHeader_CurrencyRate_CurrencyRateID" FOREIGN KEY ("CurrencyRateID") REFERENCES currencyrate("CurrencyRateID"),
	CONSTRAINT "FK_SalesOrderHeader_Customer_CustomerID" FOREIGN KEY ("CustomerID") REFERENCES customer("CustomerID"),
	CONSTRAINT "FK_SalesOrderHeader_SalesPerson_SalesPersonID" FOREIGN KEY ("SalesPersonID") REFERENCES salesperson("BusinessEntityID"),
	CONSTRAINT "FK_SalesOrderHeader_SalesTerritory_TerritoryID" FOREIGN KEY ("TerritoryID") REFERENCES salesterritory("TerritoryID"),
	CONSTRAINT "FK_SalesOrderHeader_ShipMethod_ShipMethodID" FOREIGN KEY ("ShipMethodID") REFERENCES shipmethod("ShipMethodID")
);


-- salesorderheadersalesreason definition

CREATE TABLE salesorderheadersalesreason (
	"SalesOrderID" INTEGER NOT NULL,
	"SalesReasonID" INTEGER NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SalesOrderHeaderSalesReason_target_pkey" PRIMARY KEY ("SalesOrderID", "SalesReasonID"),
	CONSTRAINT "FK_SalesOrderHeaderSalesReason_SalesOrderHeader_SalesOrderID" FOREIGN KEY ("SalesOrderID") REFERENCES salesorderheader("SalesOrderID"),
	CONSTRAINT "FK_SalesOrderHeaderSalesReason_SalesReason_SalesReasonID" FOREIGN KEY ("SalesReasonID") REFERENCES salesreason("SalesReasonID")
);


-- salesorderdetail definition

CREATE TABLE salesorderdetail (
	"SalesOrderID" INTEGER NOT NULL,
	"SalesOrderDetailID" INTEGER NOT NULL,
	"CarrierTrackingNumber" TEXT NULL,
	"OrderQty" INTEGER NOT NULL,
	"ProductID" INTEGER NOT NULL,
	"SpecialOfferID" INTEGER NOT NULL,
	"UnitPrice" REAL NOT NULL,
	"UnitPriceDiscount" REAL NOT NULL,
	"LineTotal" text NOT NULL,
	rowguid TEXT NOT NULL,
	"ModifiedDate" TEXT NOT NULL,
	CONSTRAINT "SalesOrderDetail_target_pkey" PRIMARY KEY ("SalesOrderID", "SalesOrderDetailID"),
	CONSTRAINT "FK_SalesOrderDetail_SalesOrderHeader_SalesOrderID" FOREIGN KEY ("SalesOrderID") REFERENCES salesorderheader("SalesOrderID")
);