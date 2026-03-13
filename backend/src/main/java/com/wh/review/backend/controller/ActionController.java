package com.wh.review.backend.controller;

import com.wh.review.backend.dto.ActionCreateRequest;
import com.wh.review.backend.dto.ActionResponse;
import com.wh.review.backend.service.ActionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    @PostMapping("/actions")
    public ResponseEntity<ActionResponse> create(@Valid @RequestBody ActionCreateRequest request) {
        ActionResponse action = actionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(action);
    }
}
