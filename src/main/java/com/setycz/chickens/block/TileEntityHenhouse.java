package com.setycz.chickens.block;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.setycz.chickens.client.gui.GuiHenhouse;
import com.setycz.chickens.client.gui.IInventoryGui;
import com.setycz.chickens.client.gui.container.ContainerHenhouse;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Created by setyc on 01.03.2016.
 */
public class TileEntityHenhouse extends TileEntity implements ISidedInventory, IInventoryGui {
    public static final int hayBaleEnergy = 100;

    public static final int hayBaleSlotIndex = 0;
    public static final int dirtSlotIndex = 1;
    public static final int firstItemSlotIndex = 2;
    private static final int lastItemSlotIndex = 10;
    private static final double HENHOUSE_RADIUS = 0.5;
    private static final double FENCE_THRESHOLD = 0.5;

    private String customName;
    private final NonNullList<ItemStack> slots = NonNullList.withSize(11, ItemStack.EMPTY);
    private int energy = 0;

    @Nullable
    public static ItemStack pushItemStack(ItemStack itemToLay, World worldObj, Vec3d pos) {
        List<TileEntityHenhouse> henhouses = findHenhouses(worldObj, pos, 4 + HENHOUSE_RADIUS + FENCE_THRESHOLD);
        for (TileEntityHenhouse henhouse : henhouses) {
            itemToLay = henhouse.pushItemStack(itemToLay);
            if (itemToLay == null) {
                break;
            }
        }
        return itemToLay;
    }

