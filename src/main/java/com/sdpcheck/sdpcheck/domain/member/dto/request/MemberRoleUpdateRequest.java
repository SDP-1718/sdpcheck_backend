package com.sdpcheck.sdpcheck.domain.member.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import jakarta.validation.constraints.NotNull;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record MemberRoleUpdateRequest(
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonDeserialize(using = StrictRoleDeserializer.class) Role role
) {
    public static final class StrictRoleDeserializer extends ValueDeserializer<Role> {
        @Override
        public Role deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (Role) context.handleUnexpectedToken(Role.class, parser);
            }
            String value = parser.getString();
            try {
                return Role.valueOf(value);
            } catch (IllegalArgumentException exception) {
                return (Role) context.handleWeirdStringValue(
                        Role.class, value, "role must be ADMIN or MEMBER");
            }
        }
    }
}
