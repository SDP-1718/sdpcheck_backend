package com.sdpcheck.sdpcheck.domain.member.service;

import com.sdpcheck.sdpcheck.domain.member.dto.response.AdminMemberListItem;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.AdminMemberListQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminMemberListQueryServiceTest {
    private final AdminMemberListQueryRepository repository = mock(AdminMemberListQueryRepository.class);
    private final AdminMemberListQueryService service = new AdminMemberListQueryService(repository);

    @Test
    void limitsThePageAndUsesTheExtraRowOnlyForHasNext() {
        var first = item(1L, 18, Role.ADMIN);
        var second = item(2L, 18, Role.MEMBER);
        var extra = item(3L, 18, Role.MEMBER);
        when(repository.findMembers(18, 3, 4L)).thenReturn(List.of(first, second, extra));

        var response = service.getAdminMembers(18, 2, 2);

        assertEquals(List.of(first, second), response.members());
        assertEquals(2, response.page());
        assertEquals(2, response.size());
        assertTrue(response.hasNext());
        verify(repository).findMembers(18, 3, 4L);
    }

    @Test
    void anUnfilteredFullLastPageDoesNotClaimAnotherPage() {
        var first = item(1L, 19, Role.ADMIN);
        var second = item(2L, 17, Role.MEMBER);
        when(repository.findMembers(null, 3, 0L)).thenReturn(List.of(first, second));

        var response = service.getAdminMembers(null, 0, 2);

        assertEquals(List.of(first, second), response.members());
        assertFalse(response.hasNext());
        verify(repository).findMembers(null, 3, 0L);
    }

    @Test
    void returnsAnEmptyPageWithoutInventingMembers() {
        when(repository.findMembers(99, 21, 60L)).thenReturn(List.of());

        var response = service.getAdminMembers(99, 3, 20);

        assertEquals(List.of(), response.members());
        assertEquals(3, response.page());
        assertEquals(20, response.size());
        assertFalse(response.hasNext());
    }

    @Test
    void calculatesLargeOffsetsWithoutIntegerOverflow() {
        when(repository.findMembers(null, 51, 107374182350L)).thenReturn(List.of());

        var response = service.getAdminMembers(null, Integer.MAX_VALUE, 50);

        assertEquals(Integer.MAX_VALUE, response.page());
        assertEquals(50, response.size());
        assertFalse(response.hasNext());
        verify(repository).findMembers(null, 51, 107374182350L);
    }

    @ParameterizedTest
    @MethodSource("invalidParameters")
    void invalidParametersFailBeforeTheRepository(Integer generation, int page, int size) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getAdminMembers(generation, page, size));

        assertEquals(CommonErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    void repositoryFailureIsNotTurnedIntoAnEmptySuccessfulList() {
        var failure = new IllegalStateException("storage unavailable");
        when(repository.findMembers(18, 21, 0L)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.getAdminMembers(18, 0, 20)));
    }

    private static Stream<Arguments> invalidParameters() {
        return Stream.of(Arguments.of(0, 0, 20), Arguments.of(-1, 0, 20),
                Arguments.of(null, -1, 20), Arguments.of(18, 0, 0), Arguments.of(18, 0, -1),
                Arguments.of(null, 0, 51), Arguments.of(18, 0, Integer.MAX_VALUE));
    }

    private static AdminMemberListItem item(long id, int generation, Role role) {
        return new AdminMemberListItem(id, "회원 " + id, generation, role);
    }
}
