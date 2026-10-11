package com.tyes.civilisation.client.dashboard;

import com.tyes.civilisation.network.DemonstrateRecipePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Bounded RecipeManager-backed browser for an optional, server-validated physical demonstration. */
public final class RecipeBrowserScreen extends Screen {
    private static final String[] CATEGORIES = {"All", "Crafting", "Smelting", "Blasting", "Smoking"};
    private final CompoundTag data;
    private EditBox search;
    private Button categoryButton;
    private int categoryIndex;
    private int panelX, panelY, panelW, panelH, scroll;

    public RecipeBrowserScreen(CompoundTag data) {
        super(Component.literal("Demonstrate a Recipe")); this.data = data.copy();
    }

    @Override protected void init() {
        panelW = Math.min(560, width - 24); panelH = Math.min(360, height - 24);
        panelX = (width - panelW) / 2; panelY = (height - panelH) / 2;
        int searchWidth = Math.max(80, panelW - 238);
        search = new EditBox(font, panelX + 18, panelY + 42, searchWidth, 20,
                Component.literal("Search recipes")); search.setHint(Component.literal("Search recipes..."));
        addRenderableWidget(search);
        int categoryX = panelX + 24 + searchWidth;
        categoryButton = addRenderableWidget(Button.builder(Component.literal("Category: All"), b -> cycleCategory())
                .bounds(categoryX, panelY + 42, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(panelX + panelW - 66, panelY + 42, 50, 20).build());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.flush();
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0xFF3A281C);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFFF0D9A4);
        g.drawString(font, "Physical Recipe Demonstration", panelX + 18, panelY + 15, 0xFF2E241A, false);
        g.drawString(font, "Consumes the shown real ingredients and teaches only this citizen.",
                panelX + 18, panelY + 27, 0xFF6C5740, false);
        int x = panelX + 18, y = panelY + 72 - scroll;
        g.enableScissor(panelX + 12, panelY + 68, panelX + panelW - 12, panelY + panelH - 12);
        for (CompoundTag recipe : filtered()) {
            boolean hover = mouseX >= x && mouseX < panelX + panelW - 18 && mouseY >= y && mouseY < y + 38;
            if (hover) g.fill(x - 3, y - 2, panelX + panelW - 18, y + 36, 0x33FFFFFF);
            ItemStack output = stack(recipe.getCompound("output"));
            g.renderItem(output, x, y + 3); g.renderItemDecorations(font, output, x, y + 3);
            String name = output.isEmpty() ? recipe.getString("id") : output.getHoverName().getString();
            g.drawString(font, name, x + 23, y + 2, 0xFF2E241A, false);
            g.drawString(font, recipe.getString("level") + " • " + recipe.getInt("points") + " familiarity",
                    x + 23, y + 14, 0xFF6C5740, false);
            int ix = x + 23;
            for (Tag raw : recipe.getList("ingredients", Tag.TAG_COMPOUND)) {
                ItemStack ingredient = stack((CompoundTag) raw); g.renderItem(ingredient, ix, y + 22); ix += 18;
            }
            y += 42;
        }
        g.disableScissor(); super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int y = panelY + 72 - scroll;
        for (CompoundTag recipe : filtered()) {
            if (mouseX >= panelX + 18 && mouseX < panelX + panelW - 18 && mouseY >= y && mouseY < y + 38) {
                ResourceLocation id = ResourceLocation.tryParse(recipe.getString("id"));
                if (id != null) PacketDistributor.sendToServer(new DemonstrateRecipePayload(data.getUUID("citizen"), id));
                return true;
            }
            y += 42;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int)(scrollY * 24)); return true;
    }
    @Override public boolean isPauseScreen() { return false; }

    private void cycleCategory() {
        categoryIndex = (categoryIndex + 1) % CATEGORIES.length;
        categoryButton.setMessage(Component.literal("Category: " + CATEGORIES[categoryIndex]));
        scroll = 0;
    }

    private List<CompoundTag> filtered() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        List<CompoundTag> result = new ArrayList<>(); ListTag recipes = data.getList("recipes", Tag.TAG_COMPOUND);
        for (Tag raw : recipes) { CompoundTag recipe = (CompoundTag) raw; ItemStack output = stack(recipe.getCompound("output"));
            String haystack = (recipe.getString("id") + " " + output.getHoverName().getString()).toLowerCase(Locale.ROOT);
            boolean categoryMatches = categoryIndex == 0 || CATEGORIES[categoryIndex].equals(recipe.getString("category"));
            if (categoryMatches && (query.isEmpty() || haystack.contains(query))) result.add(recipe); }
        return result;
    }

    private ItemStack stack(CompoundTag tag) {
        return minecraft.level == null ? ItemStack.EMPTY : ItemStack.parseOptional(minecraft.level.registryAccess(), tag);
    }
}
