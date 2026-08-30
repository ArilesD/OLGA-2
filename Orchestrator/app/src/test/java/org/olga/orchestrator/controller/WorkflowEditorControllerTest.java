package org.olga.orchestrator.controller;

import org.junit.jupiter.api.Test;
import org.olga.orchestrator.client.WorkflowBackendClient;
import org.olga.orchestrator.dto.WorkflowSaveRequest;
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

@WebMvcTest(WorkflowEditorController.class)
class WorkflowEditorControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private WorkflowBackendClient backendClient;

    @Test
    void getWorkflows_shouldReturnList() throws Exception {
        when(backendClient.getWorkflows()).thenReturn("[{\"workflow_id\":\"wf1\"}]");

        mvc.perform(get("/workflows"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"workflow_id\":\"wf1\"}]"));
    }

    @Test
    void getWorkflow_shouldReturnWorkflow() throws Exception {
        when(backendClient.getWorkflow("wf1")).thenReturn("{\"workflow_id\":\"wf1\",\"nodes\":[]}");

        mvc.perform(get("/workflows/wf1"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"workflow_id\":\"wf1\"}"));
    }

    @Test
    void saveWorkflow_shouldReturnOk() throws Exception {
        when(backendClient.saveWorkflow(eq("wf1"), any(WorkflowSaveRequest.class)))
                .thenReturn("{\"document_id\":\"doc1\"}");

        mvc.perform(put("/workflows/wf1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"edited_by\":\"test\",\"workflow\":{\"nodes\":[],\"edges\":[]}}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"document_id\":\"doc1\"}"));
    }

    @Test
    void saveWorkflow_withoutWorkflow_shouldReturn400() throws Exception {
        mvc.perform(put("/workflows/wf1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"edited_by\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getVersions_shouldReturnList() throws Exception {
        when(backendClient.getVersions("wf1")).thenReturn("[{\"version_id\":\"v1\"}]");

        mvc.perform(get("/workflows/wf1/versions"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"version_id\":\"v1\"}]"));
    }

    @Test
    void restoreVersion_shouldReturnOk() throws Exception {
        when(backendClient.restoreVersion(eq("wf1"), eq("v1"), any()))
                .thenReturn("{\"restored_version_id\":\"v1\"}");

        mvc.perform(post("/workflows/wf1/versions/v1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"edited_by\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"restored_version_id\":\"v1\"}"));
    }

    @Test
    void backendDown_shouldReturn502() throws Exception {
        when(backendClient.getWorkflows()).thenThrow(new RuntimeException("Connection refused"));

        mvc.perform(get("/workflows"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string("{\"error\":\"Bad Gateway\",\"details\":\"Connection refused\"}"));
    }
}
