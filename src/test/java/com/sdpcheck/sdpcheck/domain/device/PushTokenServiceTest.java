package com.sdpcheck.sdpcheck.domain.device;

import com.sdpcheck.sdpcheck.domain.device.dto.request.PushTokenRequest;
import com.sdpcheck.sdpcheck.domain.device.entity.PushToken;
import com.sdpcheck.sdpcheck.domain.device.exception.PushTokenErrorCode;
import org.springframework.http.HttpStatus;
import com.sdpcheck.sdpcheck.domain.device.repository.PushTokenRepository;
import com.sdpcheck.sdpcheck.domain.device.service.PushTokenService;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushTokenServiceTest {
    private final PushTokenRepository tokens = mock(PushTokenRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final PushTokenService service = new PushTokenService(tokens, members);

    @Test
    void missingMemberFailsBeforeTokenRegistration() {
        assertThatThrownBy(() -> service.addPushToken(1L, new PushTokenRequest("token")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> {
                            assertThat(error.getErrorCode()).isEqualTo(PushTokenErrorCode.MEMBER_NOT_FOUND);
                            assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        });
        verifyNoInteractions(tokens);
    }

    @Test
    void returnsExistingTokenId() {
        when(members.existsById(1L)).thenReturn(true);
        var stored = mock(PushToken.class);
        when(stored.getId()).thenReturn(37L);
        when(tokens.findByToken("token")).thenReturn(Optional.of(stored));
        assertThat(service.addPushToken(1L, new PushTokenRequest("token")).tokenId()).isEqualTo(37L);
    }

    @Test
    void deletionChecksMemberBeforeAccessingTokens() {
        assertThatThrownBy(() -> service.deletePushToken(1L, 37L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(PushTokenErrorCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(tokens);
    }

    @Test
    void deletesOnlyTokenOwnedByMember() {
        when(members.existsById(1L)).thenReturn(true);
        when(tokens.deleteOwnedToken(37L, 1L)).thenReturn(1);
        service.deletePushToken(1L, 37L);
        verify(tokens).deleteOwnedToken(37L, 1L);
    }

    @Test
    void deletionOfMissingOrOtherMembersTokenFails() {
        when(members.existsById(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deletePushToken(1L, 37L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(PushTokenErrorCode.PUSH_TOKEN_NOT_FOUND));
    }
}
