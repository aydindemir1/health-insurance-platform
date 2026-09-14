package com.aydindemir.health.search.presentation.rest;

import com.aydindemir.health.search.application.port.in.SearchRebuildUseCase;
import com.aydindemir.health.search.application.port.in.SearchRecordsUseCase;
import com.aydindemir.health.search.infrastructure.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SearchController.class, SearchRebuildController.class})
@Import({SecurityConfiguration.class, AuthenticatedActorMapper.class})
class SearchSecurityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SearchRecordsUseCase search;
    @MockitoBean SearchRebuildUseCase rebuild;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void returnsProblemDetailsWhenAuthenticationIsMissing() throws Exception {
        mvc.perform(get("/api/v1/search"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void enforcesAdministratorRoleBeforeCallingTheRebuildUseCase() throws Exception {
        mvc.perform(post("/api/v1/admin/search-rebuilds")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schemaVersion\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Operation not permitted"))
                .andExpect(jsonPath("$.status").value(403));
        verify(rebuild, never()).create(any(), anyInt());
    }
}
