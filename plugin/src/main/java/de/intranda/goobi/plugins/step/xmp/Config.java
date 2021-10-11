package de.intranda.goobi.plugins.step.xmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.intranda.goobi.plugins.step.xmp.util.ImageMetadataField;
import lombok.Data;

@Data
public class Config {

//    private boolean useMasterFolder;
//    private boolean useDerivateFolder;

    private List<String> folders = new ArrayList<String>();
    
    private String command;
    private List<String> parameter;

    private List<ImageMetadataField> configuredFields = new ArrayList<>();

    public void addField(ImageMetadataField field) {
        configuredFields.add(field);
    }

}
