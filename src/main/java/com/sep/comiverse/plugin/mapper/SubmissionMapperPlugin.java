package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.SubmissionDTO;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.repository.IComicRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class SubmissionMapperPlugin extends AbstractMapperPlugin<SubmissionEntity, SubmissionDTO, UUID> {

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IComicRepository comicRepository;

    private static class UserInfo {
        final String displayName;
        final String email;
        UserInfo(String displayName, String email) {
            this.displayName = displayName;
            this.email = email;
        }
    }

    private final java.util.Map<UUID, List<String>> comicGenresCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, UserInfo> userCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public SubmissionMapperPlugin(ModelMapper modelMapper) {
        super(SubmissionEntity.class, SubmissionDTO.class, UUID.class, modelMapper);
    }

    @Override
    public SubmissionDTO toDto(SubmissionEntity model) {
        SubmissionDTO dto = super.toDto(model);
        if (dto != null && dto.getComicId() != null) {
            List<String> genreNames = comicGenresCache.computeIfAbsent(dto.getComicId(), 
                id -> comicRepository.findGenreNamesByComicId(id));
            dto.setGenres(genreNames);
        }
        if (dto != null && dto.getSubmittedBy() != null) {
            String subBy = dto.getSubmittedBy();
            UserInfo cachedInfo = userCache.get(subBy);
            if (cachedInfo != null) {
                dto.setSubmittedBy(cachedInfo.displayName);
                dto.setSubmittedByEmail(cachedInfo.email);
            } else {
                resolveAndCacheUser(subBy, dto);
            }
        }
        if (dto != null && dto.getModeratorId() != null) {
            String modIdStr = dto.getModeratorId().toString();
            UserInfo cachedInfo = userCache.get(modIdStr);
            if (cachedInfo != null) {
                dto.setModeratorName(cachedInfo.displayName);
            } else {
                resolveAndCacheModerator(dto.getModeratorId(), dto);
            }
        }
        return dto;
    }

    private void resolveAndCacheModerator(UUID modId, SubmissionDTO dto) {
        var user = userRepository.findById(modId).orElse(null);
        if (user != null) {
            String displayName = user.getFullName() != null && !user.getFullName().trim().isEmpty() 
                ? user.getFullName() 
                : user.getUsername();
            UserInfo info = new UserInfo(displayName, user.getEmail());
            userCache.put(modId.toString(), info);
            dto.setModeratorName(displayName);
        } else {
            userCache.put(modId.toString(), new UserInfo("Unknown Moderator", null));
            dto.setModeratorName("Unknown Moderator");
        }
    }

    private void resolveAndCacheUser(String subBy, SubmissionDTO dto) {
        try {
            UUID userId = UUID.fromString(subBy);
            var user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                String displayName = user.getFullName() != null && !user.getFullName().trim().isEmpty() 
                    ? user.getFullName() 
                    : user.getUsername();
                UserInfo info = new UserInfo(displayName, user.getEmail());
                userCache.put(subBy, info);
                dto.setSubmittedBy(displayName);
                dto.setSubmittedByEmail(user.getEmail());
            } else {
                userCache.put(subBy, new UserInfo(subBy, null));
            }
        } catch (IllegalArgumentException e) {
            if (subBy.startsWith("Author: ")) {
                String possibleUuid = subBy.substring(8);
                try {
                    UUID userId = UUID.fromString(possibleUuid);
                    var user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        String displayName = "Author: " + (user.getFullName() != null && !user.getFullName().trim().isEmpty() 
                            ? user.getFullName() 
                            : user.getUsername());
                        UserInfo info = new UserInfo(displayName, user.getEmail());
                        userCache.put(subBy, info);
                        dto.setSubmittedBy(displayName);
                        dto.setSubmittedByEmail(user.getEmail());
                    } else {
                        userCache.put(subBy, new UserInfo(subBy, null));
                    }
                } catch (IllegalArgumentException ex) {
                    userCache.put(subBy, new UserInfo(subBy, null));
                }
            } else {
                final String lookup = subBy;
                var userList = userRepository.findByUsernameOrFullNameIgnoreCase(lookup);
                var user = userList.isEmpty() ? null : userList.get(0);
                if (user != null) {
                    String displayName = user.getFullName() != null && !user.getFullName().trim().isEmpty()
                        ? user.getFullName()
                        : user.getUsername();
                    UserInfo info = new UserInfo(displayName, user.getEmail());
                    userCache.put(subBy, info);
                    dto.setSubmittedBy(displayName);
                    dto.setSubmittedByEmail(user.getEmail());
                } else {
                    userCache.put(subBy, new UserInfo(subBy, null));
                }
            }
        }
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("title", "chapter", "submittedBy", "queueType");
    }
}
