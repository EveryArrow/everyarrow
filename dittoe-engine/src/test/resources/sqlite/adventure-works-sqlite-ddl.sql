-- addresstype definition

CREATE TABLE addresstype (
	"AddressTypeID" int4 NOT NULL,
	"Name" text NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "AddressType_target_pkey" PRIMARY KEY ("AddressTypeID")
);


-- awbuildversion definition

CREATE TABLE awbuildversion (
	"SystemInformationID" int2 NOT NULL,
	"Database" text NOT NULL,
	"VersionDate" timestamp NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "AWBuildVersion_target_pkey" PRIMARY KEY ("SystemInformationID")
);


-- businessentity definition

CREATE TABLE businessentity (
	"BusinessEntityID" int4 NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "BusinessEntity_target_pkey" PRIMARY KEY ("BusinessEntityID")
);


-- contacttype definition

CREATE TABLE contacttype (
	"ContactTypeID" int4 NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ContactType_target_pkey" PRIMARY KEY ("ContactTypeID")
);


-- countryregion definition

CREATE TABLE countryregion (
	"CountryRegionCode" varchar(6) NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "CountryRegion_target_pkey" PRIMARY KEY ("CountryRegionCode")
);


-- creditcard definition

CREATE TABLE creditcard (
	"CreditCardID" int4 NOT NULL,
	"CardType" varchar(100) NOT NULL,
	"CardNumber" varchar(50) NOT NULL,
	"ExpMonth" int2 NOT NULL,
	"ExpYear" int2 NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "CreditCard_target_pkey" PRIMARY KEY ("CreditCardID")
);


-- culture definition

CREATE TABLE culture (
	"CultureID" bpchar(12) NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Culture_target_pkey" PRIMARY KEY ("CultureID")
);


-- currency definition

CREATE TABLE currency (
	"CurrencyCode" bpchar(6) NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Currency_target_pkey" PRIMARY KEY ("CurrencyCode")
);


-- databaselog definition

CREATE TABLE databaselog (
	"DatabaseLogID" int4 NOT NULL,
	"PostTime" timestamp NOT NULL,
	"DatabaseUser" varchar(256) NOT NULL,
	"Event" varchar(256) NOT NULL,
	"Schema" varchar(256) NULL,
	"Object" varchar(256) NULL,
	"TSQL" text NOT NULL,
	"XmlEvent" xml NOT NULL,
	CONSTRAINT "DatabaseLog_target_pkey" PRIMARY KEY ("DatabaseLogID")
);


-- department definition

CREATE TABLE department (
	"DepartmentID" int2 NOT NULL,
	"Name" text NOT NULL,
	"GroupName" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Department_target_pkey" PRIMARY KEY ("DepartmentID")
);


-- errorlog definition

CREATE TABLE errorlog (
	"ErrorLogID" int4 NOT NULL,
	"ErrorTime" timestamp NOT NULL,
	"UserName" varchar(256) NOT NULL,
	"ErrorNumber" int4 NOT NULL,
	"ErrorSeverity" int4 NULL,
	"ErrorState" int4 NULL,
	"ErrorProcedure" varchar(252) NULL,
	"ErrorLine" int4 NULL,
	"ErrorMessage" varchar(8000) NOT NULL,
	CONSTRAINT "ErrorLog_target_pkey" PRIMARY KEY ("ErrorLogID")
);


-- illustration definition

CREATE TABLE illustration (
	"IllustrationID" int4 NOT NULL,
	"Diagram" xml NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Illustration_target_pkey" PRIMARY KEY ("IllustrationID")
);


-- location definition

CREATE TABLE "location" (
	"LocationID" int2 NOT NULL,
	"Name" text NOT NULL,
	"CostRate" money NOT NULL,
	"Availability" numeric NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Location_target_pkey" PRIMARY KEY ("LocationID")
);


-- phonenumbertype definition

CREATE TABLE phonenumbertype (
	"PhoneNumberTypeID" int4 NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "PhoneNumberType_target_pkey" PRIMARY KEY ("PhoneNumberTypeID")
);


-- productcategory definition

CREATE TABLE productcategory (
	"ProductCategoryID" int4 NOT NULL,
	"Name" text NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductCategory_target_pkey" PRIMARY KEY ("ProductCategoryID")
);


-- productdescription definition

CREATE TABLE productdescription (
	"ProductDescriptionID" int4 NOT NULL,
	"Description" varchar(800) NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductDescription_target_pkey" PRIMARY KEY ("ProductDescriptionID")
);


-- productmodel definition

CREATE TABLE productmodel (
	"ProductModelID" int4 NOT NULL,
	"Name" text NOT NULL,
	"CatalogDescription" xml NULL,
	"Instructions" xml NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductModel_target_pkey" PRIMARY KEY ("ProductModelID")
);


-- productphoto definition

CREATE TABLE productphoto (
	"ProductPhotoID" int4 NOT NULL,
	"ThumbNailPhoto" bytea NULL,
	"ThumbnailPhotoFileName" varchar(100) NULL,
	"LargePhoto" bytea NULL,
	"LargePhotoFileName" varchar(100) NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductPhoto_target_pkey" PRIMARY KEY ("ProductPhotoID")
);


