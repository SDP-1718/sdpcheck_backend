package com.sdpcheck.sdpcheck.domain.notice.dto.response;

import java.util.List;

public record NoticeListResponse(NoticeSummary pinnedNotice, List<NoticeSummary> items) {
}
