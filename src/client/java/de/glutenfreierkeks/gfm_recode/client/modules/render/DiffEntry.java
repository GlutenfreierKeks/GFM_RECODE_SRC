package de.glutenfreierkeks.gfm_recode.client.modules.render;

import net.minecraft.util.math.BlockPos;

public class DiffEntry {
    public final BlockPos pos;
    public final DiffType type;

    public DiffEntry(BlockPos pos, DiffType type) {
        this.pos = pos;
        this.type = type;
    }

    public enum DiffType {
        ADDED,
        REMOVED
    }
}
