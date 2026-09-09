package com.limelight.preferences;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class SettingsPanelContractTest {
    @Test public void everyPersistentSettingHasAnEntryOrAnExplicitReplacement() throws Exception {
        Set<String> mapped = new HashSet<>();
        for (SettingsCatalog.Group group : SettingsCatalog.GROUPS) {
            for (SettingsCatalog.Section section : group.sections) {
                for (String key : section.keys) assertTrue("Duplicate entry: " + key, mapped.add(key));
            }
        }
        Set<String> replacements = new HashSet<>(Arrays.asList(SettingsCatalog.MERGED_OR_RETIRED));
        Set<String> xmlKeys = new HashSet<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList elements = factory.newDocumentBuilder().parse(new File("src/main/res/xml/preferences.xml"))
                .getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (element.getTagName().equals("PreferenceScreen") || element.getTagName().equals("PreferenceCategory")) continue;
            String key = element.getAttribute("android:key");
            assertFalse("Preference needs a stable key: " + element.getTagName(), key.isEmpty());
            xmlKeys.add(key);
            assertTrue("Unreachable setting: " + key, mapped.contains(key) || replacements.contains(key));
        }
        mapped.remove("performance_overlay_mode");
        assertTrue("Navigation references an unknown setting", xmlKeys.containsAll(mapped));
        assertTrue("Stale replacement entry", xmlKeys.containsAll(replacements));
    }

    @Test public void overlayModeRespectsMasterSwitchAndExistingLiteDefault() {
        assertEquals(0, SettingsCatalog.overlayMode(false, false));
        assertEquals(0, SettingsCatalog.overlayMode(false, true));
        assertEquals(1, SettingsCatalog.overlayMode(true, true));
        assertEquals(2, SettingsCatalog.overlayMode(true, false));
    }

    @Test public void bitrateInputUsesMbpsWithoutLosingPrecision() {
        assertEquals(20500, SettingsValueParser.parseNumber("20.5", 1000, 500, 300000));
        assertEquals(20500, SettingsValueParser.parseNumber("20,5", 1000, 500, 300000));
        assertEquals(500, SettingsValueParser.parseNumber("0.5", 1000, 500, 300000));
        assertEquals(300000, SettingsValueParser.parseNumber("300", 1000, 500, 300000));
    }

    @Test public void manualBitrateSupports99999MbpsAndRejectsOverflow() {
        assertEquals(99999000, SettingsValueParser.parseBitrateMbps("99999"));
        assertEquals(20500, SettingsValueParser.parseBitrateMbps("20.5"));
        assertEquals(500, SettingsValueParser.parseBitrateMbps("0.5"));
        for (String input : new String[] {"100000", "99999.001", "-1", "0", "2147483647", "NaN", ""}) {
            assertThrows(input, IllegalArgumentException.class, () -> SettingsValueParser.parseBitrateMbps(input));
        }
    }

    @Test public void invalidInputCannotReachPersistentSettings() {
        for (String input : new String[] {"", "NaN", "Infinity", "0.499", "300.001", "-20", "1e20", "0.5001"}) {
            assertThrows(input, IllegalArgumentException.class,
                    () -> SettingsValueParser.parseNumber(input, 1000, 500, 300000));
        }
    }

    @Test public void customResolutionAcceptsLandscapePortraitAndMultiplicationSign() {
        assertEquals("1920x1080", SettingsValueParser.parseResolution("1920 X 1080"));
        assertEquals("1080x1920", SettingsValueParser.parseResolution("1080×1920"));
        assertEquals("3840x1200", SettingsValueParser.parseResolution("3840x1200"));
        for (String input : new String[] {"0x1080", "1921x1080", "1920x", "1920x1080x2", "17000x1080", "abc"}) {
            assertThrows(input, IllegalArgumentException.class, () -> SettingsValueParser.parseResolution(input));
        }
    }
}
