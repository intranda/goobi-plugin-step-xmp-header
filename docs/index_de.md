---
title: Schreiben von XMP Metadaten in Bilddateien
identifier: intranda_step_xmp_header
published: true
description: Dies ist die technische Dokumentation für das Goobi-Plugin zum Schreiben von XMP Metadaten in Bilddateien.
---
## Einführung
Die vorliegende Dokumentation beschreibt die Installation, die Konfiguration und den Einsatz des XMP-Metadaten Plugins in Goobi. Dieses Plugin ermöglicht es, verschiedenste Metadaten aus Goobi in die XMP-Header von Bilddateien zu schreiben. Dabei können sowohl Metadaten auf Werk-Ebene als auch Metadaten auf Bild-Ebene (wie zum Beispiel Zugehörigkeit zu Strukturelementen) geschrieben werden.


## Installation
Zur Nutzung des Plugins muss es an den folgenden Ort kopiert werden:

```bash
/opt/digiverso/goobi/plugins/step/plugin-intranda-step-xmp-header-base.jar
```

## Überblick und Funktionsweise
In Goobi muss das Plugin `write-xmp` in den Schritten ausgewählt werden, in denen die Bildmetadaten geschrieben werden sollen. Außerdem sollten diese Schritte als `automatisch`markiert werden, damit sie nicht manuell angestoßen werden müssen. Da das Schreiben der Metadaten auf den Daten der METS Datei beruht und eine fertige Paginierung und Strukutrierung erwartet, sollte das Schreiben der XMP-Header erst nach der Metadatenbearbeitung passieren.

![Konfiguration des Schritts in Goobi Workflow](screen1.png)


## Konfiguration
Die Konfigurationsdatei wird unter folgendem Pfad erwartet:

```bash
/opt/digiverso/goobi/config/plugin_intranda_step_xmp_header.xml
```

Diese Konfigurationsdatei kann im laufenden Betrieb angepasst werden. Eine Beispielkonfiguration, die alle Möglichkeiten des Plugins zeigt, ist hier zu sehen:

```xml
<config_plugin>

    <config>
        <!-- define for which project this configuration block is used. Is repeatable if the same configuration shall be used on different project, * means any project. -->
        <project>*</project>
        <!-- define for which step name this configuration block is used. Is repeatable if the same configuration shall be used on different steps, * means any step. -->
        <step>*</step>
        <!-- define if the images in master folder are used -->
        <useDerivateFolder>true</useDerivateFolder>
        <!-- define if the images in master folder are used -->
        <useMasterFolder>false</useMasterFolder>
        <!-- call this command to write the metadata into the image-->
        <command>/usr/bin/exiftool</command>
        <!-- Each parameter is comma separated, double quotation " can be used.
        {PARAM} is replaced by the list of fields, {FILE} is replaced with the current file -->
        <parameter>-overwrite_original, -q, -q, -m, -sep, {PARAM}, {FILE}</parameter>
        <!-- @name - xmp field name -->
        <imageMetadataField name="-xmp:Location">
            <!-- separator - use this to separate the different entries. Default is white space (\u0020) -->
            <separator>;</separator>
            <goobiField>
                <!-- type: - type of the field: templateproperty, workpieceproperty, processproperty, staticText, metadata or docstruct, default is metadata -->
                <type>metadata</type>
                <!-- name: - name of the metadata field -->
                <name>PlaceOfPublication</name>
                <!-- use: - default is logical , use value from the physical, logical, anchor, current, page docstruct, last (lowest in hierarchy that has the metadata) or from all elements -->
                <use>logical</use> <!-- physical|logical|anchor|page|current|last|all-->
                <!-- separator - use this separator to separate the different occurences of a field, default is blank -->
                <separator>;</separator>
                <!-- useFirst: - use only the first occurence or all - default is true -->
                <useFirst>false</useFirst>
                <!-- staticPrefix: - text gets added before the metadata value, leading/trailing white spaces must be encoded \u0020 -->
                <staticPrefix>some static text \u0020</staticPrefix>
                <!-- staticSuffix: - text gets added after the metadata value -->
                <staticSuffix>\u0020additional text</staticSuffix>
            </goobiField>

            <goobiField>
                <type>docstruct</type>
                <!-- language - which language should be used - if not given, the internal names are used -->
                <language>en</language>
                <!-- use - use the first docstruct (usually the monograph), the last docstruct (the deepest in hierarchy) or all together -->
                <use>all</use>
                <separator>;\u0020</separator>
            </goobiField>
            <goobiField>
                <type>staticText</type>
                <text>some example text</text>
            </goobiField>
            <goobiField>
                <type>processproperty</type>
                <name>Physikalischer Standort</name>
                <useFirst>true</useFirst>
                <separator>;</separator>
            </goobiField>
            <goobiField>
                <type>templateproperty</type>
                <name>Artist</name>
                <useFirst>true</useFirst>
                <separator>;</separator>
            </goobiField>
            <goobiField>
                <type>workpieceproperty</type>
                <name>Hersteller der digit. Ausgabe</name>
                <useFirst>true</useFirst>
                <separator>;</separator>
            </goobiField>
        </imageMetadataField>
    </config>
</config_plugin>
```

