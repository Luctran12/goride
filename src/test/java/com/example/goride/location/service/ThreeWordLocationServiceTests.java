package com.example.goride.location.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.location.config.ThreeWordLocationProperties;
import com.example.goride.location.provider.ThreeWordLocationClient;
import com.example.goride.location.provider.ThreeWordLocationClient.ProviderCellBounds;
import com.example.goride.location.provider.ThreeWordLocationClient.ProviderLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ThreeWordLocationServiceTests {
    private static final BigDecimal LATITUDE = BigDecimal.valueOf(10.7769);
    private static final BigDecimal LONGITUDE = BigDecimal.valueOf(106.7009);

    @Test
    void convertsCoordinatesAndMapsProviderBoundsForMobile() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationService service = new ThreeWordLocationService(enabledProperties(), client);
        when(client.toWords("http://localhost:5000", LATITUDE, LONGITUDE))
                .thenReturn(location(List.of("hoa", "con_meo", "cay"), "hoa.con_meo.cay"));

        var response = service.toWords(LATITUDE, LONGITUDE);

        assertThat(response.lat()).isEqualByComparingTo(LATITUDE);
        assertThat(response.lng()).isEqualByComparingTo(LONGITUDE);
        assertThat(response.words()).containsExactly("hoa", "con meo", "cay");
        assertThat(response.wordAddress()).isEqualTo("hoa.con meo.cay");
        assertThat(response.bounds().southwest().lat()).isEqualByComparingTo("10.7768");
        assertThat(response.bounds().northeast().lng()).isEqualByComparingTo("106.7010");
    }

    @Test
    void normalizesDriverInputBeforeResolvingCoordinates() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationService service = new ThreeWordLocationService(enabledProperties(), client);
        when(client.toCoordinate("http://localhost:5000", "hoa.khuon_mat.cay"))
                .thenReturn(location(null, "hoa.khuon_mat.cay"));

        var response = service.toCoordinate(" ///Hoa . khuon   mat . CAY ");

        assertThat(response.wordAddress()).isEqualTo("hoa.khuon mat.cay");
        assertThat(response.words()).containsExactly("hoa", "khuon mat", "cay");
        verify(client).toCoordinate("http://localhost:5000", "hoa.khuon_mat.cay");
    }

    @Test
    void rejectsMalformedAddressBeforeCallingProvider() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationService service = new ThreeWordLocationService(enabledProperties(), client);

        assertThatThrownBy(() -> service.toCoordinate("hoa..cay"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_INVALID_ADDRESS)
                );
        assertThatThrownBy(() -> service.toCoordinate("hoa.la"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.toCoordinate("   "))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(client);
    }

    @Test
    void rejectsInvalidWgs84CoordinatesBeforeCallingProvider() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationService service = new ThreeWordLocationService(enabledProperties(), client);

        assertThatThrownBy(() -> service.toWords(BigDecimal.valueOf(91), LONGITUDE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );
        verifyNoInteractions(client);
    }

    @Test
    void returnsUnavailableWhenProviderIsDisabled() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationService service = new ThreeWordLocationService(
                new ThreeWordLocationProperties(),
                client
        );

        assertThatThrownBy(() -> service.toWords(LATITUDE, LONGITUDE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_PROVIDER_UNAVAILABLE)
                );
        verifyNoInteractions(client);
    }

    @Test
    void returnsUnavailableWhenBaseUrlIsInvalid() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationProperties properties = enabledProperties();
        properties.setBaseUrl("not-an-http-url");
        ThreeWordLocationService service = new ThreeWordLocationService(properties, client);

        assertThatThrownBy(() -> service.toWords(LATITUDE, LONGITUDE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.WORD_LOCATION_PROVIDER_UNAVAILABLE)
                );
        verifyNoInteractions(client);
    }

    @Test
    void rejectsInconsistentProviderAddress() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationService service = new ThreeWordLocationService(enabledProperties(), client);
        when(client.toWords("http://localhost:5000", LATITUDE, LONGITUDE))
                .thenReturn(location(List.of("hoa", "la", "cay"), "khac.la.cay"));

        assertThatThrownBy(() -> service.toWords(LATITUDE, LONGITUDE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_PROVIDER_ERROR)
                );
    }

    @Test
    void rejectsCoordinateLookupWhenProviderReturnsAnotherAddress() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationService service = new ThreeWordLocationService(enabledProperties(), client);
        when(client.toCoordinate("http://localhost:5000", "hoa.la.cay"))
                .thenReturn(location(null, "khac.la.cay"));

        assertThatThrownBy(() -> service.toCoordinate("hoa.la.cay"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_PROVIDER_ERROR)
                );
    }


    @Test
    void rejectsInvalidProviderBounds() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationService service = new ThreeWordLocationService(enabledProperties(), client);
        ProviderLocation invalid = new ProviderLocation(
                LATITUDE,
                LONGITUDE,
                List.of("hoa", "la", "cay"),
                "hoa.la.cay",
                new ProviderCellBounds(List.of(LATITUDE), List.of(LATITUDE, LONGITUDE))
        );
        when(client.toWords("http://localhost:5000", LATITUDE, LONGITUDE)).thenReturn(invalid);

        assertThatThrownBy(() -> service.toWords(LATITUDE, LONGITUDE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_PROVIDER_ERROR)
                );
    }

    @Test
    void rejectsInvertedBoundsOrCoordinatesOutsideCell() {
        ThreeWordLocationClient client = mock(ThreeWordLocationClient.class);
        ThreeWordLocationService service = new ThreeWordLocationService(enabledProperties(), client);
        ProviderLocation inverted = new ProviderLocation(
                LATITUDE,
                LONGITUDE,
                List.of("hoa", "la", "cay"),
                "hoa.la.cay",
                new ProviderCellBounds(
                        List.of(BigDecimal.valueOf(10.7770), BigDecimal.valueOf(106.7010)),
                        List.of(BigDecimal.valueOf(10.7768), BigDecimal.valueOf(106.7008))
                )
        );
        when(client.toWords("http://localhost:5000", LATITUDE, LONGITUDE)).thenReturn(inverted);

        assertThatThrownBy(() -> service.toWords(LATITUDE, LONGITUDE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_PROVIDER_ERROR)
                );

        ProviderLocation outsideCell = new ProviderLocation(
                LATITUDE,
                LONGITUDE,
                List.of("hoa", "la", "cay"),
                "hoa.la.cay",
                new ProviderCellBounds(
                        List.of(BigDecimal.valueOf(10.7000), BigDecimal.valueOf(106.6000)),
                        List.of(BigDecimal.valueOf(10.7100), BigDecimal.valueOf(106.6100))
                )
        );
        when(client.toWords("http://localhost:5000", LATITUDE, LONGITUDE)).thenReturn(outsideCell);

        assertThatThrownBy(() -> service.toWords(LATITUDE, LONGITUDE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_PROVIDER_ERROR)
                );
    }

    private ThreeWordLocationProperties enabledProperties() {
        ThreeWordLocationProperties properties = new ThreeWordLocationProperties();
        properties.setEnabled(true);
        return properties;
    }

    private ProviderLocation location(List<String> words, String address) {
        return new ProviderLocation(
                LATITUDE,
                LONGITUDE,
                words,
                address,
                new ProviderCellBounds(
                        List.of(BigDecimal.valueOf(10.7768), BigDecimal.valueOf(106.7008)),
                        List.of(BigDecimal.valueOf(10.7770), BigDecimal.valueOf(106.7010))
                )
        );
    }
}
