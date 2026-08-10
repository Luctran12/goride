package com.example.goride.location.controller;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.common.error.GlobalExceptionHandler;
import com.example.goride.location.dto.LocationCoordinateResponse;
import com.example.goride.location.dto.ThreeWordCellBoundsResponse;
import com.example.goride.location.dto.ThreeWordLocationResponse;
import com.example.goride.location.service.ThreeWordLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ThreeWordLocationControllerTests {
    private ThreeWordLocationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ThreeWordLocationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThreeWordLocationController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsMobileEnvelopeForCoordinateConversion() throws Exception {
        when(service.toWords(BigDecimal.valueOf(10.7769), BigDecimal.valueOf(106.7009)))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/locations/to-words")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lat").value(10.7769))
                .andExpect(jsonPath("$.data.lng").value(106.7009))
                .andExpect(jsonPath("$.data.wordAddress").value("hoa.con meo.cay"))
                .andExpect(jsonPath("$.data.words[1]").value("con meo"))
                .andExpect(jsonPath("$.data.bounds.southwest.lat").value(10.7768));

        verify(service).toWords(BigDecimal.valueOf(10.7769), BigDecimal.valueOf(106.7009));
    }

    @Test
    void returnsMobileEnvelopeForAddressLookup() throws Exception {
        when(service.toCoordinate("Hoa . La . Cay")).thenReturn(response());

        mockMvc.perform(get("/api/v1/locations/to-coordinate")
                        .param("address", "Hoa . La . Cay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wordAddress").value("hoa.con meo.cay"))
                .andExpect(jsonPath("$.data.bounds.northeast.lng").value(106.7010));

        verify(service).toCoordinate("Hoa . La . Cay");
    }

    @Test
    void mapsMissingQueryParameterToValidationEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/locations/to-words")
                        .param("lat", "10.7769"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.lng").value("Required request parameter is missing"));
    }

    @Test
    void mapsMalformedCoordinateToValidationEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/locations/to-words")
                        .param("lat", "not-a-number")
                        .param("lng", "106.7009"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.lat")
                        .value("Request parameter has an unsupported value or format"));
    }

    @Test
    void preservesStableNotFoundErrorFromProviderLayer() throws Exception {
        when(service.toCoordinate("hoa.la.sai"))
                .thenThrow(new BusinessException(
                        ErrorCode.WORD_LOCATION_NOT_FOUND,
                        "Three-word address was not found"
                ));

        mockMvc.perform(get("/api/v1/locations/to-coordinate")
                        .param("address", "hoa.la.sai"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORD_LOCATION_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Three-word address was not found"));
    }

    private ThreeWordLocationResponse response() {
        return new ThreeWordLocationResponse(
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                List.of("hoa", "con meo", "cay"),
                "hoa.con meo.cay",
                new ThreeWordCellBoundsResponse(
                        new LocationCoordinateResponse(
                                BigDecimal.valueOf(10.7768),
                                BigDecimal.valueOf(106.7008)
                        ),
                        new LocationCoordinateResponse(
                                BigDecimal.valueOf(10.7770),
                                BigDecimal.valueOf(106.7010)
                        )
                )
        );
    }
}