Der `<config>` Block ist wiederholbar und kann so in unterschiedlichen Projekten verschiedene Metadaten definieren. Die Unterelemente `<project>` und `<step>` werden zur Prüfung genutzt, ob der vorliegende Block für den aktuellen Schritt genutzt werden soll. Dabei wird zuerst geprüft, ob es einen Eintrag gibt, der sowohl den Projektnamen als auch den Schrittenamen enthält. Ist dies nicht der Fall, wird nach einem Eintrag für durch den \* gekennzeichnete, beliebige Projekte und dem verwendeten Schrittenamen gesucht. Wenn dazu ebenfalls kein Eintrag gefunden wurde, erfolgt eine Suche nach dem Projektnamen und beliebigen Schritten, ansonsten greift der default Block, bei dem sowohl `<project>` als auch `<step>` \* enthalten.

In den beiden Feldern `<useDerivateFolder>` und `<useMasterFolder>` kann festgelegt werden, ob die Änderungen auf die Dateien im jeweiligen Ordner angewendet werden sollen. Mindestens einer der beiden Werte muss auf `true` gesetzt sein.

Mittels `<command>` wird das Tool definiert, das zum schreiben der Daten verwendet wird. Hier können je nach Betriebssystem oder verwendeter Dateiformate unterschiedliche Tools verwendet werden.

Das Element `<parameter>` definiert die einzelnen Parameter, die beim Aufruf des Tools übergeben werden. Dabei können zwei Variablen verwendet werden. Mittels `{FILE}` wird der absolute Pfad zur Datei übergeben, die manipuliert werden soll und `{PARAM}` enthält die konfigurierten Elemente.

Die einzelnen Felder können kommasepariert angegeben werden, dann werden sie beim Aufruf als einzelne Parameter übergeben. Sollen hingegen die Werte der Parameter `','` enthalten, können die Parameter mittels `"` maskiert werden.

`<imageMetadataField>` enthält die Konfiguration für ein einzelnes Feld, dass geschrieben werden soll. Um in einem Aufruf mehrere Felder schreiben zu können, ist dieses Feld wiederholbar. Das Attribut `name` ist verpflichtend und enthält den Feldnamen, der geschrieben werden soll.

Innerhalb des Feldes gibt es ein oder mehrere `<goobiField>` Elemente. Diese enthalten die in Goobi verwendeten Metadaten, mit denen das XMP-Feld gefüllt werden soll. Für den Fall, dass mehrere `<goobiField>` verwendet wurden, kann ein `<separator>` definiert werden, der die einzelnen Daten trennt. Führende oder endende Leerzeichen müssen unicode maskiert mittels `\u0020` angegeben werden. Die einzelnen Felder werden in der Reihenfolge hinzugefügt, in der sie konfiguriert wurden.

Jedes `<goobiField>` Element enthält eine Reihe von Unterelementen. Mittels dieser wird festgelegt, um welches Element es sich handelt. Mögliche Werte sind `processproperty`, `templateproperty`, `workpieceproperty`, `staticText`, `metadata` oder `docstruct`.

Die `processproperty`, `templateproperty` und `workpieceproperty`-Eigenschaften werden in der Goobi Datenbank gesucht. Sie enthalten daher immer identische Werte für alle Bilder. Es werden bis zu drei weitere Unterfelder erwartet:

* `<name>`: enthält den Namen der Eigenschaft, dessen Wert genutzt werden soll
* `<useFirst>`: enthält dieses Feld den Wert true, wird der erste gefundene Wert genommen, ansonsetn wird nach weiteren Werten gesucht
* `<separator>`: die hier konfigurierten Zeichen werden als Separator genutzt, falls mehr als ein Eintrag gefunden wurde.

