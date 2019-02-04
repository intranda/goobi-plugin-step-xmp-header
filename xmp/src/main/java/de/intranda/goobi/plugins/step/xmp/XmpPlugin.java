package de.intranda.goobi.plugins.step.xmp;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.intranda.goobi.plugins.step.xmp.util.DocstructField;
import de.intranda.goobi.plugins.step.xmp.util.IMetadataField;
import de.intranda.goobi.plugins.step.xmp.util.ImageMetadataField;
import de.intranda.goobi.plugins.step.xmp.util.MetadataField;
import de.intranda.goobi.plugins.step.xmp.util.StaticText;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.ShellScript;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.dl.Reference;
import ugh.exceptions.UGHException;

@Log4j
@PluginImplementation

public class XmpPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "write-xmp";

    @Getter
    private PluginType type = PluginType.Step;

    @Getter
    private PluginGuiType pluginGuiType = PluginGuiType.NONE;

    @Getter
    private Step step;

    private Process process;

    private Config config;

    private List<Path> derivateImages;
    private List<Path> masterImages;

    private DocStruct logical = null;
    private DocStruct anchor = null;
    private DocStruct physical = null;

    private Prefs prefs;

    @Override
    public void initialize(Step step, String returnPath) {

        this.step = step;
        process = step.getProzess();

        String projectName = step.getProzess().getProjekt().getTitel();

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;

        // order of configuration is:
        // 1.) project name and step name matches
        // 2.) step name matches and project is *
        // 3.) project name matches and step name is *
        // 4.) project name and step name are *
        try {
            myconfig = xmlConfig.configurationAt("/config[./project = '" + projectName + "'][./step = '" + step.getTitel() + "']");
        } catch (IllegalArgumentException e) {
            try {
                myconfig = xmlConfig.configurationAt("/config[./project = '*'][./step = '" + step.getTitel() + "']");
            } catch (IllegalArgumentException e1) {
                try {
                    myconfig = xmlConfig.configurationAt("/config[./project = '" + projectName + "'][./step = '*']");
                } catch (IllegalArgumentException e2) {
                    myconfig = xmlConfig.configurationAt("/config[./project = '*'][./step = '*']");
                }
            }
        }

        config = initConfig(myconfig);

        try {
            if (config.isUseDerivateFolder()) {
                derivateImages = StorageProvider.getInstance().listFiles(process.getImagesTifDirectory(false), NIOFileUtils.imageNameFilter);
            } else {
                derivateImages = null;
            }
            if (config.isUseMasterFolder()) {
                masterImages = StorageProvider.getInstance().listFiles(process.getImagesOrigDirectory(false), NIOFileUtils.imageNameFilter);
            } else {
                masterImages = null;
            }
        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }

    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public int getInterfaceVersion() {
        return 1;
    }

    @Override
    public PluginReturnValue run() {

        if (!config.isUseDerivateFolder() && !config.isUseMasterFolder()) {
            // don't write derivates and master files
            return PluginReturnValue.FINISH;
        }
        prefs = process.getRegelsatz().getPreferences();
        Fileformat fileformat = null;
        logical = null;
        anchor = null;
        physical = null;
        List<DocStruct> pages = null;
        try {
            fileformat = process.readMetadataFile();

            logical = fileformat.getDigitalDocument().getLogicalDocStruct();
            if (logical.getType().isAnchor()) {
                anchor = logical;
                logical = logical.getAllChildren().get(0);
            }
            physical = fileformat.getDigitalDocument().getPhysicalDocStruct();
            if (physical != null) {
                pages = physical.getAllChildren();
            }
        } catch (UGHException | IOException | InterruptedException | SwapException | DAOException e) {
            // cannot read metadata, error
            log.error(e);
            return PluginReturnValue.ERROR;
        }

        if (pages == null || pages.isEmpty()) {
            // no pages referenced in mets file, error
            return PluginReturnValue.ERROR;
        }

        // check size of metadata and images in folder

        if (config.isUseMasterFolder()) {
            if (pages.size() != masterImages.size()) {
                // size in folder and mets file don't match, error
                return PluginReturnValue.ERROR;
            }
            writeMetadataToImages(pages, masterImages);
        }

        if (config.isUseDerivateFolder()) {
            if (pages.size() != derivateImages.size()) {
                // size in folder and mets file don't match, error
                return PluginReturnValue.ERROR;
            }
            writeMetadataToImages(pages, derivateImages);
        }

        return PluginReturnValue.FINISH;
    }

    private void writeMetadataToImages(List<DocStruct> pages, List<Path> images) {

        for (int i = 0; i < pages.size(); i++) {
            DocStruct page = pages.get(i);
            Path image = images.get(i);

            List<String> xmpFields = new ArrayList<>();
            for (ImageMetadataField xmpFieldConfiguration : config.getConfiguredFields()) {
                StringBuilder sb = new StringBuilder();
                sb.append(xmpFieldConfiguration.getXmpName());
                sb.append("=");
                StringBuilder completeValue = new StringBuilder();
                for (IMetadataField configuredField : xmpFieldConfiguration.getFieldList()) {
                    StringBuilder fieldValue = new StringBuilder();
                    if (configuredField instanceof DocstructField) {
                        DocstructField docstructField = (DocstructField) configuredField;

                        String language = docstructField.getLanguage();
                        String use = docstructField.getUse();

                        List<Reference> pageReferences = page.getAllFromReferences();
                        if (pageReferences == null || pageReferences.isEmpty()) {
                            continue;
                        }
                        if (use.equals("first")) {
                            DocStruct ds = pageReferences.get(0).getSource();
                            fieldValue.append(ds.getType().getNameByLanguage(language));
                        } else if (use.equals("last")) {
                            DocStruct ds = pageReferences.get(pageReferences.size() - 1).getSource();
                            fieldValue.append(ds.getType().getNameByLanguage(language));
                        } else {
                            for (Reference ref : pageReferences) {
                                // if its not first entry, add separator value
                                if (fieldValue.length() != 0) {
                                    fieldValue.append(docstructField.getSeparator());
                                }
                                fieldValue.append(ref.getSource().getType().getNameByLanguage(language));

                            }
                        }
                        // if its not first entry, add separator value
                        if (completeValue.length() > 0) {
                            completeValue.append(xmpFieldConfiguration.getSeparator());
                        }
                        completeValue.append(fieldValue.toString());

                    } else if (configuredField instanceof MetadataField) {
                        MetadataField metadataField = (MetadataField) configuredField;
                        String name = metadataField.getName();
                        MetadataType mdt = prefs.getMetadataTypeByName(name);
                        if (mdt == null) {
                            // TODO error?
                            return;
                        }
                        String value = null;
                        List<Reference> pageReferences = page.getAllFromReferences();

                        switch (metadataField.getUse()) {
                            case "physical":
                                value = getMetadataValue(mdt, physical, metadataField.isUseFirst(), metadataField.getSeparator());
                                break;
                            case "page":
                                value = getMetadataValue(mdt, page, metadataField.isUseFirst(), metadataField.getSeparator());
                                break;
                            case "logical":
                                value = getMetadataValue(mdt, logical, metadataField.isUseFirst(), metadataField.getSeparator());
                                break;
                            case "anchor":
                                if (anchor != null) {
                                    value = getMetadataValue(mdt, anchor, metadataField.isUseFirst(), metadataField.getSeparator());
                                }
                                break;
                            case "current":
                                if (pageReferences == null || pageReferences.isEmpty()) {
                                    break;
                                }
                                DocStruct ds = pageReferences.get(pageReferences.size() - 1).getSource();
                                value = getMetadataValue(mdt, ds, metadataField.isUseFirst(), metadataField.getSeparator());
                                // deepest in hierarchy
                                break;
                            default:
                                //  any/all
                                if (pageReferences == null || pageReferences.isEmpty()) {
                                    break;
                                }
                                StringBuilder metadata = new StringBuilder();
                                for (Reference ref : pageReferences) {
                                    String metadataValue = getMetadataValue(mdt, ref.getSource(), metadataField.isUseFirst(), metadataField
                                            .getSeparator());
                                    if (metadata.length() != 0) {
                                        metadata.append(metadataField.getSeparator());
                                    }
                                    metadata.append(metadataValue);
                                }
                                value = metadata.toString();

                                break;

                        }
                        if (StringUtils.isNotBlank(value)) {
                            if (fieldValue.length() != 0) {
                                fieldValue.append(metadataField.getSeparator());
                            }
                            if (StringUtils.isNotBlank(metadataField.getStaticPrefix())) {
                                fieldValue.append(metadataField.getStaticPrefix());
                            }
                            fieldValue.append(value);
                            if (StringUtils.isNotBlank(metadataField.getStaticSuffix())) {
                                fieldValue.append(metadataField.getStaticSuffix());
                            }
                        }

                        // if its not first entry, add separator value
                        if (completeValue.length() > 0) {
                            completeValue.append(xmpFieldConfiguration.getSeparator());
                        }
                        completeValue.append(fieldValue.toString());

                    } else if (configuredField instanceof StaticText) {
                        StaticText staticText = (StaticText) configuredField;
                        if (completeValue.length() > 0) {
                            completeValue.append(xmpFieldConfiguration.getSeparator());
                        }
                        completeValue.append(staticText.getText());
                    }

                }
                sb.append(completeValue);
                xmpFields.add(sb.toString());
            }

            List<String> parameterList = new ArrayList<>();
            for (String tok : config.getParameter()) {
                if ("{PARAM}".equals(tok)) {
                    for (String field : xmpFields) {
                        parameterList.add(field);
                    }
                } else if ("{FILE}".equals(tok)) {
                    parameterList.add(image.toString());
                } else {
                    parameterList.add(tok);
                }
            }
            //            `["exiftool", "-overwrite_original", "-q", "-q", "-m", "-sep", ", ", "-xmp:location={}".format(location), "-xmp:Creator={}".format(photog), "-xmp:Description={}".format(im_caption), "-xmp:Subject={}".format(im_keywords),''
            try {
                ShellScript s = new ShellScript(Paths.get(config.getCommand()));
                int returnValue = s.run(parameterList);

                if (returnValue != 0) {
                    log.error(s.getStdErr());
                    return;
                }
            } catch (IOException | InterruptedException e) {
                log.error(e);
            }

        }

    }

    private String getMetadataValue(MetadataType metadataType, DocStruct docstruct, boolean useFirst, String separator) {
        StringBuilder result = new StringBuilder();
        if (metadataType.getIsPerson()) {
            // get person value
            // get all persons
            List<Person> personList = docstruct.getAllPersonsByType(metadataType);
            if (personList != null && !personList.isEmpty()) {
                for (Person person : personList) {
                    // get display name
                    String value = person.getDisplayname();
                    if (StringUtils.isNotBlank(value)) {
                        // if useFirst, return value
                        if (useFirst) {
                            return value;
                        } else {
                            // otherwise add it to result list
                            if (result.length() > 0) {
                                result.append(separator);
                            }
                            result.append(value);

                        }
                    }
                }
            }

        } else {
            // get metadata value
            List<? extends Metadata> metadataList = docstruct.getAllMetadataByType(metadataType);

            if (metadataList != null && !metadataList.isEmpty()) {
                for (Metadata metadata : metadataList) {
                    String value = metadata.getValue();
                    if (StringUtils.isNotBlank(value)) {
                        // if useFirst, return value
                        if (useFirst) {
                            return value;
                        } else {
                            // otherwise add it to result list
                            if (result.length() > 0) {
                                result.append(separator);
                            }
                            result.append(value);
                        }
                    }
                }
            }
        }
        return result.toString();
    }

    @Override
    public String cancel() {
        return null;
    }

    @Override
    public boolean execute() {
        run();
        return false;
    }

    @Override
    public String finish() {
        return null;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    /**
     * reads configfile and sets object variables accordingly, sets defaults for some settings if no value is specified
     * 
     * @param myconfig SubnodeConfiguration object of the config file
     */
    private Config initConfig(SubnodeConfiguration xmlconfig) {
        Config config = new Config();

        config.setUseDerivateFolder(xmlconfig.getBoolean("useDerivateFolder", false));
        config.setUseMasterFolder(xmlconfig.getBoolean("useMasterFolder", false));

        List<SubnodeConfiguration> metadataFields = xmlconfig.configurationsAt("/imageMetadataField");

        config.setCommand(xmlconfig.getString("command"));
        config.setParameter(xmlconfig.getList("parameter"));

        // read xmp fields
        for (SubnodeConfiguration fieldElement : metadataFields) {
            String name = fieldElement.getString("./@name");
            String fieldSeparator = fieldElement.getString("./separator", " ").replace("\\u0020", " ");

            ImageMetadataField imageMetadataField = new ImageMetadataField();
            imageMetadataField.setXmpName(name);
            imageMetadataField.setSeparator(fieldSeparator);
            config.addField(imageMetadataField);

            // read field configuration

            List<SubnodeConfiguration> goobiFieldElements = fieldElement.configurationsAt("/goobiField");

            for (SubnodeConfiguration goobiFieldElement : goobiFieldElements) {

                switch (goobiFieldElement.getString("./type", "metadata")) {
                    case "metadata":
                        MetadataField metadataField = new MetadataField();
                        metadataField.setName(goobiFieldElement.getString("./name"));
                        metadataField.setUse(goobiFieldElement.getString("./use", "logical"));
                        metadataField.setSeparator(goobiFieldElement.getString("./separator", " ").replace("\\u0020", " "));
                        metadataField.setUseFirst(goobiFieldElement.getBoolean("./useFirst", true));
                        metadataField.setStaticPrefix(goobiFieldElement.getString("./staticPrefix", "").replace("\\u0020", " "));
                        metadataField.setStaticSuffix(goobiFieldElement.getString("./staticSuffix", "").replace("\\u0020", " "));
                        imageMetadataField.addField(metadataField);
                        break;

                    case "docstruct":

                        DocstructField docStructField = new DocstructField();
                        docStructField.setLanguage(goobiFieldElement.getString("./language", ""));
                        docStructField.setSeparator(goobiFieldElement.getString("./separator", " ").replace("\\u0020", " "));
                        docStructField.setUse(goobiFieldElement.getString("./use", "last"));
                        imageMetadataField.addField(docStructField);

                        break;

                    case "staticText":
                        StaticText text = new StaticText();
                        text.setText(goobiFieldElement.getString("./text"));
                        imageMetadataField.addField(text);
                        break;
                }
            }
        }

        return config;
    }

}
