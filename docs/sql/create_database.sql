-- Tao database cho Courtly (chay 1 lan bang SSMS / Azure Data Studio / sqlcmd voi quyen sysadmin).
-- Bang se do Flyway tu tao khi khoi dong ung dung, KHONG tao bang o day.
--   sqlcmd -S localhost -U sa -P "<mat khau>" -C -i docs/sql/create_database.sql

IF DB_ID(N'CourtlyDB') IS NULL
BEGIN
    CREATE DATABASE CourtlyDB COLLATE Vietnamese_CI_AS;
END
GO

-- Database rieng cho integration test (test xoa du lieu sau moi ca)
IF DB_ID(N'CourtlyDB_Test') IS NULL
BEGIN
    CREATE DATABASE CourtlyDB_Test COLLATE Vietnamese_CI_AS;
END
GO
