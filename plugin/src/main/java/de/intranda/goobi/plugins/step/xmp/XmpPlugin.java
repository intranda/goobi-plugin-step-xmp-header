package de.intranda.goobi.plugins.step.xmp;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.LogEntry;
import org.goobi.beans.Masterpiece;
import org.goobi.beans.Masterpieceproperty;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Step;
import org.goobi.beans.Template;
import org.goobi.beans.Templateproperty;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.intranda.goobi.plugins.step.xmp.util.DocstructField;
import de.intranda.goobi.plugins.step.xmp.util.FilenameField;
import de.intranda.goobi.plugins.step.xmp.util.IMetadataField;
import de.intranda.goobi.plugins.step.xmp.util.ImageMetadataField;
import de.intranda.goobi.plugins.step.xmp.util.MetadataField;
import de.intranda.goobi.plugins.step.xmp.util.ProcesspropertyField;
import de.intranda.goobi.plugins.step.xmp.util.StaticText;
import de.intranda.goobi.plugins.step.xmp.util.TemplatepropertyField;
import de.intranda.goobi.plugins.step.xmp.util.VariableField;
import de.intranda.goobi.plugins.step.xmp.util.WorkpiecepropertyField;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.ShellScript;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
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
    private String title = "intranda_step_xmp_header";

    @Getter
    private PluginType type = PluginType.Step;

    @Getter
    private PluginGuiType pluginGuiType = PluginGuiType.NONE;

    @Getter
    private Step step;

    private Process process;

    private Config config;

    private Config defaultConfig;

    private DocStruct logical = null;
    private DocStruct anchor = null;
    private DocStruct physical = null;

    private DigitalDocument digDoc;

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

        SubnodeConfiguration fallbackconfig = null;

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
            fallbackconfig = xmlConfig.configurationAt("/config[@type = 'default']");
            defaultConfig = initConfig(fallbackconfig);
        } catch (IllegalArgumentException e) {
            log.debug("No default block configured");
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

        if (config.getFolders() == null || config.getFolders().size() == 0) {
            // don't write any images
        	writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: There are no image folders configured to use for writing.");
            return PluginReturnValue.ERROR;
        }
        prefs = process.getRegelsatz().getPreferences();
        // prefs not readable
        if (prefs == null) {
            writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: The ruleset is not valid.");
            return PluginReturnValue.ERROR;
        }

        Fileformat fileformat = null;
        logical = null;
        anchor = null;
        physical = null;
        List<DocStruct> pages = null;
        try {
            // read metadata
            fileformat = process.readMetadataFile();
            digDoc = fileformat.getDigitalDocument();
            logical = digDoc.getLogicalDocStruct();
            anchor = logical;
            if (logical.getType().isAnchor()) {
                logical = logical.getAllChildren().get(0);
            }
            physical = digDoc.getPhysicalDocStruct();
            if (physical != null) {
                pages = physical.getAllChildren();
            }
        } catch (UGHException | IOException | InterruptedException | SwapException | DAOException e) {
            // cannot read metadata, error
            writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: Cannot read metadata from METS file.");
            log.error("Error while writing the XMP headers: Cannot read metadata from METS file for process with ID " + process.getId(), e);
            return PluginReturnValue.ERROR;
        }

        if (pages == null || pages.isEmpty()) {
            // no pages referenced in mets file, error
            writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: No pages are referenced in mets file.");
            return PluginReturnValue.ERROR;
        }

        // check size of each folder to use
        for (String f : config.getFolders()) {
            try {
                String folderName = step.getProzess().getConfiguredImageFolder(f);
                List<Path> images = StorageProvider.getInstance().listFiles(process.getConfiguredImageFolder(f), NIOFileUtils.imageNameFilter);

                if (pages.size() != images.size()) {
                    if (defaultConfig == null) {
                        // size in folder and mets file don't match, error
                        writeLogEntry(LogType.ERROR,
                                "Error while writing the XMP headers: Different number of objects in folder '" + folderName + "' and in mets file. Default configuration is null.");
                        return PluginReturnValue.ERROR;
                    } else {
                        if (!writeDefaultMetadataToImages(images)) {
                            writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: Different number of objects in folder '" + folderName
                                    + "' and in mets file. Default metadata could not be written.");
                            return PluginReturnValue.ERROR;
                        }
                    }
                } else if (!writeMetadataToImages(pages, images)) {
                    return PluginReturnValue.ERROR;
                }
            } catch (IOException | InterruptedException | SwapException | DAOException e) {
                writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: Error while writing metadata into images folder: " + e.getMessage());
                log.error("Error while writing the XMP headers: Error while writing metadata into images folder for process with ID " + process.getId(), e);
                return PluginReturnValue.ERROR;
            }
        }
        return PluginReturnValue.FINISH;
    }

    /**
     * Write the configured default fields to all images. The metadata can be taken only from the main structure element
     * 
     * @param images list of image names
     */
    private boolean writeDefaultMetadataToImages(List<Path> images) {

        for (Path image : images) {

            List<String> xmpFields = new ArrayList<>();
            // handle different xmp fields
            for (ImageMetadataField xmpFieldConfiguration : defaultConfig.getConfiguredFields()) {
                StringBuilder sb = new StringBuilder();
                sb.append(xmpFieldConfiguration.getXmpName());
                sb.append("=");
                StringBuilder completeValue = new StringBuilder();
                for (IMetadataField configuredField : xmpFieldConfiguration.getFieldList()) {
                    StringBuilder fieldValue = new StringBuilder();

                    if (configuredField instanceof DocstructField) {
                        DocstructField docstructField = (DocstructField) configuredField;
                        String language = docstructField.getLanguage();
                        fieldValue.append(logical.getType().getNameByLanguage(language));
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
                            writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: Cannot find metadata type " + name);
                            return false;
                        }
                        String value = null;

                        switch (metadataField.getUse()) {
                            case "physical":
                                // get metadata from physical main element (physical location)
                                value = getMetadataValue(mdt, physical, metadataField.isUseFirst(), metadataField.getSeparator());
                                break;
                            case "logical":
                                // get metadata from top element (main title)
                                value = getMetadataValue(mdt, logical, metadataField.isUseFirst(), metadataField.getSeparator());
                                break;
                            case "anchor":
                                // get metadata from anchor element (publisher)
                                if (anchor != null) {
                                    value = getMetadataValue(mdt, anchor, metadataField.isUseFirst(), metadataField.getSeparator());
                                }
                                break;
                        }
                        if (StringUtils.isNotBlank(value)) {
                            if (fieldValue.length() != 0) {
                                fieldValue.append(metadataField.getSeparator());
                            }
                            // add prefix
                            if (StringUtils.isNotBlank(metadataField.getStaticPrefix())) {
                                fieldValue.append(metadataField.getStaticPrefix());
                            }
                            // add element
                            fieldValue.append(value);
                            // add suffix
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
                        getStaticTextConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof ProcesspropertyField) {
                        getProcessPropertyConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof TemplatepropertyField) {
                        getTemplatePropertyConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof WorkpiecepropertyField) {
                        getWorkpiecePropertyConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof FilenameField) {
                        getFilenameFieldConfiguration(image, xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof VariableField) {
                        getVariableFieldConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    }
                }
                sb.append(completeValue);
                xmpFields.add(sb.toString());
            }
            // get configured parameter list, replace PARAM and FILE with actual values
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
                // run script for current image
                ShellScript s = new ShellScript(Paths.get(config.getCommand()));
                int returnValue = s.run(parameterList);

                if (returnValue != 0) {
                    List<String> errors = s.getStdErr();
                    writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: " + errors.toString());
                    log.error("Error while writing the XMP headers for process with ID " + process.getId() + ": " + errors);
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                log.error(e);
            }

        }
        writeLogEntry(LogType.INFO, "Writing the XMP headers: Default metadata was written into the images.");
        return true;
    }

    /**
     * Write the configured fields to all pages. The metadata is collected for each page individually
     * 
     * @param pages list of docstruct elements of all pages
     * @param images list of image names
     */
    private boolean writeMetadataToImages(List<DocStruct> pages, List<Path> images) {

        for (int i = 0; i < pages.size(); i++) {
            DocStruct page = pages.get(i);
            Path image = images.get(i);

            List<String> xmpFields = new ArrayList<>();
            // handle different xmp fields
            for (ImageMetadataField xmpFieldConfiguration : config.getConfiguredFields()) {
                StringBuilder sb = new StringBuilder();
                sb.append(xmpFieldConfiguration.getXmpName());
                sb.append("=");
                StringBuilder completeValue = new StringBuilder();
                for (IMetadataField configuredField : xmpFieldConfiguration.getFieldList()) {
                    StringBuilder fieldValue = new StringBuilder();
                    // get information from docstructs
                    if (configuredField instanceof DocstructField) {

                        getDocstructConfiguration(xmpFieldConfiguration, fieldValue, configuredField, completeValue, page);

                    } else if (configuredField instanceof MetadataField) {
                        getMetadataConfiguration(fieldValue, xmpFieldConfiguration, completeValue, configuredField, page);
                    } else if (configuredField instanceof StaticText) {
                        getStaticTextConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof ProcesspropertyField) {
                        getProcessPropertyConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof TemplatepropertyField) {
                        getTemplatePropertyConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof WorkpiecepropertyField) {
                        getWorkpiecePropertyConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof FilenameField) {
                        getFilenameFieldConfiguration(image, xmpFieldConfiguration, completeValue, configuredField);
                    } else if (configuredField instanceof VariableField) {
                        getVariableFieldConfiguration(xmpFieldConfiguration, completeValue, configuredField);
                    }
                }
                sb.append(completeValue);
                xmpFields.add(sb.toString());
            }
            // get configured parameter list, replace PARAM and FILE with actual values
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
                // run script for current image
                ShellScript s = new ShellScript(Paths.get(config.getCommand()));
                int returnValue = s.run(parameterList);

                if (returnValue != 0) {
                    List<String> errors = s.getStdErr();
                    writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: " + errors.toString());
                    log.error("Error while writing the XMP headers for process with ID " + process.getId() + ": " + errors);
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                log.error(e);
            }

        }
        writeLogEntry(LogType.INFO, "Writing the XMP headers: The metadata was written into the images.");
        return true;
    }

    private void getDocstructConfiguration(ImageMetadataField xmpFieldConfiguration, StringBuilder fieldValue, IMetadataField configuredField,
            StringBuilder completeValue, DocStruct page) {
        DocstructField docstructField = (DocstructField) configuredField;

        String language = docstructField.getLanguage();
        String use = docstructField.getUse();
        // abort if page is not assigned to any docstruct
        List<Reference> pageReferences = page.getAllFromReferences();
        if (pageReferences == null || pageReferences.isEmpty()) {
            return;
        }
        if (use.equals("first")) {
            // use first (probably main) element
            DocStruct ds = pageReferences.get(0).getSource();
            fieldValue.append(ds.getType().getNameByLanguage(language));
        } else if (use.equals("last")) {
            // use last element
            DocStruct ds = pageReferences.get(pageReferences.size() - 1).getSource();
            fieldValue.append(ds.getType().getNameByLanguage(language));
        } else {
            // use all referenced elements
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
    }

    private void getMetadataConfiguration(StringBuilder fieldValue, ImageMetadataField xmpFieldConfiguration, StringBuilder completeValue,
            IMetadataField configuredField, DocStruct page) {
        MetadataField metadataField = (MetadataField) configuredField;
        String name = metadataField.getName();
        MetadataType mdt = prefs.getMetadataTypeByName(name);
        if (mdt == null) {
            writeLogEntry(LogType.ERROR, "Error while writing the XMP headers: Cannot find metadata type " + name);
            return;
        }
        String value = null;

        List<Reference> pageReferences = null;
        if (page != null) {
            pageReferences = page.getAllFromReferences();
        }

        switch (metadataField.getUse()) {
            case "physical":
                // get metadata from physical main element (physical location)
                value = getMetadataValue(mdt, physical, metadataField.isUseFirst(), metadataField.getSeparator());
                break;
            case "page":
                if (page != null) {
                    // get metadata from physical page element (urn)
                    value = getMetadataValue(mdt, page, metadataField.isUseFirst(), metadataField.getSeparator());
                }
                break;
            case "logical":
                // get metadata from top element (main title)
                value = getMetadataValue(mdt, logical, metadataField.isUseFirst(), metadataField.getSeparator());
                break;
            case "anchor":
                // get metadata from anchor element (publisher)
                if (anchor != null) {
                    value = getMetadataValue(mdt, anchor, metadataField.isUseFirst(), metadataField.getSeparator());
                }
                break;
            case "current":
                if (page != null) {
                    // get metadata from last element (chapter title)
                    if (pageReferences == null || pageReferences.isEmpty()) {
                        break;
                    }
                    DocStruct ds = pageReferences.get(pageReferences.size() - 1).getSource();
                    value = getMetadataValue(mdt, ds, metadataField.isUseFirst(), metadataField.getSeparator());
                    // deepest in hierarchy
                }
                break;
            case "last":
                if (pageReferences == null || pageReferences.isEmpty()) {
                    break;
                }
                for (Reference ref : pageReferences) {
                    String metadataValue = getMetadataValue(mdt, ref.getSource(), metadataField.isUseFirst(), metadataField.getSeparator());
                    if (!StringUtils.isBlank(metadataValue)) {
                        value = metadataValue;
                    }
                }
                break;
            default:
                if (page != null) {
                    //  any/all
                    // get metadata from all logical elements
                    if (pageReferences == null || pageReferences.isEmpty()) {
                        break;
                    }
                    StringBuilder metadata = new StringBuilder();
                    for (Reference ref : pageReferences) {
                        String metadataValue = getMetadataValue(mdt, ref.getSource(), metadataField.isUseFirst(), metadataField.getSeparator());
                        if (metadata.length() != 0) {
                            metadata.append(metadataField.getSeparator());
                        }
                        metadata.append(metadataValue);
                    }
                    value = metadata.toString();
                }
                break;
        }
        if (StringUtils.isNotBlank(value)) {
            if (fieldValue.length() != 0) {
                fieldValue.append(metadataField.getSeparator());
            }
            // add prefix
            if (StringUtils.isNotBlank(metadataField.getStaticPrefix())) {
                fieldValue.append(metadataField.getStaticPrefix());
            }
            // add element
            fieldValue.append(value);
            // add suffix
            if (StringUtils.isNotBlank(metadataField.getStaticSuffix())) {
                fieldValue.append(metadataField.getStaticSuffix());
            }
        }

        // if its not first entry, add separator value
        if (completeValue.length() > 0) {
            completeValue.append(xmpFieldConfiguration.getSeparator());
        }
        completeValue.append(fieldValue.toString());
    }

    private void getStaticTextConfiguration(ImageMetadataField xmpFieldConfiguration, StringBuilder completeValue, IMetadataField configuredField) {
        StaticText staticText = (StaticText) configuredField;
        if (completeValue.length() > 0) {
            completeValue.append(xmpFieldConfiguration.getSeparator());
        }
        completeValue.append(staticText.getText());
    }

    private void getProcessPropertyConfiguration(ImageMetadataField xmpFieldConfiguration, StringBuilder completeValue,
            IMetadataField configuredField) {
        ProcesspropertyField field = (ProcesspropertyField) configuredField;

        StringBuilder subValue = new StringBuilder();
        for (Processproperty prop : process.getEigenschaften()) {
            if (prop.getTitel().equals(field.getName())) {
                if (subValue.length() > 0) {
                    subValue.append(field.getSeparator());
                }
                subValue.append(prop.getWert());
                if (field.isUseFirst()) {
                    break;
                }
            }
        }
        if (subValue.length() > 0) {
            if (completeValue.length() > 0) {
                completeValue.append(xmpFieldConfiguration.getSeparator());
            }
            completeValue.append(subValue.toString());
        }
    }

    private void getTemplatePropertyConfiguration(ImageMetadataField xmpFieldConfiguration, StringBuilder completeValue,
            IMetadataField configuredField) {
        TemplatepropertyField field = (TemplatepropertyField) configuredField;
        StringBuilder subValue = new StringBuilder();
        if (process.getVorlagen() != null) {
            for (Template template : process.getVorlagen()) {
                for (Templateproperty prop : template.getEigenschaften()) {
                    if (prop.getTitel().equals(field.getName())) {
                        if (subValue.length() > 0) {
                            subValue.append(field.getSeparator());
                        }
                        subValue.append(prop.getWert());
                        if (field.isUseFirst()) {
                            break;
                        }
                    }

                }
            }
            if (subValue.length() > 0) {
                if (completeValue.length() > 0) {
                    completeValue.append(xmpFieldConfiguration.getSeparator());
                }
                completeValue.append(subValue.toString());
            }
        }
    }

    private void getWorkpiecePropertyConfiguration(ImageMetadataField xmpFieldConfiguration, StringBuilder completeValue,
            IMetadataField configuredField) {
        WorkpiecepropertyField field = (WorkpiecepropertyField) configuredField;
        StringBuilder subValue = new StringBuilder();
        if (process.getWerkstuecke() != null) {
            for (Masterpiece workpiece : process.getWerkstuecke()) {
                for (Masterpieceproperty prop : workpiece.getEigenschaften()) {
                    if (prop.getTitel().equals(field.getName())) {
                        if (subValue.length() > 0) {
                            subValue.append(field.getSeparator());
                        }
                        subValue.append(prop.getWert());
                        if (field.isUseFirst()) {
                            break;
                        }
                    }

                }
            }
            if (subValue.length() > 0) {
                if (completeValue.length() > 0) {
                    completeValue.append(xmpFieldConfiguration.getSeparator());
                }
                completeValue.append(subValue.toString());
            }
        }
    }

    private void getVariableFieldConfiguration(ImageMetadataField xmpFieldConfiguration, StringBuilder completeValue,
            IMetadataField configuredField) {
        VariableField field = (VariableField) configuredField;

        String value = new VariableReplacer(digDoc, prefs, process, step).replace(field.getName());
        if (StringUtils.isNotBlank(value)) {
            if (completeValue.length() > 0) {
                completeValue.append(xmpFieldConfiguration.getSeparator());
            }
            completeValue.append(value);
        }
    }

    private void getFilenameFieldConfiguration(Path image, ImageMetadataField xmpFieldConfiguration, StringBuilder completeValue,
            IMetadataField configuredField) {
        FilenameField field = (FilenameField) configuredField;
        if (completeValue.length() > 0) {
            completeValue.append(xmpFieldConfiguration.getSeparator());
        }
        if (field.isUseAbsolutePath()) {
            completeValue.append(image.toString());
        } else {
            completeValue.append(image.getFileName().toString());
        }
    }

    /**
     * Get metadata value for a given metadata type from a docstruct. The metadataType can be a person or a simple metadata. If no metadata with this
     * type is found, an empty String is returned
     * 
     * @param metadataType {@link MetadataType} the type
     * @param docstruct {@link DocStruct} current docstruct
     * @param useFirst stop after first occurrence or use all
     * @param separator use this character to separate entries, if useFirst is set to false
     * @return
     */
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

        config.setFolders(Arrays.asList(xmlconfig.getStringArray("folder")));
        List<HierarchicalConfiguration> metadataFields = xmlconfig.configurationsAt("/imageMetadataField");

        config.setCommand(xmlconfig.getString("command"));
        config.setParameter(Arrays.asList(xmlconfig.getStringArray("parameter")));

        // read xmp fields
        for (HierarchicalConfiguration fieldElement : metadataFields) {
            String name = fieldElement.getString("./@name");
            String fieldSeparator = fieldElement.getString("./separator", " ").replace("\\u0020", " ");

            ImageMetadataField imageMetadataField = new ImageMetadataField();
            imageMetadataField.setXmpName(name);
            imageMetadataField.setSeparator(fieldSeparator);
            config.addField(imageMetadataField);

            // read field configuration

            List<HierarchicalConfiguration> goobiFieldElements = fieldElement.configurationsAt("/goobiField");

            for (HierarchicalConfiguration goobiFieldElement : goobiFieldElements) {
                // metadata block
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
                    // docstruct
                    case "docstruct":

                        DocstructField docStructField = new DocstructField();
                        docStructField.setLanguage(goobiFieldElement.getString("./language", ""));
                        docStructField.setSeparator(goobiFieldElement.getString("./separator", " ").replace("\\u0020", " "));
                        docStructField.setUse(goobiFieldElement.getString("./use", "last"));
                        imageMetadataField.addField(docStructField);
                        break;
                    // static text
                    case "staticText":
                        StaticText text = new StaticText();
                        text.setText(goobiFieldElement.getString("./text"));
                        imageMetadataField.addField(text);
                        break;

                    case "processproperty":
                        ProcesspropertyField field = new ProcesspropertyField();
                        field.setName(goobiFieldElement.getString("./name"));
                        field.setSeparator(goobiFieldElement.getString("./separator", " ").replace("\\u0020", " "));
                        field.setUseFirst(goobiFieldElement.getBoolean("./useFirst", true));
                        imageMetadataField.addField(field);
                        break;

                    case "templateproperty":
                        TemplatepropertyField templ = new TemplatepropertyField();
                        templ.setName(goobiFieldElement.getString("./name"));
                        templ.setSeparator(goobiFieldElement.getString("./separator", " ").replace("\\u0020", " "));
                        templ.setUseFirst(goobiFieldElement.getBoolean("./useFirst", true));
                        imageMetadataField.addField(templ);
                        break;

                    case "workpieceproperty":
                        WorkpiecepropertyField work = new WorkpiecepropertyField();
                        work.setName(goobiFieldElement.getString("./name"));
                        work.setSeparator(goobiFieldElement.getString("./separator", " ").replace("\\u0020", " "));
                        work.setUseFirst(goobiFieldElement.getBoolean("./useFirst", true));
                        imageMetadataField.addField(work);
                        break;

                    case "filename":
                        FilenameField filenameField = new FilenameField();
                        filenameField.setUseAbsolutePath(goobiFieldElement.getBoolean("./useAbsolutePath", true));
                        imageMetadataField.addField(filenameField);
                        break;

                    case "variable":
                        VariableField variableField = new VariableField();
                        variableField.setName(goobiFieldElement.getString("./value"));
                        imageMetadataField.addField(variableField);
                        break;
                }
            }
        }

        return config;
    }

    /**
     * write log entry in case of errors
     * 
     * @param type {@link LogType}
     * @param text Text is added as content
     */
    private void writeLogEntry(LogType type, String text) {
        LogEntry logEntry = new LogEntry();
        logEntry.setContent(text);
        logEntry.setProcessId(process.getId());
        logEntry.setType(type);
        logEntry.setCreationDate(new Date());
        logEntry.setUserName("Write XMP header plugin");
        ProcessManager.saveLogEntry(logEntry);
    }

}
