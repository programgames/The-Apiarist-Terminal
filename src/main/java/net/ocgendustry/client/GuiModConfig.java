package net.ocgendustry.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.ocgendustry.Config;
import net.ocgendustry.OCGendustryMod;

import java.util.ArrayList;
import java.util.List;

public class GuiModConfig extends GuiConfig {
    public GuiModConfig(GuiScreen parent) {
        super(parent, getElements(), OCGendustryMod.MODID, false, false, "The Apiarist Terminal Config");
    }

    private static List<IConfigElement> getElements() {
        List<IConfigElement> list = new ArrayList<>();

        if (Config.config != null) {
            // Add categories themselves so they show as top-level pages
            list.add(new ConfigElement(Config.config.getCategory(Config.CAT_GENERAL)));
            list.add(new ConfigElement(Config.config.getCategory(Config.CAT_ADV_MUTATRON)));
            list.add(new ConfigElement(Config.config.getCategory(Config.CAT_APIARY)));
            list.add(new ConfigElement(Config.config.getCategory(Config.CAT_PROCESSORS)));
            list.add(new ConfigElement(Config.config.getCategory(Config.CAT_INTEGRATION)));
        }

        return list;
    }
}
