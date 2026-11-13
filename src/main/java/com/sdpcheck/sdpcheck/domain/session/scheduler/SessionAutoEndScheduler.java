package com.sdpcheck.sdpcheck.domain.session.scheduler;

import com.sdpcheck.sdpcheck.domain.session.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "session.auto-end.enabled", havingValue = "true", matchIfMissing = true)
public class SessionAutoEndScheduler {

	private final SessionService sessionService;

	public SessionAutoEndScheduler(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@Scheduled(fixedDelayString = "${session.auto-end.interval:PT30S}")
	public void endExpiredSessions() {
		try {
			int ended = sessionService.autoEndExpiredSessions();
			if (ended > 0) {
				log.info("Auto-ended {} session(s) after the check-in window closed", ended);
			}
		} catch (RuntimeException exception) {
			log.error("Failed to auto-end expired sessions", exception);
		}
	}
}
