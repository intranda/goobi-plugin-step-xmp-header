# Goobi Plugin für das Schreiben von XMP Metadaten in Bilddateien

## Einführung

Die vorliegende Dokumentation beschreibt die Installation, die Konfiguration und den Einsatz des XMP Plugins in Goobi.

## Installation

Das Plugin muss in den folgenden Ordner installiert werden:

```bash
 /opt/digiverso/goobi/plugins/step/plugin_intranda_step_xmp.jar
```

Daneben gibt es eine Konfigurationsdatei, die an folgender Stelle liegen muss:

```bash
 /opt/digiverso/goobi/config/plugin_write-xmp.xml
```

## Konfiguration

Die Konfiguration erfolgt über die Konfigurationsdatei plugin_write-xmp.xml und kann im laufenden Betrieb angepasst werden.

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
                <!-- type: - type of the field, staticText, metadata or docstruct, default is metadata -->
                <type>metadata</type>
                <!-- name: - name of the metadata field -->
                <name>PlaceOfPublication</name>
                <!-- use: - default is logical , use value from the physical, logical, anchor, current, page docstruct or from all elements -->
                <use>logical</use> <!-- physical|logical|anchor|page|current|all-->
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
        </imageMetadataField>
    </config>
</config_plugin>

```

Der *&lt;config>* Block ist wiederholbar und kann so in unterschiedlichen Projekten verschiedene Metadaten definieren. Die Unterelemente *&lt;project>* und *&lt;step>* werden zur Prüfung genutzt, ob der vorliegende Block für den aktuellen Schritt genutzt werden soll. Dabei wird zuerst geprüft, ob es einen Eintrag gibt, der sowohl den Projektnamen als auch den Schrittenamen enthält. Ist dies nicht der Fall, wird nach einem Eintrag für durch den * gekennzeichnete, beliebige Projekte und dem verwendeten Schrittenamen gesucht. Wenn dazu ebenfalls kein Eintrag gefunden wurde, erfolgt eine Suche nach dem Projektnamen und beliebigen Schritten, ansonsten greift der default Block, bei dem sowohl *&lt;project>* als auch *&lt;step>* * enthalten.

In den beiden Feldern *&lt;useDerivateFolder>* und *&lt;useMasterFolder>* kann festgelegt werden, ob die Änderungen auf die Dateien im jeweiligen Ordner angewendet werden sollen. Mindestens einer der beiden Werte muss auf *true* gesetzt sein.

Mittels *&lt;command>* wird das Tool definiert, das zum schreiben der Daten verwendet wird. Hier können je nach Betriebssystem oder verwendeter Dateiformate unterschiedliche Tools verwendet werden.

Das Element *&lt;parameter>* definiert die einzelnen Parameter, die beim Aufruf des Tools übergeben werden. Dabei können zwei Variablen verwendet werden. Mittels *{FILE}* wird der absolute Pfad zur Datei übergeben, die manipuliert werden soll und {PARAM} enthält die konfigurierten Elemente.

Die einzelnen Felder können Kommasepariert angegeben, dann werden sie beim Aufruf als einzelne Parameter übergeben. Sollen hingegen die Werte der Parameter ',' enthalten, können die Parameter mittels " maskiert werden.

&lt;imageMetadataField> enthält die Konfiguration für ein einzelnes Feld, dass geschrieben werden soll. Um in einem Aufruf mehrere Felder schreiben zu können, ist dieses Feld wiederholbar. Das Attribut *name* ist verpflichtend und enthält den Feldnamen, der geschrieben werden soll.

Innerhalb des Feldes gibt es ein oder mehrere *&lt;goobiField>* Elemente. Diese enthalten die in Goobi verwendeten Metadaten, mit denen das Bildfeld gefüllt werden soll. Für den Fall, dass mehrere *&lt;goobiField>* verwendet wurden, kann ein *&lt;separator>* definiert werden, der die einzelnen Daten trennt. Führende oder endende Leerzeichen müssen unicode masiert mittels \u0020 angegeben werden. Die einzelnen Felder werden in der Reihenfolge hinzugefügt, in der sie konfiguriert wurde.

Jedes *&lt;goobiField>* Element enthält eine Reihe von Unterelementen. Mittels &lt;type> wird festgelegt, um was für ein Element es sich handelt. Mögliche Werte sind staticText, metadata oder docstruct.

### metadata

Wenn es sich um ein metadata Feld handelt, werden eine Reihe von weiteren Unterelementen erwartet.

* *&lt;name>*: enthält den internen Mame des Metadatums
* *&lt;>use>*: definiert, in welchen Strukturelementen nach dem Feld gesucht werden soll.
  * *logical*: die Suche ist auf das Hauptelement wie Monographie oder Band beschränkt. Hier stehen üblicherweise die Daten aus dem OPAC.
  * *anchor*: die Suche ist auf den anchor wie Mehrbändiges Werk oder Zeitschrift beschränkt.
  * *physical*: die Suche ist auf das Element physSequence beschränkt. Hier kann zum Beispiel die URN des Werkes gefunden werden.
  * *current*: die Suche wird nur in dem Element durchgeführt, dass dem aktuellen Bild zugeordet wurde und in der Hierarchie am tiefsten liegt. Zum Beispiel ein Kapitel oder ein Artikel.
  * *page*: die Suche wird nur innerhalb des page Elements durchgeführt. Hier stehen üblicherweise die granulare URNs oder die physische und logische Seitennummer
  * *all*: die Suche wird in allen logischen Elementen durchgeführt, die dem Bild zugeordnet sind. Dabei wird mit dem höchsten begonnen.
* *&lt;separator>*: die hier konfigurierten Zeichen werden als Separator genutzt, falls mehr als ein Eintrag gefunden wurde.
* *&lt;useFirst>*: enthält dieses Feld den Wert *true*, wird der erste gefundene Wert genommen, ansonsetn wird nach weiteren Werten gesucht
* *&lt;staticPrefix>*: dieser Text wird vor den Metadateninhalt gesetzt
* *&lt;staticSuffix>*: dieser Text wird an den Metadateninhalt angehängt.

Sofern es sich bei dem Metadatum um eine Person handelt, wird der Wert aus displayName genutzt, ansonsten der normale value.

### docstruct

Bei docstruct werden folgende Felder erwartet:

* *&lt;language>*: hiermit wird definiert, in welcher Sprache der Name der Strukturelemente geschrieben werden soll. Fehlt die Angabe, wird der interne Name genutzt.
* *&lt;>use>*: definiert, welches Strukturelement genutzt werden soll.
  * *first*: Nutze das erste dem Bild zugeordnete Element. Üblicherweise ist dies die Monographie oder der Band
  * *last*: Nutze das Element, dass dem aktuellen Bild zugeordet wurde und in der Hierarchie am tiefsten liegt. Zum Beispiel ein Kapitel oder ein Artikel.
  * *all*: Nutze alle zugewiesenen Strukturelemente, beginnend mit dem höchsten Element.
* *&lt;separator>*: Dieser Separator wird bei der Verwendung von *all* zur Trennung der einzelnen Strukturelemente genutzt.

### staticText

Bei der Verwendung von staticText wird ein zusätzliches Feld *&lt;text>* erwartet. Dessen Inhalt wird unverändert übernommen.

## Nutzung in Goobi

In Goobi muss das Plugin *write-xmp* in den Schritten ausgewählt werden, in denen die Bildmetadaten geschrieben werden sollen. Da das Schreiben der Metadaten auf den Daten der METS Datei beruht und eine fertige Paginierung und Strukutrierung erwartet, sollte das Schreiben der Bilddaten erst nach der Metadatenbearbeitung passieren.