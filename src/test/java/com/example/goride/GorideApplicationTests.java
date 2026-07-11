package com.example.goride;

import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.SurgePricingRuleRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.chat.repository.TripMessageRepository;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.notification.repository.NotificationRepository;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.payment.repository.PaymentSandboxE2eSessionRepository;
import com.example.goride.payment.repository.PaymentSandboxUatResultRepository;
import com.example.goride.rating.repository.RatingRepository;
import com.example.goride.servicearea.repository.ServiceAreaRepository;
import com.example.goride.tracking.repository.TripLocationHistoryRepository;
import com.example.goride.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "management.endpoint.health.group.readiness.include=readinessState")
class GorideApplicationTests {
	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private DriverProfileRepository driverProfileRepository;

	@MockitoBean
	private PricingConfigRepository pricingConfigRepository;

	@MockitoBean
	private SurgePricingRuleRepository surgePricingRuleRepository;

	@MockitoBean
	private TripRepository tripRepository;

	@MockitoBean
	private TripStatusHistoryRepository tripStatusHistoryRepository;

	@MockitoBean
	private TripLocationHistoryRepository tripLocationHistoryRepository;

	@MockitoBean
	private PaymentRepository paymentRepository;

	@MockitoBean
	private PaymentSandboxUatResultRepository paymentSandboxUatResultRepository;

	@MockitoBean
	private PaymentSandboxE2eSessionRepository paymentSandboxE2eSessionRepository;

	@MockitoBean
	private RatingRepository ratingRepository;

	@MockitoBean
	private ServiceAreaRepository serviceAreaRepository;

	@MockitoBean
	private NotificationRepository notificationRepository;

	@MockitoBean
	private TripMessageRepository tripMessageRepository;

	@Test
	void contextLoads() {
	}

}
