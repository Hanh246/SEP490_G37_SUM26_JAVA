package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.SubmissionDTO;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import com.sep.comiverse.repository.IUserRepository;
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
    public SubmissionMapperPlugin(ModelMapper modelMapper) {
        super(SubmissionEntity.class, SubmissionDTO.class, UUID.class, modelMapper);
    }

    @Override
    public SubmissionDTO toDto(SubmissionEntity model) {
        SubmissionDTO dto = super.toDto(model);
        if (dto != null && dto.getSubmittedBy() != null) {
            String subBy = dto.getSubmittedBy();
            try {
                UUID userId = UUID.fromString(subBy);
                var user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    dto.setSubmittedBy(user.getFullName() != null && !user.getFullName().trim().isEmpty() 
                        ? user.getFullName() 
                        : user.getUsername());
                    dto.setSubmittedByEmail(user.getEmail());
                }
            } catch (IllegalArgumentException e) {
                // Check if it's "Author: <UUID>"
                if (subBy.startsWith("Author: ")) {
                    String possibleUuid = subBy.substring(8);
                    try {
                        UUID userId = UUID.fromString(possibleUuid);
                        var user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                            dto.setSubmittedBy("Author: " + (user.getFullName() != null && !user.getFullName().trim().isEmpty() 
                                ? user.getFullName() 
                                : user.getUsername()));
                            dto.setSubmittedByEmail(user.getEmail());
                        }
                    } catch (IllegalArgumentException ex) {
                        // Keep original "Author: ..."
                    }
                } else {
                    // Try looking up by username/fullname for mock data (like moderator1, author1)
                    final String lookup = subBy;
                    var user = userRepository.findAll().stream()
                        .filter(u -> u.getUsername().equalsIgnoreCase(lookup) || (u.getFullName() != null && u.getFullName().equalsIgnoreCase(lookup)))
                        .findFirst()
                        .orElse(null);
                    if (user != null) {
                        dto.setSubmittedByEmail(user.getEmail());
                    }
                }
            }
        }
        return dto;
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("title", "chapter", "submittedBy", "queueType");
    }
}
