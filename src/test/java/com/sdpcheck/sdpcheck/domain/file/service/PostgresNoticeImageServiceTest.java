package com.sdpcheck.sdpcheck.domain.file.service;

import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PostgresNoticeImageServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PostgresNoticeImageService service = new PostgresNoticeImageService(jdbc);

    @Test
    void rejectsOversizedOrMisidentifiedFileBeforeWritingToDatabase() {
        var tooLarge = new MockMultipartFile("file", "large.png", "image/png",
                new byte[5 * 1024 * 1024 + 1]);
        assertEquals("PAYLOAD_TOO_LARGE", assertThrows(BusinessException.class,
                () -> service.upload(tooLarge, 7L)).getErrorCode().getCode());

        var unsupported = new MockMultipartFile("file", "image.gif", "image/gif",
                new byte[]{'G', 'I', 'F'});
        assertEquals("UNSUPPORTED_MEDIA_TYPE", assertThrows(BusinessException.class,
                () -> service.upload(unsupported, 7L)).getErrorCode().getCode());

        var falsePng = new MockMultipartFile("file", "image.png", "image/png",
                new byte[]{'G', 'I', 'F'});
        assertEquals("INVALID_IMAGE", assertThrows(BusinessException.class,
                () -> service.upload(falsePng, 7L)).getErrorCode().getCode());
        verifyNoInteractions(jdbc);
    }
}
