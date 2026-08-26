package com.zorth.aiplatform.server.controller;

import com.zorth.aiplatform.server.auth.AuthUserResolver;
import com.zorth.aiplatform.server.conversation.AgentConversationQueryService;
import com.zorth.aiplatform.server.conversation.ConversationDetail;
import com.zorth.aiplatform.server.conversation.ConversationListItem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/agent/conversations")
public class AgentConversationController {

    private final AuthUserResolver authUserResolver;
    private final AgentConversationQueryService conversations;

    public AgentConversationController(
            AuthUserResolver authUserResolver, AgentConversationQueryService conversations) {
        this.authUserResolver = authUserResolver;
        this.conversations = conversations;
    }

    @GetMapping
    public List<ConversationListItem> list(HttpServletRequest request) {
        return conversations.list(userId(request));
    }

    @GetMapping("/{id}")
    public ConversationDetail get(@PathVariable("id") String id, HttpServletRequest request) {
        return conversations.get(userId(request), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id, HttpServletRequest request) {
        conversations.delete(userId(request), id);
    }

    private String userId(HttpServletRequest request) {
        return authUserResolver.requireUserId(request.getHeader(HttpHeaders.AUTHORIZATION));
    }
}
