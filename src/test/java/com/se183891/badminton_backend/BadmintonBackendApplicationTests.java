package com.se183891.badminton_backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Can SQL Server (CourtlyDB_Test): Flyway migrate + Hibernate validate schema
@SpringBootTest(properties = {
        // Integration test chay tren SQL Server THAT, database rieng CourtlyDB_Test (khong dung H2/SQLite)
        "spring.datasource.url=${TEST_DB_URL:jdbc:sqlserver://localhost:1433;databaseName=CourtlyDB_Test;encrypt=true;trustServerCertificate=true}",
        "app.seed.enabled=false",
        "spring.jpa.show-sql=false"
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class BadmintonBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
