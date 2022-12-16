package dev.Watami.modules.implementations.movement;

import dev.Watami.Watami;
import dev.Watami.events.api.annotations.Listener;
import dev.Watami.events.api.annotations.Priority;
import dev.Watami.events.network.PacketReceiveEvent;
import dev.Watami.events.player.MoveEntityEvent;
import dev.Watami.events.player.UpdatePositionEvent;
import dev.Watami.modules.Module;
import dev.Watami.modules.ModuleCategory;
import dev.Watami.modules.ModuleInfo;
import dev.Watami.modules.ModuleManager;
import dev.Watami.modules.implementations.other.GameSpeed;
import dev.Watami.property.Property;
import dev.Watami.property.things.DoubleProperty;
import dev.Watami.property.things.EnumProperty;
import dev.Watami.utils.Wrapper;
import dev.Watami.utils.math.MathUtils;
import dev.Watami.utils.movement.MovementUtils;
import kotlin.Unit;
import kotlin.ranges.RangesKt;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(label = "Speed", key = Keyboard.KEY_X, category = ModuleCategory.MOVEMENT, subCategory = ModuleCategory.SubCategory.MOVEMENT_EXTRAS)
public final class Speed extends Module {
    private final EnumProperty<SpeedMode> speedModeProperty = new EnumProperty<>("Mode", SpeedMode.WATCHDOG);
    private final Property<Boolean> timerValue = new Property<>("Timer", true, () -> speedModeProperty.get() == SpeedMode.WATCHDOG);
    private final DoubleProperty chocoSpeed = new DoubleProperty("Choco Speed", 0.475, () -> speedModeProperty.get() == SpeedMode.CHOCO, 0.3, 0.6, 5.0E-4);
    private final DoubleProperty watchdogSpeed = new DoubleProperty("Watchdog Speed", 0.475, () -> speedModeProperty.get() == SpeedMode.WATCHDOG, 0.475, 1.3, 5.0E-4);
    private final DoubleProperty tacoSpeed = new DoubleProperty("Taco Speed", 0.475, () -> speedModeProperty.get() == SpeedMode.TACO, 0.475, 1.3, 5.0E-4);
    private final DoubleProperty timerAmountValue = new DoubleProperty("Timer Amount", 1.6, () -> speedModeProperty.get() == SpeedMode.WATCHDOG, 0.9, 1.6, 0.05);
    private final Property<Boolean> liquidCheck = new Property<>("Liquid Check", false, () -> speedModeProperty.get() == SpeedMode.WATCHDOG || speedModeProperty.get() == SpeedMode.CHOCO);
    private final DoubleProperty motionYValue = new DoubleProperty("Motion Y", 0.4, () -> speedModeProperty.get() == SpeedMode.TACO, 0.0, 0.42, 0.01);
    private final EnumProperty<HopType> hopType = new EnumProperty<>("Hop Type", HopType.Normal, () -> speedModeProperty.get() == SpeedMode.TACO);
    private final EnumProperty<BoostMode> boostModeValue = new EnumProperty<>("Boost Mode", BoostMode.Yport, () -> speedModeProperty.get() == SpeedMode.TACO);
    private final DoubleProperty limitSpeedValue = new DoubleProperty("Limit Speed", 0.82, () -> speedModeProperty.get() == SpeedMode.TACO, 0.66, 0.85, 0.01);
    private final DoubleProperty strafeValue = new DoubleProperty("Strafe", 159.0, () -> speedModeProperty.get() == SpeedMode.TACO, 33.0, 159.0, 1.0);
    private final DoubleProperty downStrafeValue = new DoubleProperty("Down Strafe", 159.0, () -> speedModeProperty.get() == SpeedMode.TACO, 33.0, 159.0, 1.0);
    private final Property<Boolean> stepCheck = new Property<Boolean>("Step Check", false);

