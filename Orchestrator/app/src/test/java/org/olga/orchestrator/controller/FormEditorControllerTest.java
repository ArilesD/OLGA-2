package org.olga.orchestrator.controller;

import org.junit.jupiter.api.Test;
import org.olga.orchestrator.client.WorkflowBackendClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FormEditorController.class)
class FormEditorControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private WorkflowBackendClient backendClient;

    @Test
    void getForms_shouldReturnList() throws Exception {
        when(backendClient.getForms()).thenReturn("[{\"form_id\":\"f1\"}]");

        mvc.perform(get("/forms"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"form_id\":\"f1\"}]"));
    }

    @Test
    void getForm_shouldReturnForm() throws Exception {
        when(backendClient.getForm("f1")).thenReturn("{\"form_id\":\"f1\",\"fields\":[]}");

        mvc.perform(get("/forms/f1"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"form_id\":\"f1\"}"));
    }

    @Test
    void saveForm_shouldReturnOk() throws Exception {
        when(backendClient.saveForm(any())).thenReturn("{\"form_id\":\"f_new\"}");

        mvc.perform(post("/forms/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"form_label\":\"Test\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"form_id\":\"f_new\"}"));
    }

    @Test
    void updateForm_shouldReturnOk() throws Exception {
        when(backendClient.updateForm(eq("f1"), any())).thenReturn("{\"form_id\":\"f1\"}");

        mvc.perform(post("/forms/update/f1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"form_label\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"form_id\":\"f1\"}"));
    }

    @Test
    void deleteForm_shouldReturnOk() throws Exception {
        when(backendClient.deleteForm("f1")).thenReturn("{\"deleted\":true}");

        mvc.perform(delete("/forms/f1"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"deleted\":true}"));
    }

    @Test
    void backendDown_shouldReturn502() throws Exception {
        when(backendClient.getForms()).thenThrow(new RuntimeException("Backend timeout"));

        mvc.perform(get("/forms"))
                .andExpect(status().isBadGateway());
    }
}
