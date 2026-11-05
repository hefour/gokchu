package com.gokchu.gokchu.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 PostgreSQL을 Testcontainers로 띄운다. docker-compose와 같은 이미지(postgres:16-alpine)를
 * 써서 운영과 같은 DB 엔진/격리수준에서 검증하고, @ServiceConnection으로 datasource 설정을 자동 주입한다.
 * Flyway V1~V4가 그대로 적용되므로 ddl-auto: validate도 실제 스키마 기준으로 통과해야 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }
}