-- salesreason definition

CREATE TABLE salesreason (
	"SalesReasonID" int4 NOT NULL,
	"Name" text NOT NULL,
	"ReasonType" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SalesReason_target_pkey" PRIMARY KEY ("SalesReasonID")
);


-- scrapreason definition

CREATE TABLE scrapreason (
	"ScrapReasonID" int2 NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ScrapReason_target_pkey" PRIMARY KEY ("ScrapReasonID")
);


-- shift definition

CREATE TABLE shift (
	"ShiftID" int2 NOT NULL,
	"Name" text NOT NULL,
	"StartTime" time NOT NULL,
	"EndTime" time NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Shift_target_pkey" PRIMARY KEY ("ShiftID")
);


-- shipmethod definition

CREATE TABLE shipmethod (
	"ShipMethodID" int4 NOT NULL,
	"Name" text NOT NULL,
	"ShipBase" money NOT NULL,
	"ShipRate" money NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ShipMethod_target_pkey" PRIMARY KEY ("ShipMethodID")
);


-- specialoffer definition

CREATE TABLE specialoffer (
	"SpecialOfferID" int4 NOT NULL,
	"Description" varchar(510) NOT NULL,
	"DiscountPct" money NOT NULL,
	"Type" varchar(100) NOT NULL,
	"Category" varchar(100) NOT NULL,
	"StartDate" timestamp NOT NULL,
	"EndDate" timestamp NOT NULL,
	"MinQty" int4 NOT NULL,
	"MaxQty" int4 NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SpecialOffer_target_pkey" PRIMARY KEY ("SpecialOfferID")
);


-- specialofferproduct definition

CREATE TABLE specialofferproduct (
	"SpecialOfferID" int4 NOT NULL,
	"ProductID" int4 NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SpecialOfferProduct_target_pkey" PRIMARY KEY ("SpecialOfferID", "ProductID")
);


-- transactionhistoryarchive definition

CREATE TABLE transactionhistoryarchive (
	"TransactionID" int4 NOT NULL,
	"ProductID" int4 NOT NULL,
	"ReferenceOrderID" int4 NOT NULL,
	"ReferenceOrderLineID" int4 NOT NULL,
	"TransactionDate" timestamp NOT NULL,
	"TransactionType" bpchar(2) NOT NULL,
	"Quantity" int4 NOT NULL,
	"ActualCost" money NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "TransactionHistoryArchive_target_pkey" PRIMARY KEY ("TransactionID")
);


-- unitmeasure definition

CREATE TABLE unitmeasure (
	"UnitMeasureCode" bpchar(6) NOT NULL,
	"Name" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "UnitMeasure_target_pkey" PRIMARY KEY ("UnitMeasureCode")
);


-- countryregioncurrency definition

CREATE TABLE countryregioncurrency (
	"CountryRegionCode" varchar(6) NOT NULL,
	"CurrencyCode" bpchar(6) NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "CountryRegionCurrency_target_pkey" PRIMARY KEY ("CountryRegionCode", "CurrencyCode"),
	CONSTRAINT "FK_CountryRegionCurrency_CountryRegion_CountryRegionCode" FOREIGN KEY ("CountryRegionCode") REFERENCES countryregion("CountryRegionCode"),
	CONSTRAINT "FK_CountryRegionCurrency_Currency_CurrencyCode" FOREIGN KEY ("CurrencyCode") REFERENCES currency("CurrencyCode")
);


-- currencyrate definition

CREATE TABLE currencyrate (
	"CurrencyRateID" int4 NOT NULL,
	"CurrencyRateDate" timestamp NOT NULL,
	"FromCurrencyCode" bpchar(6) NOT NULL,
	"ToCurrencyCode" bpchar(6) NOT NULL,
	"AverageRate" money NOT NULL,
	"EndOfDayRate" money NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "CurrencyRate_target_pkey" PRIMARY KEY ("CurrencyRateID"),
	CONSTRAINT "FK_CurrencyRate_Currency_FromCurrencyCode" FOREIGN KEY ("FromCurrencyCode") REFERENCES currency("CurrencyCode"),
	CONSTRAINT "FK_CurrencyRate_Currency_ToCurrencyCode" FOREIGN KEY ("ToCurrencyCode") REFERENCES currency("CurrencyCode")
);


-- person definition

CREATE TABLE person (
	"BusinessEntityID" int4 NOT NULL,
	"PersonType" bpchar(4) NOT NULL,
	"NameStyle" text NOT NULL,
	"Title" varchar(16) NULL,
	"FirstName" text NOT NULL,
	"MiddleName" text NULL,
	"LastName" text NOT NULL,
	"Suffix" varchar(20) NULL,
	"EmailPromotion" int4 NOT NULL,
	"AdditionalContactInfo" xml NULL,
	"Demographics" xml NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Person_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Person_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID")
);


-- personcreditcard definition

