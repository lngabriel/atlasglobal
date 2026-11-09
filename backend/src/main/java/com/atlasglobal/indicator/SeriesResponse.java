package com.atlasglobal.indicator;

import com.atlasglobal.source.SourceResponse;
import java.util.UUID;

public record SeriesResponse(UUID id, String externalCode, String datasetCode, String originalName,
                             String methodology, String sourceOrganization, String unit,
                             String frequency, SourceResponse source) {}
