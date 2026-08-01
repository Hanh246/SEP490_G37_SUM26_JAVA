package com.sep.comiverse.integration.support;

import com.sep.comiverse.ComiverseApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

@ComiverseIntegrationTest
@SpringBootTest(classes = ComiverseApplication.class)
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest {

    @MockBean
    protected RedisTemplate<String, Object> redisTemplate;

    @MockBean
    protected StringRedisTemplate stringRedisTemplate;

    @MockBean
    protected org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @org.junit.jupiter.api.BeforeEach
    void setupRedisMocks() {
        org.springframework.data.redis.core.ValueOperations<String, Object> valueOps = org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }
}
