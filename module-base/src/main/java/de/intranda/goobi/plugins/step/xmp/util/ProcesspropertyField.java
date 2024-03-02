package de.intranda.goobi.plugins.step.xmp.util;

import lombok.Data;

@Data
public class ProcesspropertyField implements IMetadataField {

    private String name;
    private String separator = ";";
    private boolean useFirst;
}
