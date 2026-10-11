package com.tyes.civilisation.client.dashboard;

import com.tyes.civilisation.dashboard.DashboardView;
import com.tyes.civilisation.network.DashboardRequestPayload;
import com.tyes.civilisation.network.RecipeCatalogRequestPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Original responsive parchment dashboard. It is a read-only view of server snapshots. */
public final class CivilisationDashboardScreen extends Screen {
    private static final int PARCHMENT = 0xFFF0D9A4;
    private static final int PARCHMENT_DARK = 0xFFD0AE70;
    private static final int INK = 0xFF2E241A;
    private static final int MUTED = 0xFF6C5740;
    private static final int ACCENT = 0xFF76492B;
    private static final int ALERT = 0xFF9D332D;
    private CompoundTag snapshot;
    private DashboardView view;
    private int selectedTab;
    private int panelX, panelY, panelW, panelH, contentX, contentY, contentW;
    private int scroll;
    private int refreshTicks;
    private EditBox search;
    private Button demonstrateButton;
    private ItemStack deferredItemTooltip = ItemStack.EMPTY;

    public CivilisationDashboardScreen(CompoundTag snapshot) {
        super(Component.literal("Civilisation Dashboard"));
        this.snapshot = snapshot.copy();
        this.view = parseView(snapshot.getString("view"));
    }

    public boolean accepts(CompoundTag incoming) {
        return incoming.hasUUID("target") && snapshot.hasUUID("target")
                && incoming.getUUID("target").equals(snapshot.getUUID("target"))
                && incoming.getString("view").equals(snapshot.getString("view"));
    }

    public void updateSnapshot(CompoundTag incoming) {
        snapshot = incoming.copy();
        view = parseView(snapshot.getString("view"));
    }

