package blusunrize.immersiveengineering.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import blusunrize.immersiveengineering.api.WireType;
import blusunrize.immersiveengineering.api.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.ImmersiveNetHandler;
import blusunrize.immersiveengineering.api.TargetingInfo;
import blusunrize.immersiveengineering.common.IESaveData;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.immersiveengineering.common.util.Lib;
import blusunrize.immersiveengineering.common.util.Utils;


public class ItemWireCoil extends ItemIEBase
{
	public ItemWireCoil()
	{
		super("coil", 64, "copper","electrum","HV","rope","structural");
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv)
	{
		if(stack.getItemDamage()>2)
		{
			list.add(StatCollector.translateToLocal(Lib.DESC+"flavour.coil.construction0"));
			list.add(StatCollector.translateToLocal(Lib.DESC+"flavour.coil.construction1"));
		}
		if(stack.getTagCompound()!=null && stack.getTagCompound().hasKey("linkingPos"))
		{
			int[] link = stack.getTagCompound().getIntArray("linkingPos");
			if(link!=null&&link.length>3)
				list.add(StatCollector.translateToLocalFormatted(Lib.DESC_INFO+"attachedToDim", link[1],link[2],link[3],link[0]));
		}
	}

	@Override
	public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ)
	{
		if(!world.isRemote && world.getTileEntity(x, y, z) instanceof IImmersiveConnectable && ((IImmersiveConnectable)world.getTileEntity(x, y, z)).canConnect() )
		{
			TargetingInfo target = new TargetingInfo(side, hitX,hitY,hitZ);
			if( !((IImmersiveConnectable)world.getTileEntity(x, y, z)).canConnectCable(WireType.getValue(stack.getItemDamage()), target))
			{
				player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN+"wrongCable"));
				return false;
			}

			if(!ItemNBTHelper.hasKey(stack, "linkingPos"))
			{
				ItemNBTHelper.setIntArray(stack, "linkingPos", new int[]{world.provider.dimensionId,x,y,z});
				target.writeToNBT(stack.getTagCompound());
			}
			else
			{
				WireType type = WireType.getValue(stack.getItemDamage());
				int[] pos = ItemNBTHelper.getIntArray(stack, "linkingPos");
				int distance = (int) Math.ceil(Math.sqrt( (pos[1]-x)*(pos[1]-x) + (pos[2]-y)*(pos[2]-y) + (pos[3]-z)*(pos[3]-z) ));
				if(pos[0]!=world.provider.dimensionId)
					player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN+"wrongDimension"));
				else if(pos[1]==x&&pos[2]==y&&pos[3]==z)
					player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN+"sameConnection"));
				else if( distance > type.getMaxLength())
					player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN+"tooFar"));
				else
				{
					IImmersiveConnectable nodeHere = (IImmersiveConnectable)world.getTileEntity(x, y, z);
					IImmersiveConnectable nodeLink = (IImmersiveConnectable)world.getTileEntity(pos[1], pos[2], pos[3]);
					if(nodeHere!=null && nodeLink!=null)
					{
						Vec3 rtOff0 = nodeHere.getRaytraceOffset().addVector(x, y, z);
						Vec3 rtOff1 = nodeLink.getRaytraceOffset().addVector(pos[1], pos[2], pos[3]);
						boolean canSee = Utils.canBlocksSeeOther(world, new ChunkCoordinates(x,y,z), new ChunkCoordinates(pos[1], pos[2], pos[3]), rtOff0,rtOff1);
						if(canSee)
						{
							TargetingInfo targetLink = TargetingInfo.readFromNBT(stack.getTagCompound());
							ImmersiveNetHandler.addConnection(world, Utils.toCC(nodeHere), Utils.toCC(nodeLink), distance, type);
							nodeHere.connectCable(type, target);
							nodeLink.connectCable(type, targetLink);
							IESaveData.setDirty(world.provider.dimensionId);

							if(!player.capabilities.isCreativeMode)
								stack.stackSize--;
							world.markBlockForUpdate(x, y, z);
							world.markBlockForUpdate(pos[1], pos[2], pos[3]);
						}
						else
							player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN+"cantSee"));
					}
				}
				ItemNBTHelper.remove(stack, "linkingPos");
				ItemNBTHelper.remove(stack, "side");
				ItemNBTHelper.remove(stack, "hitX");
				ItemNBTHelper.remove(stack, "hitY");
				ItemNBTHelper.remove(stack, "hitZ");
			}
			return true;
		}
		return false;
	}
}