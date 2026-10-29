package com.sdpcheck.sdpcheck.domain.member.entity;

import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kakao_id", unique = true)
    private Long kakaoId;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Enumerated(EnumType.STRING)
    @Column
    private Role role;

    @Column(unique = true, length = 50)
    private String loginId;

    @Column(length = 255)
    private String password;

    @Column(length = 50)
    private String name;

    @Column
    private Integer generation;

    public Member(
            String loginId,
            String password,
            String name,
            Integer generation,
            Role role
    ) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.generation = generation;
        this.role = role;
        this.onboardingCompleted = true;
    }

    public static Member createKakaoMember(Long kakaoId){
        if(kakaoId == null || kakaoId <= 0){
            throw new IllegalArgumentException("유효한 카카오 ID가 필요합니다.");
        }

        Member member = new Member();
        member.kakaoId = kakaoId;
        member.onboardingCompleted = false;
        return member;
    }

    public void completeOnboarding(
            String name,
            Integer generation,
            Role role
    ){
        this.name=name;
        this.generation=generation;
        this.role=role;
        this.onboardingCompleted=true;
    }
}
