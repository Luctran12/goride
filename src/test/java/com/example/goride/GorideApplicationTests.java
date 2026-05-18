package com.example.goride;

import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class GorideApplicationTests {
	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private DriverProfileRepository driverProfileRepository;

	@Test
	void contextLoads() {
	}

}
