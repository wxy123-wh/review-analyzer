package review.backend.application;

import review.backend.api.dto.ActionCreateRequest;
import review.backend.api.dto.ActionResponse;
import review.backend.data.ActionRepository;
import review.backend.data.ActionRepository.ActionValidationContext;
import java.time.Instant;
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

    public Optional<ActionValidationContext> findValidationContextById(String actionId) {
        return actionRepository.findValidationContextById(actionId);
    }

    public List<ActionValidationContext> listValidationContexts() {
        return actionRepository.listValidationContexts();
    }

    public void updateLaunchContext(String actionId, Instant launchedAt) {
        actionRepository.updateLaunchContext(actionId, launchedAt);
    }
}
