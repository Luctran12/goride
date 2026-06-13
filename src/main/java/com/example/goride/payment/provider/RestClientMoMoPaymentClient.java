package com.example.goride.payment.provider;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class RestClientMoMoPaymentClient implements MoMoPaymentClient {
    static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;

    @Autowired
    public RestClientMoMoPaymentClient(RestClient.Builder restClientBuilder) {
        this(restClientBuilder, PROVIDER_TIMEOUT);
    }

    RestClientMoMoPaymentClient(RestClient.Builder restClientBuilder, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public MoMoCreatePaymentResponse createPayment(
            String checkoutUrl,
            MoMoCreatePaymentRequest request
    ) {
        try {
            MoMoCreatePaymentResponse response = restClient.post()
                    .uri(checkoutUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MoMoCreatePaymentResponse.class);
            if (response == null) {
                throw providerError("MoMo returned an empty checkout response");
            }
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_PROVIDER_ERROR,
                    "Unable to create MoMo checkout session"
            );
        }
    }

    private BusinessException providerError(String message) {
        return new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, message);
    }
}