    private final EnumProperty<Bypass> bypass = new EnumProperty<>("Bypass", Bypass.BypassOffset, () -> speedModeProperty.get() == SpeedMode.TACO);
    private final EnumProperty<Timer> timerMode = new EnumProperty<>("Timer", Timer.None, () -> speedModeProperty.get() == SpeedMode.TACO);
    private final Property<Boolean> lagBackCheckValue = new Property<>("Lag Back Check", true);
    private final Property<Boolean> noRotateSetValue = new Property<>("No Rotate Set", false);
    private final DoubleProperty stopTicksValue = new DoubleProperty("Stop Ticks", 16.0, 3.0, 25.0, 1.0);
    private final DoubleProperty normalTimerValue = new DoubleProperty("Normal Timer", 2.6, () -> speedModeProperty.get() == SpeedMode.TACO, 0.25, 5.0, 0.01);
    private final DoubleProperty groundTimerValue = new DoubleProperty("Ground Timer", 1.2, () -> speedModeProperty.get() == SpeedMode.TACO, 0.25, 5.0, 0.01);

    private final DoubleProperty fallDistValue = new DoubleProperty("Fall Dist", 255.0, () -> speedModeProperty.get() == SpeedMode.TACO, 0.0, 255.0, 1.0);

    private final DoubleProperty fallTimerValue = new DoubleProperty("Fall Timer", 0.98, () -> speedModeProperty.get() == SpeedMode.TACO, 0.25, 5.0, 0.01);

    private int stage;
    private double movementSpeed;
    private double lastDist;
    private int stopTicks;

    public EnumProperty<Timer> getTimerMode() {
        return this.timerMode;
    }

    public Property<Boolean> getStepCheck() {
        return stepCheck;
    }

    public int getStage() {
        return this.stage;
    }

