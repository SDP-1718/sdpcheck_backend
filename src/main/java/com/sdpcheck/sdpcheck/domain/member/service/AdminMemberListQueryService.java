package com.sdpcheck.sdpcheck.domain.member.service;

import com.sdpcheck.sdpcheck.domain.member.dto.response.AdminMemberListResponse;
import com.sdpcheck.sdpcheck.domain.member.repository.AdminMemberListQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminMemberListQueryService {
    public static final int MAX_PAGE_SIZE = 50;
    private final AdminMemberListQueryRepository repository;

    public AdminMemberListQueryService(AdminMemberListQueryRepository repository) {
        this.repository = repository;
    }

    public AdminMemberListResponse getAdminMembers(Integer generation, int page, int size) {
        if ((generation != null && generation < 1) || page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        var rows = repository.findMembers(generation, size + 1, (long) page * size);
        boolean hasNext = rows.size() > size;
        var members = rows.subList(0, Math.min(size, rows.size()));
        return new AdminMemberListResponse(members, page, size, hasNext);
    }
}
