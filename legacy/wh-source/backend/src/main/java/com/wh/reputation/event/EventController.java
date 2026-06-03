package com.wh.reputation.event;

import com.wh.reputation.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ApiResponse<CreateEventResponseDto> create(@RequestBody CreateEventRequest body) {
        long id = eventService.create(body);
        return ApiResponse.ok(new CreateEventResponseDto(id));
    }

    @GetMapping
    public ApiResponse<List<EventItemDto>> list(@RequestParam("productId") Long productId) {
        return ApiResponse.ok(eventService.list(productId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        eventService.delete(id);
        return ApiResponse.ok(true);
    }
}
