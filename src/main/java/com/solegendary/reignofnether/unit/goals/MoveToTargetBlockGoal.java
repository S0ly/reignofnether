package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.commands.RtsDebug;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitPathClientboundPacket;
import com.solegendary.reignofnether.unit.pathfinding.MobilityClass;
import com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig;
import com.solegendary.reignofnether.unit.pathfinding.RtsPathfinder;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.EnumSet;

import static com.solegendary.reignofnether.unit.interfaces.Unit.FOLLOW_RANGE;
import static com.solegendary.reignofnether.unit.interfaces.Unit.FOLLOW_RANGE_IMPROVED;

public class MoveToTargetBlockGoal extends Goal {

    protected final Mob mob;
    @Nullable protected BlockPos moveTarget = null;
    protected boolean persistent;
    protected int moveReachRange = 0;
    @Nullable public BlockPos lastSelectedMoveTarget = null;

    protected final int RECALC_COOLDOWN_MAX = 20;
    protected static final int RECALC_COOLDOWN_CAP = 200;
    protected int currentRecalcCooldown = RECALC_COOLDOWN_MAX;
    protected void resetRecalcCooldown() { recalcCooldown = currentRecalcCooldown; }
    protected void backoffRecalcCooldown() {
        currentRecalcCooldown = Math.min(currentRecalcCooldown * 2, RECALC_COOLDOWN_CAP);
    }
    protected void resetRecalcBackoff() { currentRecalcCooldown = RECALC_COOLDOWN_MAX; }
    public boolean isInBackoff() { return currentRecalcCooldown > RECALC_COOLDOWN_MAX && moveTarget != null; }
    protected int recalcCooldown = 0;
    protected boolean pathPending = false;

    public MoveToTargetBlockGoal(Mob mob, boolean persistent, int reachRange) {
        this.mob = mob;
        this.persistent = persistent;
        this.moveReachRange = reachRange;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public boolean isAtDestination() {
        if (moveTarget == null) return true;
        return mob.getNavigation().isDone();
    }

    public double getMinDistToRecalculateSqr() {
        double dist = Math.max(1, moveReachRange);
        return dist * dist;
    }

    public boolean canUse() {
        if (this.mob instanceof Unit unit && unit.isFlyingUnit()) return false;
        return moveTarget != null;
    }

    public boolean canContinueToUse() {
        if (pathPending) return moveTarget != null;
        if (recalcCooldown > 0) { recalcCooldown -= 1; return true; }
        if (this.mob.getNavigation().isDone() && moveTarget != null &&
            this.mob.getOnPos().distSqr(moveTarget) > getMinDistToRecalculateSqr()) {
            BlockPos oldFinalNode = getFinalNodePos();
            this.start();
            BlockPos newFinalNode = getFinalNodePos();
            if (oldFinalNode != null && oldFinalNode.equals(newFinalNode))
                stopMoving();
            else
                backoffRecalcCooldown();
            resetRecalcCooldown();
            return true;
        }
        else if (moveTarget == null) return false;
        else if (this.mob.getNavigation().isDone()) {
            if (!persistent && !((Unit) this.mob).getHoldPosition()) moveTarget = null;
            return false;
        }
        return true;
    }

    public void start() {
        if (moveTarget == null) { this.mob.getNavigation().stop(); return; }
        if (!(this.mob instanceof Unit u)) { this.mob.getNavigation().stop(); return; }
        this.mob.setMaxUpStep(1.0f);
        if (this.mob.getNavigation() instanceof GroundPathNavigation gpn) gpn.setCanFloat(true);

        if (PathfinderConfig.isRtsEnabled()) {
            pathPending = true;
            this.mob.getNavigation().stop();
            RtsPathfinder.requestPath(this.mob, moveTarget, moveReachRange, MobilityClass.of(u), this::onPathReady);
        } else {
            AttributeInstance ai = mob.getAttribute(Attributes.FOLLOW_RANGE);
            boolean improvedPathfinding = ai != null && ai.getBaseValue() == FOLLOW_RANGE_IMPROVED;
            Path bestPath;
            if (improvedPathfinding) {
                ai.setBaseValue(FOLLOW_RANGE);
                Path shortPath = mob.getNavigation().createPath(moveTarget.getX(), moveTarget.getY(), moveTarget.getZ(), moveReachRange);
                BlockPos shortFinalPos = getFinalNodePos(shortPath);
                ai.setBaseValue(FOLLOW_RANGE_IMPROVED);
                if (shortFinalPos != null && shortFinalPos.equals(moveTarget)) {
                    bestPath = shortPath;
                } else {
                    Path longPath = mob.getNavigation().createPath(moveTarget.getX(), moveTarget.getY(), moveTarget.getZ(), moveReachRange);
                    BlockPos longFinalPos = getFinalNodePos(longPath);
                    bestPath = longPath;
                    if (shortFinalPos != null && longFinalPos != null) {
                        BlockPos moveTargetXZ = new BlockPos(moveTarget.getX(), 0, moveTarget.getZ());
                        double shortXZDist = new BlockPos(shortFinalPos.getX(), 0, shortFinalPos.getZ()).distSqr(moveTargetXZ);
                        double longXZDist = new BlockPos(longFinalPos.getX(), 0, longFinalPos.getZ()).distSqr(moveTargetXZ);
                        if (shortXZDist < longXZDist) bestPath = shortPath;
                    }
                }
            } else {
                bestPath = mob.getNavigation().createPath(moveTarget.getX(), moveTarget.getY(), moveTarget.getZ(), moveReachRange);
            }
            this.mob.getNavigation().moveTo(bestPath, Unit.getSpeedModifier((Unit) this.mob));
            if (RtsDebug.enabled) {
                byte type = (bestPath != null && !bestPath.canReach()) ? RtsPathfinder.TYPE_FAILED : RtsPathfinder.TYPE_VANILLA;
                UnitPathClientboundPacket.sendPath(this.mob, bestPath, type);
            }
        }
    }

    protected void onPathReady(@Nullable Path path) {
        pathPending = false;
        if (moveTarget == null) return;
        if (path == null) { this.mob.getNavigation().stop(); return; }
        if (this.mob instanceof Unit u) {
            this.mob.getNavigation().moveTo(path, Unit.getSpeedModifier(u));
            if (RtsDebug.enabled) {
                byte type = path.canReach() ? RtsPathfinder.TYPE_ASTAR : RtsPathfinder.TYPE_FAILED;
                UnitPathClientboundPacket.sendPath(this.mob, path, type);
            }
        }
    }

    public void setMoveTarget(@Nullable BlockPos bp) {
        if (bp != null) MiscUtil.addUnitCheckpoint((Unit) mob, bp, true);
        this.moveTarget = bp;
        resetRecalcBackoff();
        if (!this.mob.level().isClientSide()) this.start();
    }

    public BlockPos getMoveTarget() { return this.moveTarget; }

    @Nullable public BlockPos getFinalNodePos() {
        Path path = this.mob.getNavigation().getPath();
        if (path != null && !path.nodes.isEmpty())
            return path.nodes.get(path.nodes.size() - 1).asBlockPos();
        return null;
    }

    @Nullable public BlockPos getFinalNodePos(Path path) {
        if (path != null && !path.nodes.isEmpty())
            return path.nodes.get(path.nodes.size() - 1).asBlockPos();
        return null;
    }

    public void stopMoving() {
        recalcCooldown = 0;
        this.moveTarget = null;
        this.mob.getNavigation().stop();
        if (this.mob.isVehicle() && this.mob.getPassengers().get(0) instanceof Unit unit)
            unit.getMoveGoal().stopMoving();
    }
}
