package gg.galaxygaming.gasconduits.conduit;

import crazypants.enderio.base.conduit.ConduitUtil;
import crazypants.enderio.base.conduit.ConnectionMode;
import crazypants.enderio.base.conduit.IConduitNetwork;
import crazypants.enderio.base.conduit.RaytraceResult;
import crazypants.enderio.base.conduit.geom.CollidableComponent;
import crazypants.enderio.base.tool.ToolUtil;
import crazypants.enderio.conduits.render.BlockStateWrapperConduitBundle.ConduitCacheKey;
import gg.galaxygaming.gasconduits.utils.GasUtil;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class AbstractTankConduit extends AbstractGasConduit {
    protected ConduitTank tank = new ConduitTank(0);
    protected boolean stateDirty = false;
    protected long lastEmptyTick = 0;
    protected int numEmptyEvents = 0;
    protected boolean gasTypeLocked = false;

    @Override
    public boolean onBlockActivated(@Nonnull EntityPlayer player, @Nonnull EnumHand hand, @Nonnull RaytraceResult res, @Nonnull List<RaytraceResult> all) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem.isEmpty()) {
            return false;
        }
        AbstractTankConduitNetwork<? extends AbstractTankConduit> network = getTankNetwork();
        if (ToolUtil.isToolEquipped(player, hand)) {

            if (!getBundle().getEntity().getWorld().isRemote) {
                final CollidableComponent component = res.component;
                if (component != null) {
                    EnumFacing faceHit = res.movingObjectPosition.sideHit;
                    if (component.isCore()) {
                        if (getConnectionMode(faceHit) == ConnectionMode.DISABLED) {
                            setConnectionMode(faceHit, getNextConnectionMode(faceHit));
                            return true;
                        }
                        // Attempt to join networks
                        BlockPos pos = getBundle().getLocation().offset(faceHit);
                        IGasConduit gasConduit = ConduitUtil.getConduit(getBundle().getEntity().getWorld(), pos.getX(), pos.getY(), pos.getZ(), IGasConduit.class);
                        if (!(gasConduit instanceof AbstractTankConduit) || !canJoinNeighbour(gasConduit)) {
                            return false;
                        }
                        AbstractTankConduit neighbour = (AbstractTankConduit) gasConduit;
                        if (neighbour.getGasType() != null) {
                            setGasTypeOnNetwork(this, neighbour.getGasType());
                        } else if (getGasType() != null) {
                            neighbour.setGasTypeOnNetwork(neighbour, getGasType());
                        }
                        return ConduitUtil.connectConduits(this, faceHit);
                    } else {
                        EnumFacing connDir = component.getDirection();
                        if (containsExternalConnection(connDir)) {
                            setConnectionMode(connDir, getNextConnectionMode(connDir));
                        } else if (containsConduitConnection(connDir)) {
                            GasStack curGasType = null;
                            if (getTankNetwork() != null) {
                                curGasType = getTankNetwork().getGasType();
                            }
                            ConduitUtil.disconnectConduits(this, connDir);
                            setGasType(curGasType);
                        }
                    }
                }
            }
            return true;

        } else if (heldItem.getItem() == Items.BUCKET) {

            if (!getBundle().getEntity().getWorld().isRemote) {
                long curTick = getBundle().getEntity().getWorld().getTotalWorldTime();
                if (curTick - lastEmptyTick < 20) {
                    numEmptyEvents++;
                } else {
                    numEmptyEvents = 1;
                }
                lastEmptyTick = curTick;

                if (numEmptyEvents < 2) {
                    if (network.gasTypeLocked) {
                        network.setGasTypeLocked(false);
                        numEmptyEvents = 0;
                        player.sendStatusMessage(new TextComponentTranslation("enderio.item_gas_conduit.unlocked_type"), true);
                    }
                } else if (network != null) {
                    network.setGasType(null);
                    numEmptyEvents = 0;
                }
            }

            return true;
        } else {

            GasStack gas = GasUtil.getGasTypeFromItem(heldItem);
            if (gas != null) {
                if (!getBundle().getEntity().getWorld().isRemote) {
                    if (network != null
                            && (network.getGasType() == null || network.getTotalVolume() < 500 || GasConduitNetwork.areGassesCompatable(getGasType(), gas))) {
                        network.setGasType(gas);
                        network.setGasTypeLocked(true);

                        player.sendStatusMessage(new TextComponentTranslation("enderio.item_gas_conduit.locked_type", gas.getGas().getLocalizedName()), true);
                    }
                }
                return true;
            }
        }

        return false;
    }

    void setGasTypeLocked(boolean gasTypeLocked) {
        if (gasTypeLocked == this.gasTypeLocked) {
            return;
        }
        this.gasTypeLocked = gasTypeLocked;
        stateDirty = true;
    }

    private void setGasTypeOnNetwork(AbstractTankConduit con, GasStack type) {
        IConduitNetwork<?, ?> n = con.getNetwork();
        if (n != null) {
            AbstractTankConduitNetwork<?> network = (AbstractTankConduitNetwork<?>) n;
            network.setGasType(type);
        }

    }

    protected abstract boolean canJoinNeighbour(IGasConduit n);

    public abstract AbstractTankConduitNetwork<? extends AbstractTankConduit> getTankNetwork();

    public void setGasType(GasStack gasType) {
        if (tank.getGas() != null && tank.getGas().isGasEqual(gasType)) {
            return;
        }
        if (gasType != null) {
            gasType = gasType.copy();
        } else if (tank.getGas() == null) {
            return;
        }
        tank.setGas(gasType);
        stateDirty = true;
    }

    public ConduitTank getTank() {
        return tank;
    }

    public GasStack getGasType() {
        GasStack result = null;
        if (getTankNetwork() != null) {
            result = getTankNetwork().getGasType();
        }
        if (result == null) {
            result = tank.getGas();
        }
        return result;
    }

    public boolean isGasTypeLocked() {
        return gasTypeLocked;
    }

    protected abstract void updateTank();

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound nbtRoot) {
        super.readFromNBT(nbtRoot);
        updateTank();
        if (nbtRoot.hasKey("tank")) {
            GasStack gas = GasStack.readFromNBT(nbtRoot.getCompoundTag("tank"));
            tank.setGas(gas);
        } else {
            tank.setGas(null);
        }
        gasTypeLocked = nbtRoot.getBoolean("gasLocked");
    }

    @Override
    public void writeToNBT(@Nonnull NBTTagCompound nbtRoot) {
        super.writeToNBT(nbtRoot);
        GasStack gt = getGasType();
        if (gt != null) {
            updateTank();
            gt = gt.copy();
            gt.amount = tank.getStored();
            nbtRoot.setTag("tank", gt.write(new NBTTagCompound()));
        }
        nbtRoot.setBoolean("gasLocked", gasTypeLocked);
    }

    @Override
    public void hashCodeForModelCaching(ConduitCacheKey hashCodes) {
        super.hashCodeForModelCaching(hashCodes);
        if (gasTypeLocked) {
            hashCodes.add(1);
        }
    }

    @Override
    public boolean canReceiveGas(EnumFacing side, Gas gas) {
        if (getNetwork() == null || !getConnectionMode(side).acceptsInput()) {
            return false;
        }
        return canExtractFromDir(side) && GasConduitNetwork.areGassesCompatable(getGasType(), gas);
    }

    @Override
    public boolean canDrawGas(EnumFacing side, Gas gas) {
        if (getNetwork() == null || !getConnectionMode(side).acceptsOutput()) {
            return false;
        }
        return canInputToDir(side) && GasConduitNetwork.areGassesCompatable(getGasType(), gas);
    }

}
