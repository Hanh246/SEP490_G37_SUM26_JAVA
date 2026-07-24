package com.sep.comiverse.repository.projection;

import java.util.UUID;

public interface ComicChapterCountProjection {
    UUID getComicId();
    Long getChapterCount();
}
