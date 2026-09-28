package com.dl3s.pontes.interop.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dl3s.pontes.interop.MarketDltPlatformLinks;
import com.dl3s.pontes.interop.MarketDltReferenceData;

/** Pontes reference data: market DLT platforms configured and Participants linked to them. */
@RestController
@RequestMapping("/api/eii/market-dlt-platforms")
class ReferenceDataController {

    private final MarketDltReferenceData referenceData;

    ReferenceDataController(MarketDltReferenceData referenceData) {
        this.referenceData = referenceData;
    }

    @GetMapping
    List<MarketDltPlatformLinks> platforms() {
        return referenceData.platforms();
    }

    /** Central Bank configuration: links a Participant to a platform. */
    @PostMapping("/{platform}/participants")
    MarketDltPlatformLinks link(@PathVariable String platform, @Valid @RequestBody LinkRequest request) {
        return referenceData.link(platform, request.participant());
    }

    record LinkRequest(@NotBlank String participant) {
    }
}
