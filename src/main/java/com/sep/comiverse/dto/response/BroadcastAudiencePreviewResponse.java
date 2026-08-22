package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.BroadcastAudienceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastAudiencePreviewResponse {
    private BroadcastAudienceType audienceType;
    private String audienceLabel;
    private long matchedRecipientCount;
    private long enabledRecipientCount;
    private long optedOutCount;
}
