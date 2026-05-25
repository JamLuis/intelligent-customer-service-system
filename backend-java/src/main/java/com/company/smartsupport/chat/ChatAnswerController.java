package com.company.smartsupport.chat;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.smartsupport.common.ApiResponse;
import com.company.smartsupport.common.RequestContext;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatAnswerController {

    private final RequestContext requestContext;
    private final ChatAnswerService chatAnswerService;

    public ChatAnswerController(RequestContext requestContext, ChatAnswerService chatAnswerService) {
        this.requestContext = requestContext;
        this.chatAnswerService = chatAnswerService;
    }

    @PostMapping("/knowledge-answer")
    public ApiResponse<Map<String, Object>> answer(@RequestBody Map<String, Object> body) {
        String projectId = requestContext.requireProjectId();
        return ApiResponse.success(requestContext.requestId(), chatAnswerService.answer(projectId, body));
    }
}