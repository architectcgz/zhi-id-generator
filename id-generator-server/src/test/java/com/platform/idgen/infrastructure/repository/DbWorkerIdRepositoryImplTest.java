package com.platform.idgen.infrastructure.repository;

import com.platform.idgen.domain.exception.WorkerIdUnavailableException;
import com.platform.idgen.infrastructure.config.SnowflakeProperties;
import com.platform.idgen.infrastructure.persistence.mapper.WorkerIdAllocMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DbWorkerIdRepositoryImplTest {

    @TempDir
    Path tempDir;

    @Test
    void registerWorkerId在数据库模式下拒绝使用本地缓存降级() throws Exception {
        SnowflakeProperties properties = new SnowflakeProperties();
        properties.setWorkerId(-1);
        properties.setDatacenterId(1);
        properties.setWorkerIdCachePath(tempDir.resolve("workerID.properties").toString());

        Properties cache = new Properties();
        cache.setProperty("workerId", "7");
        try (var output = Files.newOutputStream(Path.of(properties.getWorkerIdCachePath()))) {
            cache.store(output, "test cache");
        }

        WorkerIdAllocMapper mapper = mock(WorkerIdAllocMapper.class);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("server.port", "8011")).thenReturn("8011");

        DbWorkerIdRepositoryImpl repository = org.mockito.Mockito.spy(
                new DbWorkerIdRepositoryImpl(properties, mapper, environment));
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(DbWorkerIdRepositoryImpl.class)).thenReturn(repository);
        ReflectionTestUtils.setField(repository, "applicationContext", applicationContext);
        doReturn(null).when(repository).acquireFromDatabase();

        assertThrows(WorkerIdUnavailableException.class, repository::registerWorkerId);
    }
}
