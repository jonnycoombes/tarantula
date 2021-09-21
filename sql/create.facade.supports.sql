-- noinspection SqlNoDataSourceInspectionForFile

USE [%%DATABASE_NAME%%]
GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE VIEW [%%SCHEMA%%].[Facade_Category]
AS
SELECT DataID, Name, VersionNum AS CurrentVersion
FROM DTreeCore
WHERE (SubType = 131)
GO

CREATE FUNCTION [%%SCHEMA%%].[Facade_AttributeIndexFromRegion](@regionString VARCHAR(MAX))
RETURNS int
AS
BEGIN
DECLARE @reversed VARCHAR(MAX) = REVERSE(@regionString)
RETURN CONVERT(INT, REVERSE(SUBSTRING(@reversed, 1, CHARINDEX('_', @reversed) - 1)))
END
GO


CREATE VIEW [%%SCHEMA%%].[Facade_Attributes]
AS
SELECT Facade_Category.DataID AS CategoryId,
CatRegionMap.CatName AS Category,
Facade_Category.CurrentVersion AS CategoryVersion,
CatRegionMap.AttrName AS Attribute,
%%SCHEMA%%.Facade_AttributeIndexFromRegion(CatRegionMap.RegionName) AS AttributeIndex
FROM Facade_Category
	INNER JOIN CatRegionMap
	ON Facade_Category.DataID = CatRegionMap.CatID
		GROUP BY 
			Facade_Category.DataID,CatRegionMap.CatName,
			CatRegionMap.AttrName,
			CatRegionMap.AttrType,
			%%SCHEMA%%.Facade_AttributeIndexFromRegion(CatRegionMap.RegionName),
			Facade_Category.CurrentVersion
GO


