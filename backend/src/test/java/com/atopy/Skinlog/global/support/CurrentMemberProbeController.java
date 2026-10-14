package com.atopy.Skinlog.global.support;

import com.atopy.Skinlog.global.auth.AuthenticatedMember;
import com.atopy.Skinlog.global.auth.CurrentMember;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CurrentMemberProbeController {

    @GetMapping("/__test/current-member")
    Long me(@CurrentMember AuthenticatedMember member) {
        return member.memberId();
    }
}