Bei der Verwendung von `staticText` wird ein zusätzliches Feld `<text>` erwartet. Dessen Inhalt wird unverändert übernommen.

Wenn es sich um ein `metadata`-Feld handelt, werden eine Reihe von weiteren Unterelementen erwartet.

* `<name>`: enthält den internen Mame des Metadatums
* `<use>`: definiert, in welchen Strukturelementen nach dem Feld gesucht werden soll. Es sind folgende Werte möglich:
  * `logical`: die Suche ist auf das Hauptelement wie Monographie oder Band beschränkt. Hier stehen üblicherweise die Daten aus dem OPAC.
  * `anchor`: die Suche ist auf den anchor wie Mehrbändiges Werk oder Zeitschrift beschränkt.
  * `physical`: die Suche ist auf das Element physSequence beschränkt. Hier kann zum Beispiel die URN des Werkes gefunden werden.
  * `current`: die Suche wird nur in dem Element durchgeführt, dass dem aktuellen Bild zugeordet wurde und in der Hierarchie am tiefsten liegt. Zum Beispiel ein Kapitel oder ein Artikel.
  * `page`: die Suche wird nur innerhalb des page Elements durchgeführt. Hier stehen üblicherweise die granulare URNs oder die physische und logische Seitennummer
  * `last` : die Suche wird in allen logischen Elementen durchgeführt, die dem Bild zugeordnet sind. Es wird das Metadatum mit der niedrigsten Hierarchiestufe übernommen.
  * `all`: die Suche wird in allen logischen Elementen durchgeführt, die dem Bild zugeordnet sind. Dabei wird mit dem höchsten begonnen. Alle gefundenen Werte werden konkateniert.
* `<separator>`: die hier konfigurierten Zeichen werden als Separator genutzt, falls mehr als ein Eintrag gefunden wurde.
* `<useFirst>`: enthält dieses Feld den Wert `true`, wird der erste gefundene Wert genommen, ansonsetn wird nach weiteren Werten gesucht
* `<staticPrefix>`: dieser Text wird vor den Metadateninhalt gesetzt
* `<staticSuffix>`: dieser Text wird an den Metadateninhalt angehängt.

Sofern es sich bei dem Metadatum um eine Person handelt, wird der Wert aus displayName genutzt, ansonsten der normale Wert.

Bei `docstruct` werden folgende Felder erwartet:

* `<language>`: hiermit wird definiert, in welcher Sprache der Name der Strukturelemente geschrieben werden soll. Fehlt die Angabe, wird der interne Name genutzt.
* `<use>`: definiert, welches Strukturelement genutzt werden soll. Folgende Werte sind hier valide:
  * `first`: Nutze das erste dem Bild zugeordnete Element. Üblicherweise ist dies die Monographie oder der Band
  * `last`: Nutze das Element, dass dem aktuellen Bild zugeordet wurde und in der Hierarchie am tiefsten liegt. Zum Beispiel ein Kapitel oder ein Artikel.
  * `all`: Nutze alle zugewiesenen Strukturelemente, beginnend mit dem höchsten Element.
* `<separator>`: Dieser Separator wird bei der Verwendung von `all` zur Trennung der einzelnen Strukturelemente genutzt.


## Beispiel: Dublin-Core Metadaten in alle Bilder eines Vorgangs schreiben
XMP-Header ermöglichen das Schreiben des vollständigen Dublin-Core Metadatensatzes. Mit folgender Beispielkonfiguration schreibt das Plugin alle Dublin-Core Metadaten aus Goobi heraus in die jeweiligen XMP-Header der Derivate:

