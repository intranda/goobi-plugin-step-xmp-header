package de.intranda.goobi.plugins.step.xmp.util;

import lombok.Data;

@Data
public class MetadataField implements IMetadataField {
    private String name;
    private String use = "logical";
    private String separator = ";";
    private boolean useFirst;
    private String staticPrefix;
    private String staticSuffix;
}