    public void setStage(int n) {
        this.stage = n;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.stopTicks = 0;
        mc.timer.timerSpeed = 1.0f;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.thePlayer == null) {
            return;
        }
        this.stopTicks = 0;
        mc.timer.timerSpeed = 1.0f;
        mc.thePlayer.motionX *= 0.25;
        mc.thePlayer.motionZ *= 0.25;
    }

    @Listener
    public void onUpdatePosition(UpdatePositionEvent event) {
        // for Step Check
    }

    @Listener
    public void proGaming(UpdatePositionEvent event) {
        if (this.stopTicks > 0) {
            this.stopTicks = stopTicks + (-1);
            return;
        }
        if (this.speedModeProperty.isSelected(SpeedMode.TACO)) {
            if (MovementUtils.isMoving()) {
                if (mc.thePlayer.onGround) {
                    MovementUtils.fakeJump();
                }
                EntityPlayerSP entityPlayerSP = mc.thePlayer;
                entityPlayerSP.setSprinting(true);
            }
            if (this.hopType.get() == HopType.Lower && !mc.thePlayer.isCollidedHorizontally) {
                if (MathUtils.round(mc.thePlayer.posY - (double) ((int) mc.thePlayer.posY), 3.0) == MathUtils.round(0.753, 3.0)) {
                    mc.thePlayer.motionY -= 0.01;
                } else if (MathUtils.round(mc.thePlayer.posY - (double) ((int) mc.thePlayer.posY), 3.0) == MathUtils.round(0.991, 3.0)) {
                    mc.thePlayer.motionY -= 0.02;
                } else if (MathUtils.round(mc.thePlayer.posY - (double) ((int) mc.thePlayer.posY), 3.0) == MathUtils.round(0.136, 3.0)) {
                    mc.thePlayer.motionY -= 0.01;
                } else if (MathUtils.round(mc.thePlayer.posY - (double) ((int) mc.thePlayer.posY), 3.0) == MathUtils.round(0.19, 3.0)) {
                    mc.thePlayer.motionY -= 0.02;
                } else if (MathUtils.round(mc.thePlayer.posY - (double) ((int) mc.thePlayer.posY), 3.0) == MathUtils.round(0.902, 3.0)) {
                    mc.thePlayer.motionY -= 0.01;
                }
            }
            if (this.hopType.get() == HopType.Higher && (double) mc.thePlayer.fallDistance <= 1.4) {
                mc.thePlayer.motionY += 0.005574432;
            }
            GameSpeed gameSpeed = ModuleManager.getInstance(GameSpeed.class);
            if (!gameSpeed.isEnabled() && this.timerMode.get() != Timer.None && MovementUtils.isMoving())
                if (mc.thePlayer.fallDistance > 0.0D) {
                    if (mc.thePlayer.fallDistance > ((Number) this.fallDistValue.get()).doubleValue()) {
                        mc.timer.timerSpeed = (float) ((Number) this.normalTimerValue.get()).doubleValue();
                    } else {
                        mc.timer.timerSpeed = (float) ((Number) this.fallTimerValue.get()).doubleValue();
                    }
                } else if (mc.thePlayer.onGround) {
                    mc.timer.timerSpeed = (float) ((Number) this.groundTimerValue.get()).doubleValue();
                } else {
                    mc.timer.timerSpeed = (float) ((Number) this.normalTimerValue.get()).doubleValue();
                }
        }
    }

    @Listener
    public final void onWhenYouSeeIt(UpdatePositionEvent event) {
        if (this.stopTicks > 0) {
            return;
        }
        if (this.speedModeProperty.isSelected(SpeedMode.TACO)) {
            if (this.bypass.get() == Bypass.SpoofGround && MovementUtils.isMoving() && mc.thePlayer.onGround) {
                event.setPosY(event.getPosY() + ThreadLocalRandom.current().nextDouble() / 1000.0);
                event.setOnGround(true);
            }
            if (this.bypass.get() == Bypass.BypassOffset) {
                MovementUtils.bypassOffSet(event);
            }
        }
    }

    @Listener
    private final void chocoPie(UpdatePositionEvent e) {
        EntityPlayerSP entityPlayerSP = mc.thePlayer;
        if (entityPlayerSP.isSneaking() || this.stopTicks > 0) {
            return;
        }
        if (this.speedModeProperty.isSelected(SpeedMode.TACO) && MovementUtils.isMoving()) {
            EntityPlayerSP entityPlayerSP2 = mc.thePlayer;
            entityPlayerSP2.setSprinting(true);
        }
    }

    @Listener
    public final void onPreUpdate(UpdatePositionEvent event) {
        if ((SpeedMode) ((Object) this.speedModeProperty.get()) == SpeedMode.TACO) {
            double xDist = mc.thePlayer.posX - mc.thePlayer.prevPosX;
            double zDist = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
            double d = xDist * xDist + zDist * zDist;
            boolean bl = false;
            this.lastDist = Math.sqrt(d);
        }
    }

    @Listener
    private void onMove(MoveEntityEvent e) {
        block3:
        {
            block2:
            {
                EntityPlayerSP entityPlayerSP = mc.thePlayer;
                if (entityPlayerSP.isSneaking() || this.stopTicks > 0) break block2;
                Flight flight = Watami.getInstance().getModuleManager().getModule(Flight.class);
                if (!flight.isEnabled()) break block3;
            }
            return;
        }
    }

    @Listener
    public final void onPacket(PacketReceiveEvent event) {
        if (mc.thePlayer == null) {
            return;
        }
        if (this.lagBackCheckValue.get() && event.getPacket() instanceof S08PacketPlayerPosLook) {
            mc.thePlayer.motionX *= 0.0;
            mc.thePlayer.motionY *= 0.0;
            mc.thePlayer.motionZ *= 0.0;
            this.stopTicks = (int) this.stopTicksValue.get().doubleValue();
            if (this.noRotateSetValue.get()) {
                Packet<?> packet = event.getPacket();
                if (packet == null) {
                }
                S08PacketPlayerPosLook s08PacketPlayerPosLook = (S08PacketPlayerPosLook) packet;
            }
        }
    }

    @Listener(value = Priority.LOW)
    private void onMoveEntityEvent(MoveEntityEvent e) {
        double baseSpeed = MovementUtils.getBaseMoveSpeed(0.2873D);
        if (this.speedModeProperty.get() == SpeedMode.CHOCO) {
            BlockPos blockPos = new BlockPos((Wrapper.getPlayer()).posX, (Wrapper.getPlayer()).posY, (Wrapper.getPlayer()).posZ);
            if (MovementUtils.isMoving() && (this.liquidCheck.get()) && !Wrapper.getPlayer().isInWater() && !Wrapper.getPlayer().isInLava()) {
                if (!(ModuleManager.getInstance(GameSpeed.class)).isEnabled())
                    mc.timer.timerSpeed = (float)((Number)this.timerAmountValue.get());
                if (Wrapper.getWorld().getBlockState(blockPos).getBlock() instanceof net.minecraft.block.BlockStairs) {
                    MovementUtils.setMotion(MovementUtils.getBaseMoveSpeed(0.2873D));
                } else if ((Wrapper.getPlayer()).onGround) {
                    mc.thePlayer.motionY = MovementUtils.getJumpBoostModifier(0.39999998D, true);
                    MovementUtils.setMotion(RangesKt.coerceAtLeast((this.chocoSpeed.get().doubleValue() + MovementUtils.getSpeedEffect() * 0.1D) * (ModuleManager.getInstance(Scaffold.class).isEnabled() ? 0.66D : 1.0D), baseSpeed));
                } else {
                    MovementUtils.setMotion(MovementUtils.getSpeed());
                }
            }
        } else {
            if (this.speedModeProperty.get() == SpeedMode.WATCHDOG) {
                if (mc.thePlayer != null) {
                    EntityPlayerSP player = mc.thePlayer;
                    if (MovementUtils.isMoving() && player.isCollidedHorizontally)
                        MovementUtils.setMotio(e, MovementUtils.getBaseMoveSpeed(0.258D));
                    return;
                }
                return;
            }
            if ((SpeedMode)this.speedModeProperty.get() == SpeedMode.TACO) {
                if (mc.thePlayer != null) {
                    Scaffold scaffoldModule = (Scaffold)Watami.INSTANCE.getModuleManager().getModule(Scaffold.class);
                    double normalSpeed = this.tacoSpeed.get() * MovementUtils.getBaseMoveSpeed();
                    double bhopSpeed = this.tacoSpeed.get() * MovementUtils.getBaseMoveSpeed();
                    double lowhopSpeed = this.tacoSpeed.get() * MovementUtils.getBaseMoveSpeed();
                    double yportSpeed = this.tacoSpeed.get() * MovementUtils.getBaseMoveSpeed();
                    boolean slowDown = (mc.thePlayer.fallDistance > 0.0D);
                    if (MovementUtils.isMoving()) {
                        if (this.stepCheck.get()) {
                            mc.thePlayer.stepHeight = 0.6F;
                        } else {
                            mc.thePlayer.stepHeight = 1.0F;
                        }
                        switch (this.stage) {
                            case 2:
                                if (mc.thePlayer.onGround) {
                                    double d2;
                                    boolean bool1, bool2;
                                    double d3, it;
                                    int pray, pray2, pray3;
                                    BoostMode boostMode;
                                    Unit unit;
                                    if ((BoostMode)this.boostModeValue.get() == null) {
                                        this.boostModeValue.get();
                                    }
                                    switch (this.boostModeValue.get().ordinal()) {
                                        case 1:
                                            d2 = MovementUtils.getJumpBoostModifier(0.39999998D, true);
                                            bool1 = false;
                                            bool2 = false;
                                            d3 = d2;
                                            boostMode = (BoostMode)this.boostModeValue.get();
                                            pray = 0;
                                            mc.thePlayer.motionY = d3;
                                            unit = Unit.INSTANCE;
                                        case 2:
                                            d2 = MovementUtils.getJumpBoostModifier(0.16D, false);
                                            bool1 = false;
                                            bool2 = false;
                                            it = d2;
                                            boostMode = (BoostMode)this.boostModeValue.get();
                                            pray2 = 0;
                                            mc.thePlayer.motionY = it;
                                            unit = Unit.INSTANCE;
                                        case 3:

                                        default:
                                            d2 = MovementUtils.getJumpBoostModifier(this.motionYValue.get(), true);
                                            bool1 = false;
                                            bool2 = false;
                                            it = d2;
                                            boostMode = (BoostMode)this.boostModeValue.get();
                                            pray3 = 0;
                                            mc.thePlayer.motionY = it;
                                            unit = Unit.INSTANCE;
                                            break;
                                    }
                                    e.setY(d2);
                                }
                                if (this.boostModeValue.get() == null) {
                                    this.boostModeValue.get();
                                }
                                switch (this.boostModeValue.get().ordinal()) {
                                    case 1:
                                        if (MovementUtils.isOnIce());
                                        if (MovementUtils.isInLiquid());
                                        if (mc.thePlayer.isInLava());
                                    case 2:
                                        if (MovementUtils.isOnIce());
                                        if (MovementUtils.isInLiquid());
                                        if (mc.thePlayer.isInLava());
                                    case 3:
                                        if (MovementUtils.isOnIce());
                                        if (MovementUtils.isInLiquid());
                                        if (mc.thePlayer.isInLava());
                                    default:
                                        break;
                                }
                                movementSpeed = MovementUtils.isOnIce() ? (1.12D * normalSpeed) : (MovementUtils.isInLiquid() ? (0.5D * normalSpeed) : (mc.thePlayer.isInLava() ? (0.25D * normalSpeed) : normalSpeed));
                                break;
                            case 3:
                                double difference = this.limitSpeedValue.get() * (this.lastDist - MovementUtils.getBaseMoveSpeed());
                                this.movementSpeed = this.lastDist - difference;
                                if ((Timer)this.timerMode.get() == Timer.None)
                                    mc.timer.timerSpeed = 1.07F;
                                break;
                            default:
                                if (MovementUtils.isOnGround(-mc.thePlayer.motionY) || (mc.thePlayer.isCollidedVertically && mc.thePlayer.onGround))
                                    this.stage = 1;
                                this.movementSpeed = this.lastDist - this.lastDist / (slowDown ? this.downStrafeValue.get() : this.strafeValue.get());
                                break;
                        }
                        double difference = this.movementSpeed, d1 = MovementUtils.getBaseMoveSpeed();
                        this.movementSpeed = Math.max(difference, d1);
                        MovementUtils.setMotion(e, scaffoldModule.isEnabled() ? (this.movementSpeed * 0.5D) : this.movementSpeed, 1.0D);
                        this.stage++;
                    }
                    return;
                }
                return;
            }
        }
    }

    @Listener
    private void onUpdate(UpdatePositionEvent e) {
        if (this.speedModeProperty.get() == SpeedMode.WATCHDOG) {
            EntityPlayerSP entityPlayerSP = mc.thePlayer;
            if (entityPlayerSP == null) {
                return;
            }
            EntityPlayerSP player = entityPlayerSP;
            Scaffold scaffoldModule = ModuleManager.getInstance(Scaffold.class);
            GameSpeed timer = ModuleManager.getInstance(GameSpeed.class);
            if (MovementUtils.isMoving()) {
                if (player.onGround) {
                    player.motionY = MovementUtils.getJumpBoostModifier(0.39999998, true);
                    double d = this.watchdogSpeed.get() + (double) MovementUtils.getSpeedEffect() * 0.1;
                    Scaffold scaffold = scaffoldModule;
                    double d2 = d * (scaffold.isEnabled() ? 0.66 : 1.0);
                    double d3 = MovementUtils.getBaseMoveSpeed(0.2873);
                    MovementUtils.setMotion(Math.max(d2, d3));
                } else {
                    GameSpeed gameSpeed = timer;
                    if (!gameSpeed.isEnabled()) {
                        mc.timer.timerSpeed = 1.07f;
                    } else {
                        Boolean bl = this.timerValue.get();
                        if (bl) {
                            mc.timer.timerSpeed = (float) ((Number) this.timerAmountValue.get());
                        }
                    }
                    MovementUtils.setMotion(MovementUtils.getSpeed());
                }
            } else {
                player.motionX *= 0.0;
                player.motionZ *= 0.0;
            }
        }
    }

    public Speed() {
        this.setSuffixListener(this.speedModeProperty);
    }

    public enum SpeedMode {
        WATCHDOG,
        TACO,
        CHOCO
    }

    public enum HopType {
        Normal,
        Lower,
        Higher
    }

    public enum BoostMode {
        Normal,
        Bhop,
        LowHop,
        Yport
    }

    public enum Bypass {
        SpoofGround,
        BypassOffset,
        None
    }

    public enum Timer {
        Custom,
        Stage,
        None
    }
}