    @Override protected void init() {
        panelW = Math.min(620, Math.max(330, width - 24));
        panelH = Math.min(390, Math.max(230, height - 24));
        panelX = (width - panelW) / 2; panelY = (height - panelH) / 2;
        int tabW = panelW < 430 ? 72 : 94;
        contentX = panelX + tabW + 18; contentY = panelY + 60;
        contentW = panelW - tabW - 32;
        String[] tabs = tabs();
        int y = panelY + 55;
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            addRenderableWidget(Button.builder(Component.literal(tabs[i]), button -> {
                selectedTab = index; scroll = 0; rebuildSearch();
            }).bounds(panelX + 9, y + i * 25, tabW, 21).build());
        }
        if (view == DashboardView.CITIZEN && snapshot.hasUUID("settlementId"))
            addRenderableWidget(Button.builder(Component.literal("Settlement"), b -> request(DashboardView.SETTLEMENT,
                    snapshot.getUUID("settlementId"))).bounds(panelX + panelW - 92, panelY + 12, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("×"), b -> onClose())
                .bounds(panelX + panelW - 19, panelY - 1, 18, 18).build());
        if (view == DashboardView.CITIZEN) {
            demonstrateButton = Button.builder(Component.literal("Demonstrate Recipe"), b ->
                            PacketDistributor.sendToServer(new RecipeCatalogRequestPayload(snapshot.getUUID("target"))))
                    .bounds(panelX + panelW - 142, panelY + panelH - 28, 126, 20).build();
            demonstrateButton.visible = selectedTab == 4; addRenderableWidget(demonstrateButton);
        }
        rebuildSearch();
    }

    private void rebuildSearch() {
        if (search != null) removeWidget(search);
        search = null;
        if (view == DashboardView.SETTLEMENT && selectedTab == 1) {
            search = new EditBox(font, contentX, contentY, Math.min(210, contentW), 18, Component.literal("Search citizens"));
            search.setHint(Component.literal("Search citizens...")); addRenderableWidget(search);
        }
    }

    @Override public void tick() {
        super.tick();
        if (demonstrateButton != null) demonstrateButton.visible = selectedTab == 4;
        if (++refreshTicks >= 100 && snapshot.hasUUID("target")) {
            refreshTicks = 0; request(view, snapshot.getUUID("target"));
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render() normally invokes renderBackground itself. This dashboard
        // draws content before asking Screen to draw its widgets, so allowing that
        // virtual call would run the world blur a second time over our text/bars.
        // Invoke the superclass backdrop exactly once, flush it, then suppress the
        // later virtual call made by Screen.render().
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.flush();
        deferredItemTooltip = ItemStack.EMPTY;
        graphics.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0xFF3A281C);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PARCHMENT);
        graphics.fill(panelX + 4, panelY + 4, panelX + panelW - 4, panelY + 7, PARCHMENT_DARK);
        graphics.fill(panelX + 4, panelY + 43, panelX + panelW - 4, panelY + 45, PARCHMENT_DARK);
        String title = snapshot.getString("name") + (snapshot.getString("readable").isBlank()
                ? "" : " — " + snapshot.getString("readable"));
        graphics.drawString(font, trim(title, panelW - 125), panelX + 14, panelY + 15, INK, false);
        graphics.drawString(font, subtitle(), panelX + 14, panelY + 29, MUTED, false);
        renderContent(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!deferredItemTooltip.isEmpty())
            graphics.renderTooltip(font, deferredItemTooltip, mouseX, mouseY);
    }

    /** Background is rendered explicitly at the start of render(), before all UI content. */
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private String subtitle() {
        return switch (view) {
            case CITIZEN -> join(" • ", snapshot.getString("stage"), snapshot.getString("career"), snapshot.getString("careerLevel"));
            case SETTLEMENT -> snapshot.getString("stage") + " settlement";
            case BUILDING -> join(" • ", titleCase(snapshot.getString("name")), snapshot.getString("condition"));
        };
    }

    private void renderContent(GuiGraphics g, int mouseX, int mouseY) {
        int bottom = panelY + panelH - 12;
        g.enableScissor(contentX - 3, contentY - 3, panelX + panelW - 10, bottom);
        int y = contentY - scroll;
        if (view == DashboardView.CITIZEN) y = renderCitizen(g, y, mouseX, mouseY);
        else if (view == DashboardView.SETTLEMENT) y = renderSettlement(g, y, mouseX, mouseY);
        else y = renderBuilding(g, y, mouseX, mouseY);
        g.disableScissor();
    }

    private int renderCitizen(GuiGraphics g, int y, int mouseX, int mouseY) {
        return switch (selectedTab) {
            case 0 -> {
                y = heading(g, y, "Identity & wellbeing");
                y = line(g, y, "Name", snapshot.getString("name")); y = line(g, y, "Citizen", snapshot.getString("readable"));
                y = line(g, y, "Life stage", titleCase(snapshot.getString("stage")));
                y = line(g, y, "Activity", titleCase(snapshot.getString("activity")));
                y = line(g, y, "Settlement", or(snapshot.getString("settlement"), "None"));
                y = line(g, y, "Household", or(snapshot.getString("household"), "None"));
                y = line(g, y, "Home", or(snapshot.getString("home"), "No home assigned"));
                y = line(g, y, "Career", snapshot.getString("career") + " — " + titleCase(snapshot.getString("careerLevel")));
                y = needBar(g, y + 5, "Hunger", snapshot.getDouble("hunger"));
                yield needBar(g, y + 4, "Fatigue", snapshot.getDouble("fatigue"));
            }
            case 1 -> {
                y = heading(g, y, "Autonomous work"); y = line(g, y, "Career", snapshot.getString("career"));
                y = line(g, y, "Current work", snapshot.getString("work"));
                if (!snapshot.getString("workTarget").isBlank()) y = line(g, y, "Target", snapshot.getString("workTarget"));
                if (!snapshot.getString("workReason").isBlank()) y = wrapped(g, y, "Why: " + snapshot.getString("workReason"), ACCENT);
                if (!snapshot.getString("workPriority").isBlank()) y = line(g, y, "Priority", snapshot.getString("workPriority"));
                if (snapshot.getInt("workTotal") > 0) y = progress(g, y + 5, snapshot.getInt("workDone"), snapshot.getInt("workTotal"));
                yield y;
            }
            case 2 -> renderRows(g, y, snapshot.getList("skills", Tag.TAG_COMPOUND), "name", "level", "xp", " XP");
            case 3 -> {
                y = heading(g, y, "Household " + or(snapshot.getString("household"), "unassigned"));
                y = line(g, y, "Status", or(snapshot.getString("householdState"), "No household"));
                y = line(g, y, "Home", or(snapshot.getString("home"), "NEEDS_HOME"));
                for (Tag raw : snapshot.getList("householdMembers", Tag.TAG_COMPOUND)) {
                    CompoundTag row = (CompoundTag) raw; String relation = row.getBoolean("dependant") ? "Dependant"
                            : row.getBoolean("guardian") ? "Guardian" : "Member";
                    y = row(g, y, row.getString("name") + " — " + row.getString("readable"),
                            titleCase(row.getString("stage")) + " • " + relation);
                }
                yield y;
            }
            case 4 -> {
                y = heading(g, y, "Concept knowledge");
                ListTag concepts = snapshot.getList("concepts", Tag.TAG_COMPOUND);
                if (concepts.isEmpty()) y = muted(g, y, "No developed concepts yet — learning remains autonomous.");
                CompoundTag learning = null; int bestLearning = -1; int canTeach = 0;
                for (Tag raw : concepts) { CompoundTag concept = (CompoundTag) raw;
                    if (concept.getBoolean("canTeach")) canTeach++;
                    if (!concept.getString("level").equals("MASTERED") && concept.getInt("points") > bestLearning) {
                        bestLearning = concept.getInt("points"); learning = concept;
                    }
                }
                if (learning != null) y = line(g, y, "Currently learning", learning.getString("name"));
                y = line(g, y, "Can teach", canTeach + " concept(s)");
                y = renderRows(g, y, concepts, "name", "level", "points", " pts");
                y = heading(g, y + 5, "Recipe familiarity");
                ListTag recipes = snapshot.getList("recipes", Tag.TAG_COMPOUND);
                if (recipes.isEmpty()) y = muted(g, y, "No practised recipes yet.");
                y = renderRows(g, y, recipes, "id", "level", "points", " pts");
                if (!snapshot.getString("mentor").isBlank()) y = line(g, y + 4, "Mentor", snapshot.getString("mentor"));
                ListTag apprentices = snapshot.getList("apprentices", Tag.TAG_COMPOUND);
                if (!apprentices.isEmpty()) {
                    y = heading(g, y + 4, "Apprentices");
                    for (Tag raw : apprentices) { CompoundTag apprentice = (CompoundTag) raw;
                        y = row(g, y, apprentice.getString("name") + " — " + apprentice.getString("readable"), apprentice.getString("focus")); }
                }
                yield y;
            }
            case 5 -> renderInventory(g, y, mouseX, mouseY);
            default -> {
                y = heading(g, y, "Meaningful history");
                ListTag history = snapshot.getList("history", Tag.TAG_COMPOUND);
                if (history.isEmpty()) y = muted(g, y, "No recorded milestones yet.");
                for (int i = history.size() - 1; i >= 0; i--) {
                    CompoundTag event = history.getCompound(i);
                    y = row(g, y, titleCase(event.getString("type")), event.getString("description"));
                }
                yield y;
            }
        };
    }

    private int renderSettlement(GuiGraphics g, int y, int mouseX, int mouseY) {
        return switch (selectedTab) {
            case 0 -> {
                y = heading(g, y, "Settlement overview"); y = line(g, y, "Population", snapshot.getInt("population") + "");
                y = line(g, y, "Households", snapshot.getInt("households") + "");
                y = line(g, y, "Housing capacity", snapshot.getInt("housingCapacity") + " completed, "
                        + snapshot.getInt("futureCapacity") + " including construction");
                int homeless = snapshot.getInt("needsHomes");
                y = wrapped(g, y + 4, homeless == 0 ? "Housing is currently stable"
                        : homeless + " household(s) need housing", homeless == 0 ? ACCENT : ALERT);
                ListTag shortages = snapshot.getList("shortages", Tag.TAG_COMPOUND);
                if (shortages.isEmpty()) y = wrapped(g, y, "No active material or survival shortages", ACCENT);
                for (Tag raw : shortages) { CompoundTag s = (CompoundTag) raw;
                    y = wrapped(g, y, titleCase(s.getString("type")) + " shortage: " + s.getString("severity"), ALERT); }
                yield y;
            }
            case 1 -> renderCitizenList(g, y, mouseX, mouseY);
            case 2 -> {
                y = heading(g, y, "Households");
                y = line(g, y, "Total", snapshot.getInt("households") + "");
                y = line(g, y, "Needing homes", snapshot.getInt("needsHomes") + "");
                for (Tag raw : snapshot.getList("householdRows", Tag.TAG_COMPOUND)) { CompoundTag h = (CompoundTag) raw;
                    y = row(g, y, h.getString("readable") + " — " + titleCase(h.getString("state")),
                            h.getInt("members") + " member(s) • home " + h.getString("home")); }
                yield y;
            }
            case 3 -> renderBuildingList(g, y, mouseX, mouseY);
            case 4 -> {
                y = heading(g, y, "Physical economy");
                ListTag shortages = snapshot.getList("shortages", Tag.TAG_COMPOUND);
                if (shortages.isEmpty()) y = muted(g, y, "Physical supply is currently stable.");
                for (Tag raw : shortages) { CompoundTag s = (CompoundTag) raw;
                    y = row(g, y, titleCase(s.getString("type")), s.getString("severity")); }
                yield y;
            }
            case 5 -> renderKnowledgeSummary(g, y);
            default -> muted(g, heading(g, y, "Settlement history"), "Institutional history hooks are ready; no events recorded yet.");
        };
    }

    private int renderBuilding(GuiGraphics g, int y, int mouseX, int mouseY) {
        if (selectedTab == 1) {
            y = heading(g, y, "Residents"); ListTag residents = snapshot.getList("residents", Tag.TAG_COMPOUND);
            if (residents.isEmpty()) return muted(g, y, "No residents assigned.");
            for (Tag raw : residents) { CompoundTag resident = (CompoundTag) raw;
                y = row(g, y, resident.getString("name"), resident.getString("readable")); }
            return y;
        }
        if (selectedTab == 2) return muted(g, heading(g, y, "Building history"),
                "Restoration and heritage history will be recorded by later physical systems.");
        y = heading(g, y, "Building record"); y = line(g, y, "Building", snapshot.getString("buildingId"));
        y = line(g, y, "Type", titleCase(snapshot.getString("name"))); y = line(g, y, "Condition", snapshot.getString("condition"));
        y = line(g, y, "Architecture", snapshot.getString("style")); y = line(g, y, "Capacity", snapshot.getInt("capacity") + "");
        y = line(g, y, "Location", snapshot.getString("anchor"));
        if (snapshot.getLong("completedTick") > 0) y = line(g, y, "Completed", "Settlement day "
                + (snapshot.getLong("completedTick") / 24000L + 1L));
        return line(g, y, "Construction project", snapshot.getString("projectReadable"));
    }

    private int renderCitizenList(GuiGraphics g, int y, int mouseX, int mouseY) {
        y += 24; List<CompoundTag> visible = filteredCitizens();
        for (CompoundTag citizen : visible) {
            boolean hover = mouseX >= contentX && mouseX < contentX + contentW && mouseY >= y && mouseY < y + 28;
            if (hover) g.fill(contentX - 2, y - 2, contentX + contentW, y + 25, 0x33FFFFFF);
            y = row(g, y, citizen.getString("name") + " — " + citizen.getString("readable"),
                    titleCase(citizen.getString("stage")) + " • " + citizen.getString("career") + " • "
                            + titleCase(citizen.getString("activity")));
        }
        return y;
    }

    private int renderBuildingList(GuiGraphics g, int y, int mouseX, int mouseY) {
        y = heading(g, y, "Buildings & construction");
        for (Tag raw : snapshot.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag b = (CompoundTag) raw; boolean project = b.getString("kind").equals("PROJECT");
            String name = titleCase(b.getString("name")) + " — " + (project ? b.getString("projectId") : b.getString("buildingId"));
            String detail = project ? b.getInt("done") + " / " + b.getInt("total") + " blocks • "
                    + b.getString("priority") + " • " + titleCase(b.getString("state"))
                    : b.getString("condition") + " • capacity " + b.getInt("capacity");
            boolean hover = !project && mouseX >= contentX && mouseX < contentX + contentW && mouseY >= y && mouseY < y + 28;
            if (hover) g.fill(contentX - 2, y - 2, contentX + contentW, y + 25, 0x33FFFFFF);
            y = row(g, y, name, detail);
            if (project) {
                y = wrapped(g, y, "Why: " + b.getString("reason"), ACCENT);
                for (Tag builderRaw : b.getList("builders", Tag.TAG_COMPOUND)) { CompoundTag builder = (CompoundTag) builderRaw;
                    y = line(g, y, "Builder", builder.getString("name") + " — " + titleCase(builder.getString("level"))); }
                for (Tag materialRaw : b.getList("materials", Tag.TAG_COMPOUND)) { CompoundTag m = (CompoundTag) materialRaw;
                    y = line(g, y, shortId(m.getString("item")), m.getInt("available") + " / " + m.getInt("required")); }
            }
        }
        return y;
    }

    private int renderKnowledgeSummary(GuiGraphics g, int y) {
        y = heading(g, y, "Knowledge distribution");
        for (Tag raw : snapshot.getList("knowledgeSummary", Tag.TAG_COMPOUND)) {
            CompoundTag k = (CompoundTag) raw;
            y = row(g, y, k.getString("name"), "Mastered " + k.getInt("MASTERED") + " • Understood "
                    + k.getInt("UNDERSTOOD") + " • Familiar " + k.getInt("FAMILIAR") + " • Teachers " + k.getInt("teachers"));
        }
        return y;
    }

    private int renderInventory(GuiGraphics g, int y, int mouseX, int mouseY) {
        y = heading(g, y, "Physical inventory");
        int startX = contentX; int startY = y + 4;
        ListTag items = snapshot.getList("inventory", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < 9; slot++) {
            int x = startX + (slot % 9) * 20; int sy = startY + (slot / 9) * 20;
            g.fill(x, sy, x + 18, sy + 18, 0xFF5A4631); g.fill(x + 1, sy + 1, x + 17, sy + 17, 0xFFE5C98F);
            if (slot >= items.size()) continue;
            CompoundTag row = items.getCompound(slot); if (!row.contains("stack", Tag.TAG_COMPOUND) || minecraft.level == null) continue;
            ItemStack stack = ItemStack.parseOptional(minecraft.level.registryAccess(), row.getCompound("stack"));
            g.renderItem(stack, x + 1, sy + 1); g.renderItemDecorations(font, stack, x + 1, sy + 1);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= sy && mouseY < sy + 18)
                deferredItemTooltip = stack;
        }
        return startY + 30;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || view != DashboardView.SETTLEMENT) return false;
        int y = contentY - scroll + (selectedTab == 1 ? 24 : selectedTab == 3 ? 13 : 0);
        if (selectedTab == 1) for (CompoundTag citizen : filteredCitizens()) {
            if (mouseX >= contentX && mouseX < contentX + contentW && mouseY >= y - 2 && mouseY < y + 25) {
                request(DashboardView.CITIZEN, citizen.getUUID("id")); return true;
            } y += 30;
        }
        if (selectedTab == 3) for (Tag raw : snapshot.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag building = (CompoundTag) raw;
            if (building.getString("kind").equals("PROJECT")) {
                y += 30 + font.split(Component.literal("Why: " + building.getString("reason")), contentW).size() * 10
                        + building.getList("materials", Tag.TAG_COMPOUND).size() * 12; continue;
            }
            if (mouseX >= contentX && mouseX < contentX + contentW && mouseY >= y - 2 && mouseY < y + 25) {
                request(DashboardView.BUILDING, building.getUUID("id")); return true;
            } y += 30;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int)(scrollY * 18)); return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    private void request(DashboardView requestedView, UUID target) {
        PacketDistributor.sendToServer(new DashboardRequestPayload(requestedView, target));
    }

    private List<CompoundTag> filteredCitizens() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        List<CompoundTag> result = new ArrayList<>();
        for (Tag raw : snapshot.getList("citizens", Tag.TAG_COMPOUND)) {
            CompoundTag citizen = (CompoundTag) raw;
            String haystack = (citizen.getString("name") + " " + citizen.getString("readable") + " "
                    + citizen.getString("career") + " " + citizen.getString("stage")).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || haystack.contains(query)) result.add(citizen);
        }
        return result;
    }

    private int heading(GuiGraphics g, int y, String text) { g.drawString(font, text, contentX, y, ACCENT, false); return y + 16; }
    private int line(GuiGraphics g, int y, String label, String value) {
        g.drawString(font, label + ":", contentX, y, MUTED, false);
        g.drawString(font, trim(value, contentW - 105), contentX + 102, y, INK, false); return y + 13;
    }
    private int row(GuiGraphics g, int y, String title, String detail) {
        g.drawString(font, trim(title, contentW), contentX, y, INK, false);
        g.drawString(font, trim(detail, contentW), contentX, y + 12, MUTED, false); return y + 30;
    }
    private int muted(GuiGraphics g, int y, String text) { return wrapped(g, y, text, MUTED); }
    private int wrapped(GuiGraphics g, int y, String text, int colour) {
        for (var line : font.split(Component.literal(text), contentW)) { g.drawString(font, line, contentX, y, colour, false); y += 10; }
        return y + 3;
    }
    private int progress(GuiGraphics g, int y, int done, int total) {
        int width = Math.min(contentW, 250); int filled = total <= 0 ? 0 : width * done / total;
        g.fill(contentX, y, contentX + width, y + 10, 0xFF765E43); g.fill(contentX + 1, y + 1, contentX + Math.max(1, filled - 1), y + 9, ACCENT);
        g.drawString(font, done + " / " + total, contentX + width + 8, y + 1, INK, false); return y + 17;
    }
    private int needBar(GuiGraphics g, int y, String label, double value) {
        g.drawString(font, label, contentX, y, MUTED, false); int x = contentX + 70, w = Math.min(180, contentW - 110);
        g.fill(x, y, x + w, y + 9, 0xFF765E43); int colour = value >= 78 ? ALERT : value >= 55 ? 0xFFB57A2D : ACCENT;
        g.fill(x + 1, y + 1, x + Math.max(1, (int)((w - 2) * value / 100.0D)), y + 8, colour);
        g.drawString(font, (int)value + "%", x + w + 6, y, INK, false); return y + 15;
    }
    private int renderRows(GuiGraphics g, int y, ListTag rows, String name, String level, String points, String suffix) {
        for (Tag raw : rows) { CompoundTag row = (CompoundTag) raw;
            y = row(g, y, titleCase(shortId(row.getString(name))), titleCase(row.getString(level)) + " • " + row.getInt(points) + suffix); }
        return y;
    }
    private String trim(String text, int pixels) { return font.width(text) <= pixels ? text : font.plainSubstrByWidth(text, Math.max(5, pixels - 8)) + "…"; }
    private static String[] tabsFor(DashboardView view) { return switch (view) {
        case CITIZEN -> new String[]{"Overview", "Work", "Skills", "Household", "Knowledge", "Inventory", "History"};
        case SETTLEMENT -> new String[]{"Overview", "Citizens", "Households", "Buildings", "Economy", "Knowledge", "History"};
        case BUILDING -> new String[]{"Overview", "Residents", "History"};
    }; }
    private String[] tabs() { return tabsFor(view); }
    private static DashboardView parseView(String value) { try { return DashboardView.valueOf(value); } catch (Exception ignored) { return DashboardView.CITIZEN; } }
    private static String join(String delimiter, String... values) { return String.join(delimiter, java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList()); }
    private static String or(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "Unknown"; String clean = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }
    private static String shortId(String value) { int colon = value.indexOf(':'); return colon >= 0 ? value.substring(colon + 1).replace('_', ' ') : value; }
}