CREATE TABLE personcreditcard (
	"BusinessEntityID" int4 NOT NULL,
	"CreditCardID" int4 NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "PersonCreditCard_target_pkey" PRIMARY KEY ("BusinessEntityID", "CreditCardID"),
	CONSTRAINT "FK_PersonCreditCard_CreditCard_CreditCardID" FOREIGN KEY ("CreditCardID") REFERENCES creditcard("CreditCardID"),
	CONSTRAINT "FK_PersonCreditCard_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID")
);


-- personphone definition

CREATE TABLE personphone (
	"BusinessEntityID" int4 NOT NULL,
	"PhoneNumber" text NOT NULL,
	"PhoneNumberTypeID" int4 NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "PersonPhone_target_pkey" PRIMARY KEY ("BusinessEntityID", "PhoneNumber", "PhoneNumberTypeID"),
	CONSTRAINT "FK_PersonPhone_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID"),
	CONSTRAINT "FK_PersonPhone_PhoneNumberType_PhoneNumberTypeID" FOREIGN KEY ("PhoneNumberTypeID") REFERENCES phonenumbertype("PhoneNumberTypeID")
);


-- productmodelillustration definition

CREATE TABLE productmodelillustration (
	"ProductModelID" int4 NOT NULL,
	"IllustrationID" int4 NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductModelIllustration_target_pkey" PRIMARY KEY ("ProductModelID", "IllustrationID"),
	CONSTRAINT "FK_ProductModelIllustration_Illustration_IllustrationID" FOREIGN KEY ("IllustrationID") REFERENCES illustration("IllustrationID"),
	CONSTRAINT "FK_ProductModelIllustration_ProductModel_ProductModelID" FOREIGN KEY ("ProductModelID") REFERENCES productmodel("ProductModelID")
);


-- productmodelproductdescriptionculture definition

CREATE TABLE productmodelproductdescriptionculture (
	"ProductModelID" int4 NOT NULL,
	"ProductDescriptionID" int4 NOT NULL,
	"CultureID" bpchar(12) NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductModelProductDescriptionCulture_target_pkey" PRIMARY KEY ("ProductModelID", "ProductDescriptionID", "CultureID"),
	CONSTRAINT "FK_ProductModelProductDescriptionCulture_Culture_CultureID" FOREIGN KEY ("CultureID") REFERENCES culture("CultureID"),
	CONSTRAINT "FK_ProductModelProductDescriptionCulture_ProductDescription_Pro" FOREIGN KEY ("ProductDescriptionID") REFERENCES productdescription("ProductDescriptionID"),
	CONSTRAINT "FK_ProductModelProductDescriptionCulture_ProductModel_ProductMo" FOREIGN KEY ("ProductModelID") REFERENCES productmodel("ProductModelID")
);


-- productsubcategory definition

CREATE TABLE productsubcategory (
	"ProductSubcategoryID" int4 NOT NULL,
	"ProductCategoryID" int4 NOT NULL,
	"Name" text NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductSubcategory_target_pkey" PRIMARY KEY ("ProductSubcategoryID"),
	CONSTRAINT "FK_ProductSubcategory_ProductCategory_ProductCategoryID" FOREIGN KEY ("ProductCategoryID") REFERENCES productcategory("ProductCategoryID")
);


-- salesterritory definition

CREATE TABLE salesterritory (
	"TerritoryID" int4 NOT NULL,
	"Name" text NOT NULL,
	"CountryRegionCode" varchar(6) NOT NULL,
	"Group" varchar(100) NOT NULL,
	"SalesYTD" money NOT NULL,
	"SalesLastYear" money NOT NULL,
	"CostYTD" money NOT NULL,
	"CostLastYear" money NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SalesTerritory_target_pkey" PRIMARY KEY ("TerritoryID"),
	CONSTRAINT "FK_SalesTerritory_CountryRegion_CountryRegionCode" FOREIGN KEY ("CountryRegionCode") REFERENCES countryregion("CountryRegionCode")
);


-- stateprovince definition

CREATE TABLE stateprovince (
	"StateProvinceID" int4 NOT NULL,
	"StateProvinceCode" bpchar(6) NOT NULL,
	"CountryRegionCode" varchar(6) NOT NULL,
	"IsOnlyStateProvinceFlag" text NOT NULL,
	"Name" text NOT NULL,
	"TerritoryID" int4 NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "StateProvince_target_pkey" PRIMARY KEY ("StateProvinceID"),
	CONSTRAINT "FK_StateProvince_CountryRegion_CountryRegionCode" FOREIGN KEY ("CountryRegionCode") REFERENCES countryregion("CountryRegionCode"),
	CONSTRAINT "FK_StateProvince_SalesTerritory_TerritoryID" FOREIGN KEY ("TerritoryID") REFERENCES salesterritory("TerritoryID")
);


-- vendor definition

CREATE TABLE vendor (
	"BusinessEntityID" int4 NOT NULL,
	"AccountNumber" text NOT NULL,
	"Name" text NOT NULL,
	"CreditRating" int2 NOT NULL,
	"PreferredVendorStatus" text NOT NULL,
	"ActiveFlag" text NOT NULL,
	"PurchasingWebServiceURL" varchar(2048) NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Vendor_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Vendor_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID")
);


