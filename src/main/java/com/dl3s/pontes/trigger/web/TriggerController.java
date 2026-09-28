package com.dl3s.pontes.trigger.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dl3s.pontes.trigger.TriggerBackend;
import com.dl3s.pontes.trigger.TriggerView;

/** Lists processed triggers (monitoring). */
@RestController
@RequestMapping("/api/t2/triggers")
class TriggerController {

    private final TriggerBackend triggerBackend;

    TriggerController(TriggerBackend triggerBackend) {
        this.triggerBackend = triggerBackend;
    }

    @GetMapping
    List<TriggerView> list() {
        return triggerBackend.list();
    }
}
