package de.intranda.goobi.plugins.step.xmp.util;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ImageMetadataField {

    private String xmpName;

    private String separator = " ";

    private List<IMetadataField> fieldList = new ArrayList<>();

    public void addField(IMetadataField field) {
        fieldList.add(field);
    }

}