-- address definition

CREATE TABLE address (
	"AddressID" int4 NOT NULL,
	"AddressLine1" varchar(120) NOT NULL,
	"AddressLine2" varchar(120) NULL,
	"City" varchar(60) NOT NULL,
	"StateProvinceID" int4 NOT NULL,
	"PostalCode" varchar(30) NOT NULL,
	"SpatialLocation" text NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Address_target_pkey" PRIMARY KEY ("AddressID"),
	CONSTRAINT "FK_Address_StateProvince_StateProvinceID" FOREIGN KEY ("StateProvinceID") REFERENCES stateprovince("StateProvinceID")
);


-- businessentityaddress definition

CREATE TABLE businessentityaddress (
	"BusinessEntityID" int4 NOT NULL,
	"AddressID" int4 NOT NULL,
	"AddressTypeID" int4 NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "BusinessEntityAddress_target_pkey" PRIMARY KEY ("BusinessEntityID", "AddressID", "AddressTypeID"),
	CONSTRAINT "FK_BusinessEntityAddress_AddressType_AddressTypeID" FOREIGN KEY ("AddressTypeID") REFERENCES addresstype("AddressTypeID"),
	CONSTRAINT "FK_BusinessEntityAddress_Address_AddressID" FOREIGN KEY ("AddressID") REFERENCES address("AddressID"),
	CONSTRAINT "FK_BusinessEntityAddress_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID")
);


-- businessentitycontact definition

CREATE TABLE businessentitycontact (
	"BusinessEntityID" int4 NOT NULL,
	"PersonID" int4 NOT NULL,
	"ContactTypeID" int4 NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "BusinessEntityContact_target_pkey" PRIMARY KEY ("BusinessEntityID", "PersonID", "ContactTypeID"),
	CONSTRAINT "FK_BusinessEntityContact_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID"),
	CONSTRAINT "FK_BusinessEntityContact_ContactType_ContactTypeID" FOREIGN KEY ("ContactTypeID") REFERENCES contacttype("ContactTypeID"),
	CONSTRAINT "FK_BusinessEntityContact_Person_PersonID" FOREIGN KEY ("PersonID") REFERENCES person("BusinessEntityID")
);


-- emailaddress definition

CREATE TABLE emailaddress (
	"BusinessEntityID" int4 NOT NULL,
	"EmailAddressID" int4 NOT NULL,
	"EmailAddress" varchar(100) NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "EmailAddress_target_pkey" PRIMARY KEY ("BusinessEntityID", "EmailAddressID"),
	CONSTRAINT "FK_EmailAddress_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID")
);


-- employee definition

CREATE TABLE employee (
	"BusinessEntityID" int4 NOT NULL,
	"NationalIDNumber" varchar(30) NOT NULL,
	"LoginID" varchar(512) NOT NULL,
	"OrganizationNode" varchar(255) NULL,
	"OrganizationLevel" int2 NULL,
	"JobTitle" varchar(100) NOT NULL,
	"BirthDate" date NOT NULL,
	"MaritalStatus" bpchar(2) NOT NULL,
	"Gender" bpchar(2) NOT NULL,
	"HireDate" date NOT NULL,
	"SalariedFlag" text NOT NULL,
	"VacationHours" int2 NOT NULL,
	"SickLeaveHours" int2 NOT NULL,
	"CurrentFlag" text NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Employee_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Employee_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID")
);


-- employeedepartmenthistory definition

CREATE TABLE employeedepartmenthistory (
	"BusinessEntityID" int4 NOT NULL,
	"DepartmentID" int2 NOT NULL,
	"ShiftID" int2 NOT NULL,
	"StartDate" date NOT NULL,
	"EndDate" date NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "EmployeeDepartmentHistory_target_pkey" PRIMARY KEY ("BusinessEntityID", "DepartmentID", "ShiftID", "StartDate"),
	CONSTRAINT "FK_EmployeeDepartmentHistory_Department_DepartmentID" FOREIGN KEY ("DepartmentID") REFERENCES department("DepartmentID"),
	CONSTRAINT "FK_EmployeeDepartmentHistory_Employee_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES employee("BusinessEntityID"),
	CONSTRAINT "FK_EmployeeDepartmentHistory_Shift_ShiftID" FOREIGN KEY ("ShiftID") REFERENCES shift("ShiftID")
);


-- employeepayhistory definition

CREATE TABLE employeepayhistory (
	"BusinessEntityID" int4 NOT NULL,
	"RateChangeDate" timestamp NOT NULL,
	"Rate" money NOT NULL,
	"PayFrequency" int2 NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "EmployeePayHistory_target_pkey" PRIMARY KEY ("BusinessEntityID", "RateChangeDate"),
	CONSTRAINT "FK_EmployeePayHistory_Employee_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES employee("BusinessEntityID")
);


-- jobcandidate definition

