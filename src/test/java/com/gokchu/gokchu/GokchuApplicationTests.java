package com.gokchu.gokchu;

import com.gokchu.gokchu.support.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestcontainersConfig.class)
class GokchuApplicationTests {

	@Test
	void contextLoads() {
	}

}
