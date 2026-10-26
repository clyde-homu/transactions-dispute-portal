package com.clydehomu.disputes.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void createsAnIdWhenTheCallerSendsNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), response, capturingMdc(mdcDuringRequest));

        String echoed = response.getHeader(RequestIdFilter.HEADER);
        assertThat(echoed).isNotBlank();
        assertThat(mdcDuringRequest.get()).isEqualTo(echoed);
    }

    @Test
    void reusesAWellFormedIdFromTheCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "client-123.abc_DEF");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, capturingMdc(mdcDuringRequest));

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-123.abc_DEF");
        assertThat(mdcDuringRequest.get()).isEqualTo("client-123.abc_DEF");
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "semi;colon", "<script>", "line\nbreak", "{\"json\":1}"})
    void replacesAnIdWithUnsafeCharacters(String supplied) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, supplied);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .isNotEqualTo(supplied)
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void replacesAnOverlongId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(RequestIdFilter.HEADER)).hasSize(36);
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {});

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsTheMdcWhenTheChainFails() {
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private static FilterChain capturingMdc(AtomicReference<String> target) {
        return (req, res) -> target.set(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
