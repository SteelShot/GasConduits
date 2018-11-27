package gg.galaxygaming.gasconduits.common.conduit;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;

public class GasOutput {
    private final EnumFacing dir;
    private final BlockPos location;

    public GasOutput(@Nonnull BlockPos pos, @Nonnull EnumFacing dir) {
        this.dir = dir;
        this.location = pos;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = prime + location.hashCode();
        return prime * result + dir.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        GasOutput other = (GasOutput) obj;
        return location.equals(other.location) && dir == other.dir;
    }

    public EnumFacing getDir() {
        return dir;
    }

    public BlockPos getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return "GasOutput [dir=" + dir + ", location=" + location + "]";
    }
}