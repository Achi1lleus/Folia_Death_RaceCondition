package com.foliarace;

import com.foliarace.modules.CraftingDupe;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class FoliaRaceAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Folia Race");

    @Override
    public void onInitialize() {
        Modules.get().add(new CraftingDupe());
    }

    @Override
    public String getPackage() {
        return "com.foliarace";
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }
}

