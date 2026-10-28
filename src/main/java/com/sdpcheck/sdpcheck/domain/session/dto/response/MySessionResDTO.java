package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.session.enums.MySessionState;
import java.time.OffsetDateTime;

public record MySessionResDTO(
		MySessionState state,
		SessionDetailResDTO session,
		boolean checkedIn,
		AttendanceStatus attendanceStatus,
		OffsetDateTime checkedInAt
) {

	public static MySessionResDTO none() {
		return new MySessionResDTO(MySessionState.NONE, null, false, null, null);
	}
}
