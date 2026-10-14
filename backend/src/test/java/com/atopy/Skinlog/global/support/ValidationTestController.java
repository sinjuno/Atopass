package com.atopy.Skinlog.global.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ValidationTestController {

    @PostMapping("/__test/validate")
    String echo(@Valid @RequestBody Body body) {
        return body.name();
    }

    record Body(@NotBlank String name) {
    }
}
