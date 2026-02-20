package com.biorad.csrag.testutil;

import com.biorad.csrag.app.CsRagApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composite annotation for integration tests.
 * Boots the full Spring context with MockMvc and H2 in-memory database.
 *
 * Usage:
 * <pre>
 * {@literal @}IntegrationTest
 * class MyIntegrationTest {
 *     @Autowired MockMvc mockMvc;
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public @interface IntegrationTest {
}
