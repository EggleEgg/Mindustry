package mindustry.world.blocks.production;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;
import mindustry.world.modules.*;

public class Incinerator extends Block{
    public Effect effect = Fx.fuelburn;
    public Color flameColor = Color.valueOf("ffad9d");
    public @Nullable LiquidStack outputLiquid;
    public boolean burnLiquids;
    /** Color to fall back to if outputLiquid is null. */
    public Color effectColor = Pal.slagOrange;
    public float effectChance = 0.2f;
    public float itemMultiplier = 1f;
    public float liquidMultiplier = 1f;

    public @Load("@-liquid") TextureRegion liquidRegion;
    public @Load("@-top") TextureRegion topRegion;

    public Incinerator(String name){
        super(name);
        hasPower = true;
        hasLiquids = true;
        update = true;
        solid = true;
    }

    @Override
    public void setStats(Stats stats){
        super.setStats(stats);

        if(outputLiquid != null){
            stats.add(Stat.output, outputLiquid.liquid, outputLiquid.amount * 60, true);
        }
    }

    @Override
    public void init(){
        if(outputLiquid != null || burnLiquids){
            hasLiquids = true;
            outputsLiquid = outputLiquid != null;

            if(liquidCapacity <= 0) liquidCapacity = 30f;
        }

        super.init();
    }

    @Override
    public TextureRegion[] icons(){
        if(topRegion.found()){
            return new TextureRegion[]{region, topRegion};
        }

        return super.icons();
    }

    public boolean isRequiredLiquid(Liquid liquid){
        ConsumeLiquidBase cons = findConsumer(f -> f instanceof ConsumeLiquidBase);
        return cons != null && cons.consumes(liquid);
    }

    public class IncineratorBuild extends Building{
        public float heat;
        public int burnedItems;
        public float burnedLiquids;
        public float itemWeights, liquidWeights;

        @Override
        public void updateTile(){
            heat = Mathf.approachDelta(heat, efficiency, 0.04f);

            if(outputLiquid != null && liquids != null){
                dumpLiquid(outputLiquid.liquid);
            }
        }

        @Override
        public BlockStatus status(){
            if(!enabled) return BlockStatus.logicDisable;
            if(outputLiquid != null || burnLiquids){
                return efficiency > 0 ? BlockStatus.active : BlockStatus.noInput;
            }
            return heat > 0.5f ? BlockStatus.active : BlockStatus.noInput;
        }

        @Override
        public void draw(){
            super.draw();

            if(liquidRegion != null && liquidRegion.found() && liquids != null){
                Drawf.liquid(liquidRegion, x, y, liquids.currentAmount() / liquidCapacity, liquids.current().color);
            }
            if(topRegion != null && topRegion.found()){
                Draw.rect(topRegion, x, y);
            }

            if(outputLiquid == null && !burnLiquids){
                if(heat > 0f){
                    float g = 0.3f;
                    float r = 0.06f;

                    Draw.alpha(((1f - g) + Mathf.absin(Vars.state.time, 8f, g) + Mathf.random(r) - r) * heat);

                    Draw.tint(flameColor);
                    Fill.circle(x, y, 2f);
                    Draw.color(1f, 1f, 1f, heat);
                    Fill.circle(x, y, 1f);

                    Draw.color();
                }
            }
        }

        @Override
        public void handleItem(Building source, Item item){
            if(outputLiquid != null || burnLiquids){
                if(Mathf.chance(effectChance)){
                    Color color = outputLiquid != null ? outputLiquid.liquid.color : effectColor;
                    effect.wrap(color).at(x, y);
                }

                if(outputLiquid != null && liquids != null){
                    burnedItems++;
                    //some items might not have cost
                    itemWeights += item.cost > 0f ? item.cost : 1f;
                    if(burnedItems > 0){
                        float maxOutput = liquidCapacity - liquids.get(outputLiquid.liquid);
                        if(maxOutput > 0.0001f){
                            liquids.add(outputLiquid.liquid, Math.min(outputLiquid.amount * (itemWeights / burnedItems) * itemMultiplier, maxOutput));
                        }
                        itemWeights = burnedItems = 0;
                    }
                }
            }else{
                if(Mathf.chance(0.3)){
                    effect.at(x, y);
                }
            }
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(outputLiquid != null || burnLiquids){
                return efficiency > 0;
            }
            return heat > 0.5f && enabled;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            if(outputLiquid != null || burnLiquids){
                if(burnLiquids){
                    if(Mathf.chance(effectChance)){
                        Color color = liquid.gasColor != null ? liquid.gasColor :
                        (liquid.color != null && liquid.gas) ? liquid.color :
                        (outputLiquid != null) ? outputLiquid.liquid.color : effectColor;
                        effect.wrap(color).at(x, y);
                    }
                }

                //making slag out of water makes no sense
                if(outputLiquid != null && liquids != null && liquid.flammability > 0){
                    burnedLiquids += amount;
                    liquidWeights += liquid.flammability * amount;
                    if(burnedLiquids > (1f / 60f)){
                        float maxOutput = liquidCapacity - liquids.get(outputLiquid.liquid);
                        if(maxOutput > 0.0001f){
                            liquids.add(outputLiquid.liquid, Math.min(outputLiquid.amount * (liquidWeights / burnedLiquids) * liquidMultiplier, maxOutput));
                        }
                        liquidWeights = burnedLiquids = 0f;
                    }
                }
            }else{
                if(Mathf.chance(0.02)){
                    effect.at(x, y);
                }
            }
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            if(outputLiquid != null || burnLiquids){
                return burnLiquids && (isRequiredLiquid(liquid) || efficiency > 0);
            }
            return heat > 0.5f && liquid.incinerable && enabled;
        }
    }
}
