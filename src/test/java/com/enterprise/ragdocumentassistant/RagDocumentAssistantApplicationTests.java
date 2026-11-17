package com.enterprise.ragdocumentassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test",
        "spring.ai.model.embedding=openai",
        "spring.datasource.url=jdbc:h2:mem:ragassistant;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class RagDocumentAssistantApplicationTests {

	@Test
	void contextLoads() {
	}

}