CREATE TABLE jobcandidate (
	"JobCandidateID" int4 NOT NULL,
	"BusinessEntityID" int4 NULL,
	"Resume" xml NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "JobCandidate_target_pkey" PRIMARY KEY ("JobCandidateID"),
	CONSTRAINT "FK_JobCandidate_Employee_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES employee("BusinessEntityID")
);


-- password definition

CREATE TABLE "password" (
	"BusinessEntityID" int4 NOT NULL,
	"PasswordHash" varchar(128) NOT NULL,
	"PasswordSalt" varchar(10) NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Password_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Password_Person_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES person("BusinessEntityID")
);


-- product definition

CREATE TABLE product (
	"ProductID" int4 NOT NULL,
	"Name" text NOT NULL,
	"ProductNumber" varchar(50) NOT NULL,
	"MakeFlag" text NOT NULL,
	"FinishedGoodsFlag" text NOT NULL,
	"Color" varchar(30) NULL,
	"SafetyStockLevel" int2 NOT NULL,
	"ReorderPoint" int2 NOT NULL,
	"StandardCost" money NOT NULL,
	"ListPrice" money NOT NULL,
	"Size" varchar(10) NULL,
	"SizeUnitMeasureCode" bpchar(6) NULL,
	"WeightUnitMeasureCode" bpchar(6) NULL,
	"Weight" numeric NULL,
	"DaysToManufacture" int4 NOT NULL,
	"ProductLine" bpchar(4) NULL,
	"Class" bpchar(4) NULL,
	"Style" bpchar(4) NULL,
	"ProductSubcategoryID" int4 NULL,
	"ProductModelID" int4 NULL,
	"SellStartDate" timestamp NOT NULL,
	"SellEndDate" timestamp NULL,
	"DiscontinuedDate" timestamp NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Product_target_pkey" PRIMARY KEY ("ProductID"),
	CONSTRAINT "FK_Product_ProductModel_ProductModelID" FOREIGN KEY ("ProductModelID") REFERENCES productmodel("ProductModelID"),
	CONSTRAINT "FK_Product_ProductSubcategory_ProductSubcategoryID" FOREIGN KEY ("ProductSubcategoryID") REFERENCES productsubcategory("ProductSubcategoryID"),
	CONSTRAINT "FK_Product_UnitMeasure_SizeUnitMeasureCode" FOREIGN KEY ("SizeUnitMeasureCode") REFERENCES unitmeasure("UnitMeasureCode"),
	CONSTRAINT "FK_Product_UnitMeasure_WeightUnitMeasureCode" FOREIGN KEY ("WeightUnitMeasureCode") REFERENCES unitmeasure("UnitMeasureCode")
);


-- productcosthistory definition

