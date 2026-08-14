package com.sep.comiverse.integration.support;

import com.sep.comiverse.ComiverseApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    @SuppressWarnings("unchecked")
    void setupRedisMocks() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        SetOperations<String, Object> setOps = mock(SetOperations.class);
        ZSetOperations<String, Object> zsetOps = mock(ZSetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);

        ValueOperations<String, String> strValueOps = mock(ValueOperations.class);
        HashOperations<String, Object, Object> strHashOps = mock(HashOperations.class);
        SetOperations<String, String> strSetOps = mock(SetOperations.class);
        ZSetOperations<String, String> strZsetOps = mock(ZSetOperations.class);

        when(stringRedisTemplate.opsForValue()).thenReturn(strValueOps);
        when(stringRedisTemplate.opsForHash()).thenReturn(strHashOps);
        when(stringRedisTemplate.opsForSet()).thenReturn(strSetOps);
        when(stringRedisTemplate.opsForZSet()).thenReturn(strZsetOps);
    }
}
