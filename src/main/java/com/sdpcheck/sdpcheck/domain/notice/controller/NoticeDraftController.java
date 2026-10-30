package com.sdpcheck.sdpcheck.domain.notice.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.notice.dto.request.GenerateNoticeDraftRequest;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeDraftResponse;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeDraftService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notices/ai-drafts")
@SecurityRequirement(name = "bearerAuth")
public class NoticeDraftController {
    private final NoticeDraftService service;

    public NoticeDraftController(NoticeDraftService service) {
        this.service = service;
    }

    @Operation(operationId = "generateNoticeDraft", summary = "관리자 공지 초안 생성 (목업)",
            description = "메모를 검증한 뒤 고정 제목·본문과 MOCK 모드를 반환합니다. 초안을 저장·게시하지 않으며 사용자별 최근 60초간 최대 10회 호출할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "고정 목업 초안 생성", useReturnTypeSchema = true,
                    headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "메모 누락·공백·길이 초과·잘못된 형식",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(
                            name = "validationError", value = "{\"isSuccess\":false,\"code\":\"VALIDATION_ERROR\",\"message\":\"입력값이 올바르지 않습니다.\",\"result\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(
                            name = "unauthenticated", value = "{\"isSuccess\":false,\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\",\"result\":null}")),
                    headers = @Header(name = "WWW-Authenticate", schema = @Schema(type = "string", example = "Bearer"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음 또는 온보딩 미완료",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(
                            name = "forbidden", value = "{\"isSuccess\":false,\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\",\"result\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "사용자별 요청 횟수 초과",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(
                            name = "rateLimited", value = "{\"isSuccess\":false,\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"result\":null}")),
                    headers = @Header(name = "Retry-After", schema = @Schema(type = "integer", minimum = "1", example = "60"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(
                            name = "internalError", value = "{\"isSuccess\":false,\"code\":\"INTERNAL_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\",\"result\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "초안 생성 기능 비활성",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(
                            name = "featureUnavailable", value = "{\"isSuccess\":false,\"code\":\"FEATURE_UNAVAILABLE\",\"message\":\"일시적으로 서비스를 이용할 수 없습니다.\",\"result\":null}")))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<NoticeDraftResponse>> generate(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody GenerateNoticeDraftRequest request) {
        requireAdmin(member);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "NOTICE_DRAFT_CREATED", "공지 초안이 생성되었습니다.",
                        service.generate(member.memberId())));
    }

    private static void requireAdmin(AuthenticatedMember member) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHENTICATED);
        }
        if (member.role() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN);
        }
    }
}
