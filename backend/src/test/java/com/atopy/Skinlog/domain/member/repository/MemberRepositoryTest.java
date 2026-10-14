package com.atopy.Skinlog.domain.member.repository;

import com.atopy.Skinlog.domain.member.entity.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class MemberRepositoryTest {

    @Autowired MemberRepository memberRepository;

    @Test
    void saveAndFindByEmail_roundTrips() {
        memberRepository.save(new Member("user@example.com", "사용자", "encoded-password"));

        var found = memberRepository.findByEmail("user@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("사용자");
    }

    @Test
    void duplicateEmail_violatesUniqueConstraint() {
        memberRepository.save(new Member("dup@example.com", "닉네임1", "pw1"));
        memberRepository.flush();

        assertThatThrownBy(() -> {
            memberRepository.save(new Member("dup@example.com", "닉네임2", "pw2"));
            memberRepository.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
