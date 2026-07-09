package com.sep.comiverse.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Configuration
public class DataSourcePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource) {
            DataSource dataSource = (DataSource) bean;
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // 1. Enable pgvector extension
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
                System.out.println("Database Setup: Successfully ran CREATE EXTENSION IF NOT EXISTS vector");

                // 2. Create helper functions for casting string/text to vector using pgvector's native explicit cast
                stmt.execute("CREATE OR REPLACE FUNCTION cast_varchar_to_vector(varchar) RETURNS vector AS $$\n" +
                             "    SELECT $1::vector;\n" +
                             "$$ LANGUAGE sql IMMUTABLE STRICT;");

                stmt.execute("CREATE OR REPLACE FUNCTION cast_text_to_vector(text) RETURNS vector AS $$\n" +
                             "    SELECT $1::vector;\n" +
                             "$$ LANGUAGE sql IMMUTABLE STRICT;");

                // 3. Create implicit casts if they do not exist
                stmt.execute("DO $$\n" +
                             "BEGIN\n" +
                             "    IF NOT EXISTS (\n" +
                             "        SELECT 1 FROM pg_cast \n" +
                             "        WHERE castsource = 'character varying'::regtype \n" +
                             "          AND casttarget = 'vector'::regtype\n" +
                             "    ) THEN\n" +
                             "        CREATE CAST (varchar AS vector) WITH FUNCTION cast_varchar_to_vector(varchar) AS IMPLICIT;\n" +
                             "    END IF;\n" +
                             "END $$;");

                stmt.execute("DO $$\n" +
                             "BEGIN\n" +
                             "    IF NOT EXISTS (\n" +
                             "        SELECT 1 FROM pg_cast \n" +
                             "        WHERE castsource = 'text'::regtype \n" +
                             "          AND casttarget = 'vector'::regtype\n" +
                             "    ) THEN\n" +
                             "        CREATE CAST (text AS vector) WITH FUNCTION cast_text_to_vector(text) AS IMPLICIT;\n" +
                             "    END IF;\n" +
                             "END $$;");

                System.out.println("Database Setup: Successfully registered implicit casts for varchar/text to vector");

            } catch (Exception e) {
                System.err.println("⚠️ Warning: Failed to configure database vector extensions/casts: " + e.getMessage());
            }
        }
        return bean;
    }
}
