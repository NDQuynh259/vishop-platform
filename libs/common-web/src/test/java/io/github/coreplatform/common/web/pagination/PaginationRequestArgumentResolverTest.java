package io.github.coreplatform.common.web.pagination;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class PaginationRequestArgumentResolverTest {
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.standaloneSetup(new Probe())
            .setCustomArgumentResolvers(new PaginationRequestArgumentResolver())
            .build();
  }

  @Test
  void bindsDefaultsAndNormalizesOffsetParameters() throws Exception {
    mvc.perform(get("/pagination"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.limit").value(20));
    mvc.perform(get("/pagination").param("page", "0").param("limit", "200"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.limit").value(100));
    mvc.perform(get("/pagination").param("page", "3").param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(3))
        .andExpect(jsonPath("$.limit").value(10));
  }

  @Test
  void preservesCursorAndNormalizesBlankParameters() throws Exception {
    mvc.perform(get("/pagination").param("cursor", "opaque").param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cursor").value("opaque"))
        .andExpect(jsonPath("$.limit").value(5));
    mvc.perform(get("/pagination").param("cursor", " ").param("page", "").param("limit", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.limit").value(20));
  }

  @Test
  void rejectsInvalidNumbers() throws Exception {
    mvc.perform(get("/pagination").param("page", "invalid")).andExpect(status().isBadRequest());
    mvc.perform(get("/pagination").param("limit", "2147483648")).andExpect(status().isBadRequest());
  }

  @RestController
  static class Probe {
    @GetMapping("/pagination")
    public java.util.Map<String, Object> pagination(PaginationRequest pagination) {
      var result = new java.util.HashMap<String, Object>();
      result.put("cursor", pagination.cursor());
      result.put("page", pagination.page());
      result.put("limit", pagination.limit());
      return result;
    }
  }
}
