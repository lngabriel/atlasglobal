package com.atlasglobal.shared;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @Test
    void doesNotExposeInternalFailureDetails() throws Exception {
        MockMvcBuilders.standaloneSetup(new FailureController())
            .setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(get("/failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Não foi possível concluir a solicitação."));
    }

    @RestController
    static class FailureController {
        @GetMapping("/failure") String fail() { throw new IllegalStateException("internal-secret"); }
    }
}
