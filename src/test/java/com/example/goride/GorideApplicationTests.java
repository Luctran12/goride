package com.example.goride;

import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.rating.repository.RatingRepository;
import com.example.goride.tracking.repository.TripLocationHistoryRepository;
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

	@MockitoBean
	private PricingConfigRepository pricingConfigRepository;

	@MockitoBean
	private TripRepository tripRepository;

	@MockitoBean
	private TripStatusHistoryRepository tripStatusHistoryRepository;

	@MockitoBean
	private TripLocationHistoryRepository tripLocationHistoryRepository;

	@MockitoBean
	private PaymentRepository paymentRepository;

	@MockitoBean
	private RatingRepository ratingRepository;

	@Test
	void contextLoads() {
	}

}
