package mindustry.world.blocks.defense.turrets;

import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.meta.*;
import mindustry.world.consumers.*;

import static mindustry.Vars.*;

public class TractorBeamTurret extends BaseTurret{
    public final int timerTarget = timers++;
    public float scaledTimer;
    public float retargetTime = 5f;

    public float shootCone = 6f;
    public float shootLength = 5f;
    public float laserWidth = 0.6f;
    public float force = 0.3f;
    /** Maximum force multiplier */
    public float scaledForce = 0f;
    /** Time to reach the force multiplier in ticks */
    public float scaledTime = 60f * 20f;
    public float damage = 0f;
    public boolean targetAir = true, targetGround = false;
    public Color laserColor = Color.white;
    /** Both status applied as long as debuffStatus is not present */
    public StatusEffect mainStatus = StatusEffects.none, debuffStatus = StatusEffects.none;
    public float mainStatusDur = 60f * 3f, debuffStatusDur = 60f * 50f;

    public Sound shootSound = Sounds.beamParallax;
    public float shootSoundVolume = 0.9f;

    public @Load(value = "@-base", fallback = "block-@size") TextureRegion baseRegion;
    public @Load("@-laser") TextureRegion laser;
    public @Load(value = "@-laser-start", fallback = "@-laser-end") TextureRegion laserStart;
    public @Load("@-laser-end") TextureRegion laserEnd;

    public TractorBeamTurret(String name){
        super(name);

        rotateSpeed = 10f;
        coolantMultiplier = 1f;
        envEnabled |= Env.space;
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{baseRegion, region};
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(Stat.targetsAir, targetAir);
        stats.add(Stat.targetsGround, targetGround);
        if(damage > 0) stats.add(Stat.damage, damage * 60f, StatUnit.perSecond);
        if(scaledForce > 0) stats.add(Stat.pullMultiplier, td -> {
            td.add(Core.bundle.format("bar.pullmultiplier", Strings.autoFixed(scaledForce, 2), Strings.autoFixed(scaledTime / 60f, 2)));
        });
    }

    @Override
    public void init(){
        super.init();

        updateClipRadius(range + tilesize);
    }

    public class TractorBeamBuild extends BaseTurretBuild{
        public @Nullable Unit target;
        public float lastX, lastY, strength;
        public boolean any;
        public float coolantBoost = 1f;

        @Override
        public void updateTile(){
            if(activationTimer > 0){
                activationTimer -= Time.delta;
                return;
            }

            coolantBoost = 1f;

            //consume coolant
            if(target != null && coolant != null && coolant.efficiency(this) > 0f && efficiency > 0.02f){
                Liquid liquid = coolant instanceof ConsumeLiquidFilter filter ? filter.getConsumed(this) : liquids.current();
                float capacity = liquid == null ? 0.4f : liquid.heatCapacity;
                float amount = coolant.amount * coolant.efficiency(this);
                coolant.update(this);
                coolantBoost = 1f + amount * capacity * coolantMultiplier;

                if(Mathf.chance(0.06f * amount)){
                    coolEffect.at(x + Mathf.range(size * tilesize / 2f), y + Mathf.range(size * tilesize / 2f));
                }
            }

            float eff = efficiency * coolantBoost, edelta = eff * delta();

            //retarget
            if(timer(timerTarget, retargetTime)){
                target = Units.closestEnemy(team, x, y, range, u -> u.checkTarget(targetAir, targetGround));
            }

            any = false;

            //look at target
            if(target != null && target.within(this, range + target.hitSize/2f) && target.team() != team && target.checkTarget(targetAir, targetGround) && efficiency > 0.02f){
                if(!headless){
                    control.sound.loop(shootSound, this, shootSoundVolume);
                }

                float dest = angleTo(target);
                rotation = Angles.moveToward(rotation, dest, rotateSpeed * edelta);
                lastX = target.x;
                lastY = target.y;
                strength = Mathf.lerpDelta(strength, 1f, 0.1f);

                //shoot when possible
                if(Angles.within(rotation, dest, shootCone)){
                    if(damage > 0){
                        target.damageContinuous(damage * eff * timeScale * state.rules.blockDamage(team));
                    }

                    if(mainStatus != StatusEffects.none && !target.hasEffect(debuffStatus)){
                        target.apply(mainStatus, mainStatusDur);
                        if(debuffStatus != StatusEffects.none) target.apply(debuffStatus, debuffStatusDur);
                    }

                    any = true;
                    target.impulseNet(Tmp.v1.set(this).sub(target).limit((force + (1f - target.dst(this) / range) * scaledForce) * coolantBoost * edelta * (scaledTimer / scaledTime)));
                    if(scaledTime > 0) scaledTimer = scaledTimer > scaledTime ? 0f : scaledTimer + Time.delta;
                }
            }else{
                strength = Mathf.lerpDelta(strength, 0, 0.1f);
            }
        }

        @Override
        public boolean shouldConsume(){
            return super.shouldConsume() && target != null;
        }

        @Override
        public float estimateDps(){
            if(!any || damage <= 0) return 0f;
            return damage * 60f * efficiency * coolantBoost;
        }

        @Override
        public void draw(){
            Draw.rect(baseRegion, x, y);
            Drawf.shadow(region, x - (size / 2f), y - (size / 2f), rotation - 90);
            Draw.rect(region, x, y, rotation - 90);

            //draw laser if applicable
            if(any && !isPayload()){
                Draw.z(Layer.bullet);
                float ang = angleTo(lastX, lastY);

                Draw.mixcol(laserColor, Mathf.absin(4f, 0.6f));

                Drawf.laser(laser, laserStart, laserEnd,
                x + Angles.trnsx(ang, shootLength), y + Angles.trnsy(ang, shootLength),
                lastX, lastY, strength * efficiency * laserWidth);

                Draw.mixcol();
            }
        }

        @Override
        public void write(Writes write){
            super.write(write);

            write.f(rotation);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            rotation = read.f();
        }
    }
}
