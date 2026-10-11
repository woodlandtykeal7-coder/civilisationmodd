package com.tyes.civilisation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenSavedDataTest {
    @Test
    void preVersionedDataMigratesToSchemaOneAndBecomesDirty() {
        CompoundTag oldData = new CompoundTag();
        oldData.put("citizens", new ListTag());

        CitizenSavedData loaded = CitizenSavedData.factory().deserializer().apply(oldData, null);

        assertEquals(0, loaded.registry().size());
        assertTrue(loaded.isDirty());
        CompoundTag saved = loaded.save(new CompoundTag(), null);
        assertEquals(CitizenSavedData.CURRENT_SCHEMA_VERSION, saved.getInt("schemaVersion"));
    }

    @Test
    void newerUnknownSchemaIsRejected() {
        CompoundTag futureData = new CompoundTag();
        futureData.putInt("schemaVersion", CitizenSavedData.CURRENT_SCHEMA_VERSION + 1);
        futureData.put("citizens", new ListTag());

        assertThrows(IllegalStateException.class,
                () -> CitizenSavedData.factory().deserializer().apply(futureData, null));
    }

    @Test
    void schemaThreeCitizenGetsPersistedAppearanceWithoutIdentityChanges() {
        UUID id = UUID.fromString("cc8e75eb-524f-4287-984f-18c1d2e402f5");
        CompoundTag citizen = new CompoundTag();
        citizen.putUUID("citizenId", id);
        citizen.putString("readableId", "C-1042");
        citizen.putString("displayName", "Mira");
        citizen.putLong("appearanceSeed", 8675309L);
        ListTag citizens = new ListTag();
        citizens.add(citizen);
        CompoundTag oldData = new CompoundTag();
        oldData.putInt("schemaVersion", 3);
        oldData.putInt("nextCitizenNumber", 1043);
        oldData.put("citizens", citizens);

        CitizenSavedData loaded = CitizenSavedData.factory().deserializer().apply(oldData, null);
        var migrated = loaded.registry().find(id).orElseThrow();

        assertEquals("C-1042", migrated.readableId());
        assertEquals("Mira", migrated.displayName());
        assertEquals(id, migrated.citizenId());
        assertTrue(loaded.isDirty());
        CompoundTag saved = loaded.save(new CompoundTag(), null);
        assertTrue(saved.getList("citizens", Tag.TAG_COMPOUND).getCompound(0)
                .contains("appearance", Tag.TAG_COMPOUND));
    }
}
