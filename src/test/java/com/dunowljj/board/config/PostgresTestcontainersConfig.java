package com.dunowljj.board.config;

import org.hibernate.cfg.SchemaToolingSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 모든 통합/슬라이스 테스트가 공유하는 PostgreSQL Testcontainer 설정.
 *
 * <p>{@code static} 필드 + {@code static} 블록으로 Gradle test worker JVM 당
 * 컨테이너 1 개를 보장한다. {@code @DataJpaTest} / {@code @SpringBootTest} 등
 * 서로 다른 Spring context cache key 가 발생해도 {@code @Bean} 은 같은
 * static 인스턴스를 반환하므로 컨테이너는 재사용된다.
 *
 * <p>{@code @Bean(destroyMethod = "")} 은 필수. Spring 의 bean destroy
 * lifecycle 이 첫 context 종료 시 컨테이너 {@code close()} 를 호출하면
 * 나머지 context 의 후속 테스트가 깨진다. 컨테이너 종료는 Testcontainers/Ryuk
 * 와 JVM 종료 흐름에 맡긴다.
 *
 * <p>테스트 context 는 static 컨테이너를 공유하므로 Hibernate delayed drop 이
 * shutdown hook 순서와 경합하지 않게 {@code create-drop} 대신 {@code create}
 * 를 사용한다. 테스트 격리는 {@code @DataJpaTest} rollback 과 E2E cleanup 이
 * 책임진다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    static final PostgreSQLContainer CONTAINER =
        new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"));

    static {
        CONTAINER.start();
    }

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return CONTAINER;
    }

    @Bean
    HibernatePropertiesCustomizer testHibernateDdlAutoCustomizer() {
        return properties -> properties.put(SchemaToolingSettings.HBM2DDL_AUTO, "create");
    }
}
