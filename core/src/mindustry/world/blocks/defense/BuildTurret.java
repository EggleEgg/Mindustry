package mindustry.world.blocks.defense;

import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.Teams.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

public class BuildTurret extends BaseTurret{
    public final int timerTarget = timers++, timerTarget2 = timers++;
    public int targetInterval = 15;

    public @Load(value = "@-base", fallback = "block-@size") TextureRegion baseRegion;
    public @Load("@-glow") TextureRegion glowRegion;
    public float buildSpeed = 1f;
    public float buildBeamOffset = 5f;
    public ObjectFloatMap<Liquid> liquidSpeedBoosts = new ObjectFloatMap<>();
    public ObjectFloatMap<Liquid> liquidRangeBoosts = new ObjectFloatMap<>();
    public float liquidBoostIntensity = 0f;
    public float liquidRangeBoost = 0f;
    /** Whether to multiply heat color on current liquid booster color. */
    public boolean switchHeatColor = true;

    //created in init()
    public @Nullable UnitType unitType;
    public Seq<ConsumeLiquidBase> liquidBoosters = new Seq<>();
    public float elevation = -1f;
    public Color heatColor = Pal.accent.cpy().a(0.9f);


    public BuildTurret(String name){
        super(name);
        group = BlockGroup.turrets;
        sync = false;
        rotateSpeed = 10f;
        suppressable = true;
    }

    @Override
    public void init(){
        super.init();

        if(elevation < 0) elevation = size / 2f;

        //this is super hacky, but since blocks are initialized before units it does not run into init/concurrent modification issues
        unitType = new UnitType("turret-unit-" + name){{
            hidden = true;
            internal = true;
            speed = 0f;
            hitSize = 0f;
            health = 1;
            itemCapacity = 0;
            rotateSpeed = BuildTurret.this.rotateSpeed;
            buildBeamOffset = BuildTurret.this.buildBeamOffset;
            buildRange = BuildTurret.this.range;
            buildSpeed = BuildTurret.this.buildSpeed;
            constructor = BlockUnitUnit::create;
        }};

        liquidBoosters.clear();
        for(Consume cons : consumers){
            if(cons instanceof ConsumeLiquidBase liq && cons.booster){
                liquidBoosters.add(liq);
            }
        }
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.addPercent(Stat.buildSpeed, buildSpeed);
        if(liquidBoosters.size > 0){
            stats.add(Stat.booster, table -> {
                table.row();
                table.table(c -> {
                    for(ConsumeLiquidBase cons : liquidBoosters){
                        Liquid liquid = cons instanceof ConsumeLiquid cl ? cl.liquid : null;
                        if(liquid == null) continue;
                        float speed = liquidSpeedBoosts.get(liquid, liquidBoostIntensity);
                        float range = liquidRangeBoosts.get(liquid, liquidRangeBoost);
                        if(speed == 0f && range == 0f) continue;
                        if(cons.amount <= 0f) continue;
                        float amountf = cons.amount;

                        c.table(Styles.grayPanel, b -> {
                            b.image(liquid.uiIcon).size(40).pad(10f).left().scaling(Scaling.fit).with(i -> StatValues.withTooltip(i, liquid, false));
                            b.table(info -> {
                                info.add(liquid.localizedName).left().row();
                                info.add(Strings.autoFixed(amountf * 60f, 2) + StatUnit.perSecond.localized()).left().color(Color.lightGray);
                            });

                            b.table(bt -> {
                                bt.right().defaults().padRight(3).left();
                                if(range != 0f) bt.add((range > 0f ? "[stat]+" : "[negstat]") + Strings.autoFixed(range / tilesize, 2) + "[lightgray] " + StatUnit.blocks.localized()).row();
                                if(speed != 0f) bt.add((speed > 0f ? "[stat]" : "[negstat]") + Strings.autoFixed(speed, 2) + "[lightgray]" + StatUnit.timesSpeed.localized());
                            }).right().grow().pad(10f).padRight(15f);
                        }).growX().pad(5).row();
                    }
                }).growX().colspan(table.getColumns());
                table.row();
            });
        }
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{baseRegion, region};
    }

    public class BuildTurretBuild extends BaseTurretBuild implements ControlBlock, RotBlock{
        public BlockUnitc unit = (BlockUnitc)unitType.create(team);
        public @Nullable Unit following;
        public @Nullable BlockPlan lastPlan;
        public float warmup;
        public float currBuildRange = BuildTurret.this.range;

        {
            unit.rotation(90f);
        }

        @Override
        public boolean canControl(){
            return true;
        }

        @Override
        public float buildRotation(){
            return unit.rotation();
        }

        @Override
        public Unit unit(){
            //make sure stats are correct
            unit.tile(this);
            unit.team(team);
            return (Unit)unit;
        }

        @Override
        public float range(){
            return currBuildRange;
        }

        public float boosterEfficiency(ConsumeLiquidBase cons, Liquid liq){
            if(liq == null || liquids == null) return 0f;

            float ed = delta() * potentialEfficiency * efficiencyScale();
            if(ed <= 0.0000001f) return 0f;
            float needed = cons.amount * ed * cons.multiplier.get(this);
            if(needed <= 0.0000001f) return 0f;

            return Math.min(liquids.get(liq) / needed, 1f);
        }