```xml
<config_plugin>
    <config>
        <project>*</project>
        <step>*</step>
        <useDerivateFolder>true</useDerivateFolder>
        <useMasterFolder>false</useMasterFolder>
        <command>/usr/bin/exiftool</command>
        <parameter>-overwrite_original, -q, -q, -m, {PARAM}, {FILE}</parameter>
        <!-- creator of the digital document -->
        <imageMetadataField name="-xmp-dc:Contributor">
            <goobiField>
                <type>workpieceproperty</type>
                <name>Artist</name>
                <useFirst>true</useFirst>
            </goobiField>
        </imageMetadataField>
        <!-- list of creators of the book -->
        <imageMetadataField name="-xmp-dc:Creator">
            <separator>;\u0020</separator>
            <goobiField>
                <type>metadata</type>
                <name>Author</name>
                <use>logical</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>
            <goobiField>
                <type>metadata</type>
                <name>Creator</name>
                <use>logical</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>
        </imageMetadataField>
        <!-- must be a valid date format, either YYYY or YYYY-MM-DD -->
        <imageMetadataField name="-xmp-dc:Date">
            <goobiField>
                <type>metadata</type>
                <name>PublicationYearSort</name>
                <use>logical</use>
                <useFirst>true</useFirst>
            </goobiField>
        </imageMetadataField>
        <!-- any text -->
        <imageMetadataField name="-xmp-dc:Description">
            <goobiField>
                <type>metadata</type>
                <name>Description</name>
                <use>logical</use>
                <useFirst>true</useFirst>
            </goobiField>
        </imageMetadataField>
        <!-- image format -->
        <imageMetadataField name="-xmp-dc:Format">
            <goobiField>
                <type>staticText</type>
                <text>image/tiff</text>
            </goobiField>
        </imageMetadataField>
        <!-- identifier of the document, could be goobi id, process title or catalogue id -->
        <imageMetadataField name="-xmp-dc:Identifier">
            <goobiField>
                <type>metadata</type>
                <name>CatalogIDDigital</name>
                <use>logical</use>
                <useFirst>true</useFirst>
            </goobiField>
        </imageMetadataField>
        <!-- all languages -->
        <imageMetadataField name="-xmp-dc:Language">
            <goobiField>
                <type>metadata</type>
                <name>DocLanguage</name>
                <use>all</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>
        </imageMetadataField>
        <!-- publisher name and publication place -->
        <imageMetadataField name="-xmp-dc:Publisher">
            <goobiField>
                <type>metadata</type>
                <name>PublisherName</name>
                <use>all</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>
            <goobiField>
                <type>metadata</type>
                <name>PlaceOfPublication</name>
                <use>all</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>
        </imageMetadataField>
        <!-- TODO remove this field? -->
        <imageMetadataField name="-xmp-dc:Relation">
            <goobiField>
                <type>docstruct</type>
                <language>en</language>
                <use>all</use>
                <separator>;\u0020</separator>
            </goobiField>
        </imageMetadataField>
        <!-- access rights -->
        <imageMetadataField name="-xmp-dc:Rights">
            <goobiField>
                <type>metadata</type>
                <name>AccessLicense</name>
                <use>logical</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>
        </imageMetadataField>
        <!-- TODO only main title or sub titles as well? -->
        <imageMetadataField name="-xmp-dc:Title">
            <goobiField>
                <type>metadata</type>
                <name>TitleDocMain</name>
                <use>logical</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>
        </imageMetadataField>
        <!-- document type = Manuscript or type = Chapter? -->
        <imageMetadataField name="-xmp-dc:Type">
            <goobiField>
                <type>docstruct</type>
                <language>en</language>
                <use>logical</use>
                <separator>;\u0020</separator>
            </goobiField>
        </imageMetadataField>

        <!-- some additional metadata to identify the document -->
        <imageMetadataField name="-xmp-dc:Source">
            <separator>;\u0020</separator>
            <goobiField>
                <type>metadata</type>
                <name>Author</name>
                <use>all</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>
            <goobiField>
                <type>metadata</type>
                <name>TitleDocMain</name>
                <use>logical</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>
            <goobiField>
                <type>metadata</type>
                <name>TitleDocSub1</name>
                <use>all</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>  
            <goobiField>
                <type>metadata</type>
                <name>PublicationYear</name>
                <use>logical</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>  
            <goobiField>
                <type>metadata</type>
                <name>PlaceOfPublication</name>
                <use>logical</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>  
            <goobiField>
                <type>metadata</type>
                <name>PublisherName</name>
                <use>logical</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
            </goobiField>  
            <goobiField>
                <type>metadata</type>
                <name>physPageNumber</name>
                <use>page</use>
                <useFirst>false</useFirst>
                <separator>;\u0020</separator>
                 <staticPrefix>(page\u0020</staticPrefix>
                 <staticSuffix>)</staticSuffix>
            </goobiField>  
            <goobiField>
                <type>filename</type>
                <useAbsolutePath>true</useAbsolutePath>
            </goobiField>
            <goobiField>
                <type>variable</type>
                <value>Goobi-ID {processid}</value>
            </goobiField>
        </imageMetadataField>
    </config>
</config_plugin>
```
