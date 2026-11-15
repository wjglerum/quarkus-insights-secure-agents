package nl.lunatech.insights.api.dto;

import nl.lunatech.insights.api.domain.Bookmark;

import java.time.OffsetDateTime;

public record BookmarkDto(Long id, Long sessionId, OffsetDateTime createdAt, SessionDto session) {
    public static BookmarkDto of(Bookmark b) {
        return new BookmarkDto(b.id, b.session.id, b.createdAt, SessionDto.of(b.session));
    }
}