        @Override
        public void updateTile(){
            unit.tile(this);
            unit.team(team);

            //only cares about where the unit itself is looking
            rotation = unit.rotation();

            if(checkSuppression()){
                efficiency = potentialEfficiency = 0f;
            }

            float boostSpeed = 0f, boostRange = 0f;
            if(liquidBoosters.size > 0){
                for(ConsumeLiquidBase cons : liquidBoosters){
                    Liquid liq = cons instanceof ConsumeLiquid cl ? cl.liquid :
                        cons instanceof ConsumeLiquidFilter filter ? filter.getConsumed(this) : null;
                    if(liq == null) continue;
                    float eff = boosterEfficiency(cons, liq);
                    if(eff <= 0f) continue;
                    boostSpeed += eff * liquidSpeedBoosts.get(liq, liquidBoostIntensity);
                    boostRange += eff * liquidRangeBoosts.get(liq, liquidRangeBoost);
                }
            }

            float speedMult = (1f + boostSpeed) * potentialEfficiency * timeScale;
            float realRange = BuildTurret.this.range + boostRange;
            currBuildRange = realRange;

            unit.type().buildRange = realRange;
            unit.type().buildSpeed = buildSpeed * speedMult;

            if(unit.activelyBuilding()){
                unit.lookAt(angleTo(unit.buildPlan()));
            }

            unit.speedMultiplier(speedMult);

            warmup = Mathf.lerpDelta(warmup, unit.activelyBuilding() ? efficiency : 0f, 0.1f);

            if(!isControlled()){
                unit.updateBuilding(true);

                if(following != null){
                    //validate follower
                    if(!following.isValid() || !following.activelyBuilding()){
                        following = null;
                        unit.plans().clear();
                    }else{
                        //set to follower's first build plan, whatever that is
                        unit.plans().clear();
                        unit.plans().addFirst(following.buildPlan());
                        lastPlan = null;
                    }

                }else if(unit.buildPlan() == null && timer(timerTarget, targetInterval)){ //search for new stuff
                    Queue<BlockPlan> blocks = team.data().plans;
                    for(int i = 0; i < blocks.size; i++){
                        var block = blocks.get(i);
                        if(within(block.x * tilesize, block.y * tilesize, realRange)){
                            var btype = block.block;

                            if(Build.validPlace(btype, unit.team(), block.x, block.y, block.rotation) && (state.rules.infiniteResources || team.rules().infiniteResources || team.items().has(btype.requirements, state.rules.buildCostMultiplier))){
                                unit.addBuild(new BuildPlan(block.x, block.y, block.rotation, block.block, block.config));
                                //shift build plan to tail so next unit builds something else
                                blocks.addLast(blocks.removeIndex(i));
                                lastPlan = block;
                                break;
                            }
                        }
                    }

                    //still not building, find someone to mimic
                    if(unit.buildPlan() == null){
                        following = null;
                        Units.nearby(team, x, y, realRange, u -> {
                            if(following  != null) return;

                            if(u.canBuild() && u.activelyBuilding()){
                                BuildPlan plan = u.buildPlan();

                                Building build = world.build(plan.x, plan.y);
                                if(build instanceof ConstructBuild && within(build, realRange)){
                                    following = u;
                                }
                            }
                        });
                    }
                }else if(unit.buildPlan() != null){ //validate building
                    BuildPlan req = unit.buildPlan();

                    //clear break plan if another player is breaking something
                    if(!req.breaking && timer.get(timerTarget2, 30f)){
                        for(Player player : team.data().players){
                            if(player.isBuilder() && player.unit().activelyBuilding() && player.unit().buildPlan().samePos(req) && player.unit().buildPlan().breaking){
                                unit.plans().removeFirst();
                                //remove from list of plans
                                team.data().plans.remove(p -> p.x == req.x && p.y == req.y);
                                return;
                            }
                        }
                    }

                    boolean valid =
                        !(lastPlan != null && lastPlan.removed) &&
                        ((req.tile() != null && req.tile().build instanceof ConstructBuild cons && cons.current == req.block) ||
                        (req.breaking ?
                        Build.validBreak(unit.team(), req.x, req.y) :
                        Build.validPlace(req.block, unit.team(), req.x, req.y, req.rotation)));

                    if(!valid){
                        //discard invalid request
                        unit.plans().removeFirst();
                        lastPlan = null;
                    }
                }
            }else{ //is being controlled, forget everything
                following = null;
                lastPlan = null;
            }

            //please do not commit suicide
            unit.plans().remove(b -> b.build() == this);

            unit.updateBuildLogic();
        }

        @Override
        public boolean shouldConsume(){
            return unit.plans().size > 0 && !isHealSuppressed();
        }

        @Override
        public void draw(){

            Draw.rect(baseRegion, x, y);
            Draw.color();

            Draw.z(Layer.turret);

            Drawf.shadow(region, x - elevation, y - elevation, rotation - 90);
            Draw.rect(region, x, y, rotation - 90);

            if(glowRegion.found()){
                Drawf.additive(glowRegion, heatColor, warmup, x, y, rotation - 90f, Layer.turretHeat);
            }

            if(efficiency > 0){
                unit.drawBuilding();
            }
        }

        @Override
        public float warmup(){
            return warmup;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(rotation);
            //TODO queue can be very large due to logic?
            TypeIO.writePlans(write, unit.plans().toArray(BuildPlan.class));
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            rotation = read.f();
            unit.rotation(rotation);
            unit.plans().clear();
            var reqs = TypeIO.readPlans(read);
            if(reqs != null){
                for(var req : reqs){
                    unit.plans().add(req);
                }
            }
        }

        @Override
        public double sense(LAccess sensor){
            return switch(sensor){
                case buildX, buildY -> unit.sense(sensor);
                default -> super.sense(sensor);
            };
        }

        @Override
        public Object senseObject(LAccess sensor){
            return switch(sensor){
                case building, breaking -> unit.senseObject(sensor);
                default -> super.senseObject(sensor);
            };
        }
    }
}