CREATE TABLE productcosthistory (
	"ProductID" int4 NOT NULL,
	"StartDate" timestamp NOT NULL,
	"EndDate" timestamp NULL,
	"StandardCost" money NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductCostHistory_target_pkey" PRIMARY KEY ("ProductID", "StartDate"),
	CONSTRAINT "FK_ProductCostHistory_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productinventory definition

CREATE TABLE productinventory (
	"ProductID" int4 NOT NULL,
	"LocationID" int2 NOT NULL,
	"Shelf" varchar(20) NOT NULL,
	"Bin" int2 NOT NULL,
	"Quantity" int2 NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductInventory_target_pkey" PRIMARY KEY ("ProductID", "LocationID"),
	CONSTRAINT "FK_ProductInventory_Location_LocationID" FOREIGN KEY ("LocationID") REFERENCES "location"("LocationID"),
	CONSTRAINT "FK_ProductInventory_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productlistpricehistory definition

CREATE TABLE productlistpricehistory (
	"ProductID" int4 NOT NULL,
	"StartDate" timestamp NOT NULL,
	"EndDate" timestamp NULL,
	"ListPrice" money NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductListPriceHistory_target_pkey" PRIMARY KEY ("ProductID", "StartDate"),
	CONSTRAINT "FK_ProductListPriceHistory_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productproductphoto definition

CREATE TABLE productproductphoto (
	"ProductID" int4 NOT NULL,
	"ProductPhotoID" int4 NOT NULL,
	"Primary" text NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductProductPhoto_target_pkey" PRIMARY KEY ("ProductID", "ProductPhotoID"),
	CONSTRAINT "FK_ProductProductPhoto_ProductPhoto_ProductPhotoID" FOREIGN KEY ("ProductPhotoID") REFERENCES productphoto("ProductPhotoID"),
	CONSTRAINT "FK_ProductProductPhoto_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productreview definition

CREATE TABLE productreview (
	"ProductReviewID" int4 NOT NULL,
	"ProductID" int4 NOT NULL,
	"ReviewerName" text NOT NULL,
	"ReviewDate" timestamp NOT NULL,
	"EmailAddress" varchar(100) NOT NULL,
	"Rating" int4 NOT NULL,
	"Comments" varchar(7700) NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductReview_target_pkey" PRIMARY KEY ("ProductReviewID"),
	CONSTRAINT "FK_ProductReview_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- productvendor definition

CREATE TABLE productvendor (
	"ProductID" int4 NOT NULL,
	"BusinessEntityID" int4 NOT NULL,
	"AverageLeadTime" int4 NOT NULL,
	"StandardPrice" money NOT NULL,
	"LastReceiptCost" money NULL,
	"LastReceiptDate" timestamp NULL,
	"MinOrderQty" int4 NOT NULL,
	"MaxOrderQty" int4 NOT NULL,
	"OnOrderQty" int4 NULL,
	"UnitMeasureCode" bpchar(6) NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductVendor_target_pkey" PRIMARY KEY ("ProductID", "BusinessEntityID"),
	CONSTRAINT "FK_ProductVendor_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_ProductVendor_UnitMeasure_UnitMeasureCode" FOREIGN KEY ("UnitMeasureCode") REFERENCES unitmeasure("UnitMeasureCode"),
	CONSTRAINT "FK_ProductVendor_Vendor_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES vendor("BusinessEntityID")
);


-- purchaseorderheader definition

CREATE TABLE purchaseorderheader (
	"PurchaseOrderID" int4 NOT NULL,
	"RevisionNumber" int2 NOT NULL,
	"Status" int2 NOT NULL,
	"EmployeeID" int4 NOT NULL,
	"VendorID" int4 NOT NULL,
	"ShipMethodID" int4 NOT NULL,
	"OrderDate" timestamp NOT NULL,
	"ShipDate" timestamp NULL,
	"SubTotal" money NOT NULL,
	"TaxAmt" money NOT NULL,
	"Freight" money NOT NULL,
	"TotalDue" money NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "PurchaseOrderHeader_target_pkey" PRIMARY KEY ("PurchaseOrderID"),
	CONSTRAINT "FK_PurchaseOrderHeader_Employee_EmployeeID" FOREIGN KEY ("EmployeeID") REFERENCES employee("BusinessEntityID"),
	CONSTRAINT "FK_PurchaseOrderHeader_ShipMethod_ShipMethodID" FOREIGN KEY ("ShipMethodID") REFERENCES shipmethod("ShipMethodID"),
	CONSTRAINT "FK_PurchaseOrderHeader_Vendor_VendorID" FOREIGN KEY ("VendorID") REFERENCES vendor("BusinessEntityID")
);


-- salesperson definition

CREATE TABLE salesperson (
	"BusinessEntityID" int4 NOT NULL,
	"TerritoryID" int4 NULL,
	"SalesQuota" money NULL,
	"Bonus" money NOT NULL,
	"CommissionPct" money NOT NULL,
	"SalesYTD" money NOT NULL,
	"SalesLastYear" money NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SalesPerson_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_SalesPerson_Employee_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES employee("BusinessEntityID"),
	CONSTRAINT "FK_SalesPerson_SalesTerritory_TerritoryID" FOREIGN KEY ("TerritoryID") REFERENCES salesterritory("TerritoryID")
);


-- salespersonquotahistory definition

CREATE TABLE salespersonquotahistory (
	"BusinessEntityID" int4 NOT NULL,
	"QuotaDate" timestamp NOT NULL,
	"SalesQuota" money NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SalesPersonQuotaHistory_target_pkey" PRIMARY KEY ("BusinessEntityID", "QuotaDate"),
	CONSTRAINT "FK_SalesPersonQuotaHistory_SalesPerson_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES salesperson("BusinessEntityID")
);


-- salestaxrate definition

CREATE TABLE salestaxrate (
	"SalesTaxRateID" int4 NOT NULL,
	"StateProvinceID" int4 NOT NULL,
	"TaxType" int2 NOT NULL,
	"TaxRate" money NOT NULL,
	"Name" text NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SalesTaxRate_target_pkey" PRIMARY KEY ("SalesTaxRateID"),
	CONSTRAINT "FK_SalesTaxRate_StateProvince_StateProvinceID" FOREIGN KEY ("StateProvinceID") REFERENCES stateprovince("StateProvinceID")
);


-- salesterritoryhistory definition

CREATE TABLE salesterritoryhistory (
	"BusinessEntityID" int4 NOT NULL,
	"TerritoryID" int4 NOT NULL,
	"StartDate" timestamp NOT NULL,
	"EndDate" timestamp NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SalesTerritoryHistory_target_pkey" PRIMARY KEY ("BusinessEntityID", "TerritoryID", "StartDate"),
	CONSTRAINT "FK_SalesTerritoryHistory_SalesPerson_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES salesperson("BusinessEntityID"),
	CONSTRAINT "FK_SalesTerritoryHistory_SalesTerritory_TerritoryID" FOREIGN KEY ("TerritoryID") REFERENCES salesterritory("TerritoryID")
);


-- shoppingcartitem definition

CREATE TABLE shoppingcartitem (
	"ShoppingCartItemID" int4 NOT NULL,
	"ShoppingCartID" varchar(100) NOT NULL,
	"Quantity" int4 NOT NULL,
	"ProductID" int4 NOT NULL,
	"DateCreated" timestamp NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ShoppingCartItem_target_pkey" PRIMARY KEY ("ShoppingCartItemID"),
	CONSTRAINT "FK_ShoppingCartItem_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- store definition

CREATE TABLE store (
	"BusinessEntityID" int4 NOT NULL,
	"Name" text NOT NULL,
	"SalesPersonID" int4 NULL,
	"Demographics" xml NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Store_target_pkey" PRIMARY KEY ("BusinessEntityID"),
	CONSTRAINT "FK_Store_BusinessEntity_BusinessEntityID" FOREIGN KEY ("BusinessEntityID") REFERENCES businessentity("BusinessEntityID"),
	CONSTRAINT "FK_Store_SalesPerson_SalesPersonID" FOREIGN KEY ("SalesPersonID") REFERENCES salesperson("BusinessEntityID")
);


-- transactionhistory definition

CREATE TABLE transactionhistory (
	"TransactionID" int4 NOT NULL,
	"ProductID" int4 NOT NULL,
	"ReferenceOrderID" int4 NOT NULL,
	"ReferenceOrderLineID" int4 NOT NULL,
	"TransactionDate" timestamp NOT NULL,
	"TransactionType" bpchar(2) NOT NULL,
	"Quantity" int4 NOT NULL,
	"ActualCost" money NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "TransactionHistory_target_pkey" PRIMARY KEY ("TransactionID"),
	CONSTRAINT "FK_TransactionHistory_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- workorder definition

CREATE TABLE workorder (
	"WorkOrderID" int4 NOT NULL,
	"ProductID" int4 NOT NULL,
	"OrderQty" int4 NOT NULL,
	"StockedQty" int4 NOT NULL,
	"ScrappedQty" int2 NOT NULL,
	"StartDate" timestamp NOT NULL,
	"EndDate" timestamp NULL,
	"DueDate" timestamp NOT NULL,
	"ScrapReasonID" int2 NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "WorkOrder_target_pkey" PRIMARY KEY ("WorkOrderID"),
	CONSTRAINT "FK_WorkOrder_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_WorkOrder_ScrapReason_ScrapReasonID" FOREIGN KEY ("ScrapReasonID") REFERENCES scrapreason("ScrapReasonID")
);


-- workorderrouting definition

CREATE TABLE workorderrouting (
	"WorkOrderID" int4 NOT NULL,
	"ProductID" int4 NOT NULL,
	"OperationSequence" int2 NOT NULL,
	"LocationID" int2 NOT NULL,
	"ScheduledStartDate" timestamp NOT NULL,
	"ScheduledEndDate" timestamp NOT NULL,
	"ActualStartDate" timestamp NULL,
	"ActualEndDate" timestamp NULL,
	"ActualResourceHrs" numeric NULL,
	"PlannedCost" money NOT NULL,
	"ActualCost" money NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "WorkOrderRouting_target_pkey" PRIMARY KEY ("WorkOrderID", "ProductID", "OperationSequence"),
	CONSTRAINT "FK_WorkOrderRouting_Location_LocationID" FOREIGN KEY ("LocationID") REFERENCES "location"("LocationID"),
	CONSTRAINT "FK_WorkOrderRouting_WorkOrder_WorkOrderID" FOREIGN KEY ("WorkOrderID") REFERENCES workorder("WorkOrderID")
);


-- billofmaterials definition

CREATE TABLE billofmaterials (
	"BillOfMaterialsID" int4 NOT NULL,
	"ProductAssemblyID" int4 NULL,
	"ComponentID" int4 NOT NULL,
	"StartDate" timestamp NOT NULL,
	"EndDate" timestamp NULL,
	"UnitMeasureCode" bpchar(6) NOT NULL,
	"BOMLevel" int2 NOT NULL,
	"PerAssemblyQty" numeric NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "BillOfMaterials_target_pkey" PRIMARY KEY ("BillOfMaterialsID"),
	CONSTRAINT "FK_BillOfMaterials_Product_ComponentID" FOREIGN KEY ("ComponentID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_BillOfMaterials_Product_ProductAssemblyID" FOREIGN KEY ("ProductAssemblyID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_BillOfMaterials_UnitMeasure_UnitMeasureCode" FOREIGN KEY ("UnitMeasureCode") REFERENCES unitmeasure("UnitMeasureCode")
);


-- customer definition

CREATE TABLE customer (
	"CustomerID" int4 NOT NULL,
	"PersonID" int4 NULL,
	"StoreID" int4 NULL,
	"TerritoryID" int4 NULL,
	"AccountNumber" varchar(10) NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Customer_target_pkey" PRIMARY KEY ("CustomerID"),
	CONSTRAINT "FK_Customer_Person_PersonID" FOREIGN KEY ("PersonID") REFERENCES person("BusinessEntityID"),
	CONSTRAINT "FK_Customer_SalesTerritory_TerritoryID" FOREIGN KEY ("TerritoryID") REFERENCES salesterritory("TerritoryID"),
	CONSTRAINT "FK_Customer_Store_StoreID" FOREIGN KEY ("StoreID") REFERENCES store("BusinessEntityID")
);


-- document definition

CREATE TABLE "document" (
	"DocumentNode" varchar NOT NULL,
	"DocumentLevel" int2 NULL,
	"Title" varchar(100) NOT NULL,
	"Owner" int4 NOT NULL,
	"FolderFlag" bool NOT NULL,
	"FileName" varchar(800) NOT NULL,
	"FileExtension" varchar(16) NOT NULL,
	"Revision" bpchar(10) NOT NULL,
	"ChangeNumber" int4 NOT NULL,
	"Status" int2 NOT NULL,
	"DocumentSummary" text NULL,
	"Document" bytea NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "Document_target_pkey" PRIMARY KEY ("DocumentNode"),
	CONSTRAINT "FK_Document_Employee_Owner" FOREIGN KEY ("Owner") REFERENCES employee("BusinessEntityID")
);


-- productdocument definition

CREATE TABLE productdocument (
	"ProductID" int4 NOT NULL,
	"DocumentNode" varchar NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "ProductDocument_target_pkey" PRIMARY KEY ("ProductID", "DocumentNode"),
	CONSTRAINT "FK_ProductDocument_Document_DocumentNode" FOREIGN KEY ("DocumentNode") REFERENCES "document"("DocumentNode"),
	CONSTRAINT "FK_ProductDocument_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID")
);


-- purchaseorderdetail definition

CREATE TABLE purchaseorderdetail (
	"PurchaseOrderID" int4 NOT NULL,
	"PurchaseOrderDetailID" int4 NOT NULL,
	"DueDate" timestamp NOT NULL,
	"OrderQty" int2 NOT NULL,
	"ProductID" int4 NOT NULL,
	"UnitPrice" money NOT NULL,
	"LineTotal" money NOT NULL,
	"ReceivedQty" numeric NOT NULL,
	"RejectedQty" numeric NOT NULL,
	"StockedQty" numeric NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "PurchaseOrderDetail_target_pkey" PRIMARY KEY ("PurchaseOrderID", "PurchaseOrderDetailID"),
	CONSTRAINT "FK_PurchaseOrderDetail_Product_ProductID" FOREIGN KEY ("ProductID") REFERENCES product("ProductID"),
	CONSTRAINT "FK_PurchaseOrderDetail_PurchaseOrderHeader_PurchaseOrderID" FOREIGN KEY ("PurchaseOrderID") REFERENCES purchaseorderheader("PurchaseOrderID")
);


-- salesorderheader definition

CREATE TABLE salesorderheader (
	"SalesOrderID" int4 NOT NULL,
	"RevisionNumber" int2 NOT NULL,
	"OrderDate" timestamp NOT NULL,
	"DueDate" timestamp NOT NULL,
	"ShipDate" timestamp NULL,
	"Status" int2 NOT NULL,
	"OnlineOrderFlag" text NOT NULL,
	"SalesOrderNumber" varchar(50) NOT NULL,
	"PurchaseOrderNumber" text NULL,
	"AccountNumber" text NULL,
	"CustomerID" int4 NOT NULL,
	"SalesPersonID" int4 NULL,
	"TerritoryID" int4 NULL,
	"BillToAddressID" int4 NOT NULL,
	"ShipToAddressID" int4 NOT NULL,
	"ShipMethodID" int4 NOT NULL,
	"CreditCardID" int4 NULL,
	"CreditCardApprovalCode" varchar(15) NULL,
	"CurrencyRateID" int4 NULL,
	"SubTotal" money NOT NULL,
	"TaxAmt" money NOT NULL,
	"Freight" money NOT NULL,
	"TotalDue" money NOT NULL,
	"Comment" varchar(256) NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
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
	"SalesOrderID" int4 NOT NULL,
	"SalesReasonID" int4 NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SalesOrderHeaderSalesReason_target_pkey" PRIMARY KEY ("SalesOrderID", "SalesReasonID"),
	CONSTRAINT "FK_SalesOrderHeaderSalesReason_SalesOrderHeader_SalesOrderID" FOREIGN KEY ("SalesOrderID") REFERENCES salesorderheader("SalesOrderID"),
	CONSTRAINT "FK_SalesOrderHeaderSalesReason_SalesReason_SalesReasonID" FOREIGN KEY ("SalesReasonID") REFERENCES salesreason("SalesReasonID")
);


-- salesorderdetail definition

CREATE TABLE salesorderdetail (
	"SalesOrderID" int4 NOT NULL,
	"SalesOrderDetailID" int4 NOT NULL,
	"CarrierTrackingNumber" varchar(50) NULL,
	"OrderQty" int2 NOT NULL,
	"ProductID" int4 NOT NULL,
	"SpecialOfferID" int4 NOT NULL,
	"UnitPrice" money NOT NULL,
	"UnitPriceDiscount" money NOT NULL,
	"LineTotal" text NOT NULL,
	rowguid uuid NOT NULL,
	"ModifiedDate" timestamp NOT NULL,
	CONSTRAINT "SalesOrderDetail_target_pkey" PRIMARY KEY ("SalesOrderID", "SalesOrderDetailID"),
	CONSTRAINT "FK_SalesOrderDetail_SalesOrderHeader_SalesOrderID" FOREIGN KEY ("SalesOrderID") REFERENCES salesorderheader("SalesOrderID")
);