package net.messer.augment;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import com.mojang.serialization.MapCodec;
public final class ChamberBlock extends BaseEntityBlock {
    public static final MapCodec<ChamberBlock> CODEC=simpleCodec(ChamberBlock::new);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty CLOSED=BooleanProperty.create("closed");
    public ChamberBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,DoubleBlockHalf.LOWER).setValue(CLOSED,false)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() {return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {b.add(FACING,HALF,CLOSED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return c.getClickedPos().getY()<c.getLevel().getMaxBuildHeight()-1 && c.getLevel().getBlockState(c.getClickedPos().above()).canBeReplaced(c)?defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite()):null;
    }
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity e,ItemStack stack) {l.setBlock(p.above(),s.setValue(HALF,DoubleBlockHalf.UPPER),3);}
    public static BlockPos base(BlockPos p,BlockState s) {return s.getValue(HALF)==DoubleBlockHalf.UPPER?p.below():p;}
    @Override public BlockState updateShape(BlockState s,Direction d,BlockState other,LevelAccessor l,BlockPos p,BlockPos op) {
        Direction partner=s.getValue(HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN;
        if(d==partner && (!other.is(this)||other.getValue(HALF)==s.getValue(HALF))) return Blocks.AIR.defaultBlockState();
        return super.updateShape(s,d,other,l,p,op);
    }
    @Override public BlockState playerWillDestroy(Level l,BlockPos p,BlockState s,Player player) {
        if(!l.isClientSide && s.getValue(HALF)==DoubleBlockHalf.UPPER) {
            var bottom=p.below(); var state=l.getBlockState(bottom);
            if(state.is(this)) l.destroyBlock(bottom,!player.isCreative(),player);
        }
        return super.playerWillDestroy(l,p,s,player);
    }
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving) {
        if(!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof ChamberEntity be) be.release();
        super.onRemove(s,l,p,next,moving);
    }
    @Override public RenderShape getRenderShape(BlockState s) {return RenderShape.ENTITYBLOCK_ANIMATED;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s) {return s.getValue(HALF)==DoubleBlockHalf.LOWER?new ChamberEntity(p,s):null;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t) {return createTickerHelper(t,Augment.CHAMBER_ENTITY.get(),ChamberEntity::tick);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) {return Shapes.block();}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) {
        // 12-unit entrance and 29.5-unit interior headroom across the two halves.
        var shape=Shapes.or(box(0,0,0,2,16,16),box(14,0,0,16,16,16),box(2,0,14,14,16,16));
        if(s.getValue(HALF)==DoubleBlockHalf.LOWER) shape=Shapes.or(shape,box(0,0,0,16,1,16));
        else shape=Shapes.or(shape,box(0,14.5,0,16,16,16));
        if(s.getValue(CLOSED)) shape=Shapes.or(shape,box(2,0,0,14,16,1));
        return rotate(shape,s.getValue(FACING));
    }
    private static VoxelShape rotate(VoxelShape shape,Direction facing) {
        int turns=switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
        for(int i=0;i<turns;i++){ final VoxelShape[] out={Shapes.empty()}; shape.forAllBoxes((a,b,c,d,e,f)->out[0]=Shapes.or(out[0],Shapes.box(1-f,b,a,1-c,e,d))); shape=out[0]; }
        return shape;
    }
    // 1.21 split BlockBehaviour#use into an empty-hand path and an item path.
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit) {
        if(!l.isClientSide && l.getBlockEntity(base(p,s)) instanceof ChamberEntity be && be.owns(player)) be.openMenu(player);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand h,BlockHitResult hit) {
        useWithoutItem(s,l,p,player,hit);
        return ItemInteractionResult.sidedSuccess(l.isClientSide);
    }
}
