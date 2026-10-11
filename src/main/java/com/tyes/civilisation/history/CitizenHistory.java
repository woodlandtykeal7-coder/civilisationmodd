package com.tyes.civilisation.history;

import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Bounded important-event history; routine task completions are intentionally excluded. */
public final class CitizenHistory {
    public static final int MAX_EVENTS = 64;
    private final ArrayDeque<CitizenHistoryEvent> events = new ArrayDeque<>();

    public List<CitizenHistoryEvent> events() { return List.copyOf(events); }
    public void add(long tick, String type, String description) {
        CitizenHistoryEvent latest = events.peekLast();
        if (latest != null && latest.type().equals(type) && latest.description().equals(description)) return;
        while (events.size() >= MAX_EVENTS) events.removeFirst();
        events.addLast(new CitizenHistoryEvent(tick, type, description));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        events.forEach(event -> list.add(event.save()));
        tag.put("events", list);
        return tag;
    }

    public static CitizenHistory load(CompoundTag tag) {
        CitizenHistory result = new CitizenHistory();
        ListTag list = tag.getList("events", Tag.TAG_COMPOUND);
        int start = Math.max(0, list.size() - MAX_EVENTS);
        for (int i = start; i < list.size(); i++) result.events.addLast(CitizenHistoryEvent.load(list.getCompound(i)));
        return result;
    }
}
