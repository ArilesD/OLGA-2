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

@WebMvcTest(WorkflowExecutionController.class)
class WorkflowExecutionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private WorkflowBackendClient backendClient;

    @Test
    void startTask_shouldReturnTaskAndForm() throws Exception {
        when(backendClient.startTask("inv1", "user@test.com"))
                .thenReturn("{\"task_id\":\"t1\",\"form\":{\"form_id\":\"f1\"}}");

        mvc.perform(get("/execute/start")
                        .param("inventory_id", "inv1")
                        .param("email", "user@test.com"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"task_id\":\"t1\"}"));
    }

    @Test
    void startTask_backendDown_shouldReturn502() throws Exception {
        when(backendClient.startTask(any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        mvc.perform(get("/execute/start")
                        .param("inventory_id", "inv1")
                        .param("email", "user@test.com"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void nextTask_shouldReturnNextForm() throws Exception {
        when(backendClient.nextTask(eq("t1"), eq("user@test.com"), any()))
                .thenReturn("{\"form\":{\"form_id\":\"f2\"}}");

        mvc.perform(post("/execute/next")
                        .param("task_id", "t1")
                        .param("email", "user@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field1\":\"value1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"form\":{\"form_id\":\"f2\"}}"));
    }

    @Test
    void nextTask_withEmptyBody_shouldDefaultToEmpty() throws Exception {
        when(backendClient.nextTask(eq("t1"), eq("user@test.com"), eq("{}")))
                .thenReturn("{\"form\":null}");

        mvc.perform(post("/execute/next")
                        .param("task_id", "t1")
                        .param("email", "user@test.com")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"form\":null}"));
    }

    @Test
    void getStatus_shouldReturnCurrentForm() throws Exception {
        when(backendClient.getTaskStatus("t1"))
                .thenReturn("{\"form\":{\"form_id\":\"f1\"},\"data\":{}}");

        mvc.perform(get("/execute/status")
                        .param("task_id", "t1"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"form\":{\"form_id\":\"f1\"}}"));
    }

    @Test
    void getStatus_backendDown_shouldReturn502() throws Exception {
        when(backendClient.getTaskStatus(any()))
                .thenThrow(new RuntimeException("Timeout"));

        mvc.perform(get("/execute/status")
                        .param("task_id", "t1"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void getStartedTasks_shouldReturnList() throws Exception {
        when(backendClient.getStartedTasks("user@test.com"))
                .thenReturn("[{\"id\":\"t1\"}]");

        mvc.perform(get("/execute/tasks")
                        .param("email", "user@test.com"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":\"t1\"}]"));
    }
}