    private static List<TileEntityHenhouse> findHenhouses(World worldObj, Vec3d pos, double radius) {
        int firstChunkX = MathHelper.floor((pos.xCoord - radius - World.MAX_ENTITY_RADIUS) / 16.0D);
        int lastChunkX = MathHelper.floor((pos.xCoord + radius + World.MAX_ENTITY_RADIUS) / 16.0D);
        int firstChunkY = MathHelper.floor((pos.zCoord - radius - World.MAX_ENTITY_RADIUS) / 16.0D);
        int lastChunkY = MathHelper.floor((pos.zCoord + radius + World.MAX_ENTITY_RADIUS) / 16.0D);

        List<Double> distances = new ArrayList<Double>();
        List<TileEntityHenhouse> result = new ArrayList<TileEntityHenhouse>();
        for (int chunkX = firstChunkX; chunkX <= lastChunkX; ++chunkX) {
            for (int chunkY = firstChunkY; chunkY <= lastChunkY; ++chunkY) {
                Chunk chunk = worldObj.getChunkFromChunkCoords(chunkX, chunkY);
                for (TileEntity tileEntity : chunk.getTileEntityMap().values()) {
                    if (tileEntity instanceof TileEntityHenhouse) {
                        Vec3d tileEntityPos = new Vec3d(tileEntity.getPos()).addVector(HENHOUSE_RADIUS, HENHOUSE_RADIUS, HENHOUSE_RADIUS);
                        boolean inRage = testRange(pos, tileEntityPos, radius);
                        if (inRage) {
                            double distance = pos.distanceTo(tileEntityPos);
                            addHenhouseToResults((TileEntityHenhouse) tileEntity, distance, distances, result);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean testRange(Vec3d pos1, Vec3d pos2, double range) {
        return Math.abs(pos1.xCoord - pos2.xCoord) <= range &&
                Math.abs(pos1.yCoord - pos2.yCoord) <= range &&
                Math.abs(pos1.zCoord - pos2.zCoord) <= range;
    }

    private static void addHenhouseToResults(TileEntityHenhouse henhouse, double distance, List<Double> distances, List<TileEntityHenhouse> henhouses) {
        for (int resultIndex = 0; resultIndex < distances.size(); resultIndex++) {
            if (distance < distances.get(resultIndex)) {
                distances.add(resultIndex, distance);
                henhouses.add(resultIndex, henhouse);
                return;
            }
        }
        distances.add(distance);
        henhouses.add(henhouse);
    }

    private ItemStack pushItemStack(ItemStack stack) {
        ItemStack rest = stack.copy();

        int capacity = getEffectiveCapacity();
        if (capacity <= 0) {
            return rest;
        }

        for (int slotIndex = firstItemSlotIndex; slotIndex <= lastItemSlotIndex; slotIndex++) {
            int canAdd = canAdd(slots.get(slotIndex), rest);
            int willAdd = Math.min(canAdd, capacity);
            if (willAdd > 0) {
                consumeEnergy(willAdd);
                capacity -= willAdd;

                if (slots.get(slotIndex).isEmpty()) {
                    slots.set(slotIndex, rest.splitStack(willAdd));
                } else {
                    slots.get(slotIndex).grow(willAdd);
                    rest.shrink(willAdd);
                }

                if (rest.getCount() <= 0) {
                    return ItemStack.EMPTY;
                }
            }
        }

        markDirty();
        return rest;
    }

    private void consumeEnergy(int amount) {
        while (amount > 0) {
            if (energy == 0) {
                assert !slots.get(hayBaleSlotIndex).isEmpty();
                slots.get(hayBaleSlotIndex).shrink(1);
                if (slots.get(hayBaleSlotIndex).getCount() <= 0) {
                    slots.set(hayBaleSlotIndex, ItemStack.EMPTY);
                }
                energy += hayBaleEnergy;
            }

            int consumed = Math.min(amount, energy);
            energy -= consumed;
            amount -= consumed;

            if (energy <= 0) {
                if (slots.get(dirtSlotIndex).isEmpty()) {
                    slots.set(dirtSlotIndex, new ItemStack(Blocks.DIRT, 1));
                } else {
                    slots.get(dirtSlotIndex).grow(1);
                }
            }
        }
    }

    private int canAdd(@Nullable ItemStack slotStack, ItemStack inputStack) {
        if (slotStack == null) {
            return Math.min(getInventoryStackLimit(), inputStack.getCount());
        }
        if (!slotStack.isItemEqual(inputStack)) {
            return 0;
        }
        if (slotStack.getCount() >= getInventoryStackLimit()) {
            return 0;
        }
        return Math.min(inputStack.getCount(), getInventoryStackLimit() - slotStack.getCount());
    }

    private int getEffectiveCapacity() {
        return Math.min(getInputCapacity(), getOutputCapacity());
    }

    private int getInputCapacity() {
        int potential = energy;

        ItemStack hayBaleStack = slots.get(hayBaleSlotIndex);
        if (!hayBaleStack.isEmpty() && hayBaleStack.getItem() == Item.getItemFromBlock(Blocks.HAY_BLOCK)) {
            potential += hayBaleStack.getCount() * hayBaleEnergy;
        }

        return potential;
    }

    private int getOutputCapacity() {
        ItemStack dirtStack = slots.get(dirtSlotIndex);
        if (dirtStack.isEmpty()) {
            return getInventoryStackLimit() * hayBaleEnergy;
        }
        if (dirtStack.getItem() != Item.getItemFromBlock(Blocks.DIRT)) {
            return 0;
        }
        return (getInventoryStackLimit() - dirtStack.getCount()) * hayBaleEnergy;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        if (hasCustomName()) {
            compound.setString("customName", customName);
        }

        compound.setInteger("energy", energy);

        NBTTagList items = new NBTTagList();
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            ItemStack itemStack = slots.get(slotIndex);
            if (!itemStack.isEmpty()) {
                NBTTagCompound item = new NBTTagCompound();
                item.setInteger("slot", slotIndex);
                itemStack.writeToNBT(item);
                items.appendTag(item);
            }
        }
        compound.setTag("items", items);

        compound.setInteger("energy", energy);

        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        customName = compound.getString("customName");

        energy = compound.getInteger("energy");

        clear();

        NBTTagList items = compound.getTagList("items", 10);
        for (int itemIndex = 0; itemIndex < items.tagCount(); itemIndex++) {
            NBTTagCompound item = items.getCompoundTagAt(itemIndex);
            int slotIndex = item.getInteger("slot");
            ItemStack itemStack = new ItemStack(item);
            slots.set(slotIndex, itemStack);
        }

        energy = compound.getInteger("energy");
    }

    @Override
    public int getSizeInventory() {
        return slots.size();
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return slots.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack stack = slots.get(index);

        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (count >= stack.getCount()) {
            slots.set(index, ItemStack.EMPTY);
            return stack;
        }
        return stack.splitStack(count);
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack stack = slots.get(index);
        slots.set(index, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        slots.set(index, stack);
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory(EntityPlayer player) {

    }

    @Override
    public void closeInventory(EntityPlayer player) {

    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        if (index == hayBaleSlotIndex) {
            return stack.getItem() == Item.getItemFromBlock(Blocks.HAY_BLOCK);
        }
        //noinspection RedundantIfStatement
        if (index == dirtSlotIndex) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : slots) {
            if (stack != null && !stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int getField(int id) {
        switch (id) {
            case 0:
                return energy;
            default:
                return 0;
        }
    }

    @Override
    public void setField(int id, int value) {
        switch (id) {
            case 0:
                energy = value;
                break;
        }
    }

    @Override
    public int getFieldCount() {
        return 1;
    }

    @Override
    public void clear() {
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            slots.set(slotIndex, ItemStack.EMPTY);
        }
    }

    @Override
    public String getName() {
        return hasCustomName() ? customName : "container.henhouse";
    }

    @Override
    public boolean hasCustomName() {
        return customName != null && customName.length() > 0;
    }

    @Override
    public ITextComponent getDisplayName() {
        return hasCustomName() ? new TextComponentString(getName()) : new TextComponentTranslation(getName());
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    @Override
    public Container createContainer(InventoryPlayer inventoryplayer) {
        return new ContainerHenhouse(inventoryplayer, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiContainer createGui(InventoryPlayer inventoryplayer) {
        return new GuiHenhouse(inventoryplayer, this);
    }

    public int getEnergy() {
        return energy;
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        switch (side) {
            case DOWN:
                int itemSlotCount = lastItemSlotIndex - firstItemSlotIndex + 1;
                int[] itemSlots = new int[itemSlotCount + 1];
                itemSlots[0] = dirtSlotIndex;
                for (int resultIndex = 0; resultIndex < itemSlotCount; resultIndex++) {
                    itemSlots[resultIndex + 1] = firstItemSlotIndex + resultIndex;
                }
                return itemSlots;
            case UP:
                return new int[]{hayBaleSlotIndex};
            default:
                return new int[0];
        }
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing direction) {
        return isItemValidForSlot(index, itemStackIn);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return true;
    }
}