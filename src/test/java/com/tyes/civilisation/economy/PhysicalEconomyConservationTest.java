package com.tyes.civilisation.economy;

import static org.junit.jupiter.api.Assertions.*;
import com.tyes.civilisation.economy.inventory.PhysicalInventoryOps;
import com.tyes.civilisation.economy.item.ItemKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Disabled;

@Disabled("Physical ItemStack/container conservation runs in the dedicated-server integration harness")
class PhysicalEconomyConservationTest {
    @Test void extractionAndInsertionConserveActualItems() {
        SimpleContainer source = new SimpleContainer(3), destination = new SimpleContainer(3);
        source.setItem(0, new ItemStack(Items.OAK_LOG, 17));
        ItemStack cargo = PhysicalInventoryOps.extract(source, ItemKey.bulk(new ItemStack(Items.OAK_LOG)), 12);
        assertEquals(12, cargo.getCount()); assertEquals(5, source.getItem(0).getCount());
        assertEquals(12, PhysicalInventoryOps.insert(destination, cargo)); assertTrue(cargo.isEmpty());
        assertEquals(17, PhysicalInventoryOps.count(source, ItemKey.bulk(new ItemStack(Items.OAK_LOG)))
                + PhysicalInventoryOps.count(destination, ItemKey.bulk(new ItemStack(Items.OAK_LOG))));
    }

    @Test void fullDestinationDoesNotDeleteOrDuplicateCargo() {
        SimpleContainer destination = new SimpleContainer(1);
        destination.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        ItemStack cargo = new ItemStack(Items.OAK_LOG, 8);
        assertEquals(0, PhysicalInventoryOps.insert(destination, cargo));
        assertEquals(8, cargo.getCount()); assertEquals(64, destination.getItem(0).getCount());
    }
}
