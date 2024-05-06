package de.intranda.goobi.plugins.step.xmp.util;

import lombok.Data;

@Data
public class DocstructField implements IMetadataField {

    private String language = "";
    private String use;
    private String separator = ";";

}
