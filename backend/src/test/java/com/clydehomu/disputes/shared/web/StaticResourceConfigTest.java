package com.clydehomu.disputes.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clydehomu.disputes.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Uses the fixtures under src/test/resources/static, standing in for the built SPA. */
@WebMvcTest(SpaForwardController.class)
@Import({SecurityConfig.class, StaticResourceConfig.class})
class StaticResourceConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hashedAssetsAreCachedForAYearAndNeverRevalidated() throws Exception {
        MvcResult result = mockMvc.perform(get("/assets/fixture.0123abcd.js"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control"))
                .contains("max-age=31536000")
                .contains("public")
                .contains("immutable")
                .doesNotContain("no-store");
    }

    @Test
    void headOnAnAssetCarriesTheSameCacheHeader() throws Exception {
        MvcResult result = mockMvc.perform(head("/assets/fixture.0123abcd.js"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains("immutable");
    }

    @Test
    void theSpaShellIsNeverCached() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
    }

    @Test
    void apiResponsesAreNeverCached() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
    }

    @Test
    void aMissingAssetIsAProblem404WithoutLongTermCaching() throws Exception {
        MvcResult result = mockMvc.perform(get("/assets/missing.9999.js"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).doesNotContain("immutable");
    }
}
