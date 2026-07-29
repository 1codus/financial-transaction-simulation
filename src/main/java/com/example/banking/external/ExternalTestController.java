package com.example.banking.external;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test/external")
@RequiredArgsConstructor
public class ExternalTestController {

    private final ExternalFraudCheckService externalFraudCheckService;

    @PostMapping("/force-fail")
    public String forceFail(@RequestParam boolean enabled) {
        externalFraudCheckService.setForceFail(enabled);
        return "forceFail=" + enabled;
    }
}