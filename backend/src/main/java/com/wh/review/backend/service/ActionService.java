package com.wh.review.backend.service;

import com.wh.review.backend.dto.ActionCreateRequest;
import com.wh.review.backend.dto.ActionResponse;
import com.wh.review.backend.persistence.ActionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ActionService {

    private final ActionRepository actionRepository;

    public ActionService(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    public ActionResponse create(ActionCreateRequest request) {
        return actionRepository.create(request);
    }

    public Optional<ActionResponse> findById(String actionId) {
        return actionRepository.findById(actionId);
    }

    public List<ActionResponse> listAll() {
        return actionRepository.listAll();
    }
}
