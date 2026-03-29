package mindustry.world.blocks.defense;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

public class ShockMine extends Block{
    public final int timerDamage = timers++;

    /** How often this block checks for a unit above and attacks it. */
    public float cooldown = 80f;
    /** The time to activate of this block when initially placed. */
    public float activationTime = 0f;
    public float tileDamage = 5f;
    public float damage = 13;
    public int length = 10;
    public int tendrils = 6;
    public Color lightningColor = Pal.lancerLaser;
    public int shots = 6;
    public float inaccuracy = 0f;
    public @Nullable BulletType bullet;
    /** Both affected by activation timer progress. */
    public float teamAlpha = 0.3f, blockAlpha = 0.9f;
    /** Multiplier of activation time on alpha, from this value to 1f. */
    public float activationAlpha = 0.6f;
    /** Random activation effect delay */
    public float effectDelay = 15f;
    public Effect activationEffect = new Effect(25f, e -> {
        Draw.color(e.color, Color.lightGray, e.fin());

        Lines.stroke(1.2f * e.fout());
        Lines.circle(e.x, e.y, 3f + e.fin() * 10f);

        Angles.randLenVectors(e.id, 6, 10f * e.fin(), (x, y) -> {
            float px = Mathf.lerp(e.x + x, e.x, e.fin());
            float py = Mathf.lerp(e.y + y, e.y, e.fin());
            Fill.circle(px, py, 1.2f * e.fout());
        });

        Angles.randLenVectors(e.id + 1, 3, 6f * e.fin(), (x, y) -> {
            Fill.square(e.x + x, e.y + y, e.fslope() * 1.2f, 45f);
        });

        if(e.fin() > 0.85f){
            Draw.alpha((1f - e.fin()) * 5f);
            Fill.circle(e.x, e.y, 2.5f);
            Draw.alpha(1f);
        }
    });

    public @Load("@-team-top") TextureRegion teamRegion;

    public ShockMine(String name){
        super(name);
        update = true;
        destructible = true;
        solid = false;
        targetable = false;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.damage, table -> {
            table.add((String)(Core.bundle.format("bullet.lightning", tendrils, Strings.autoFixed(damage, 2)).replace("[stat]", "[white]")));
        });
        if(activationTime > 0) stats.add(Stat.activationTime, activationTime / 60f, StatUnit.seconds);
    }

    @Override
    public void setBars(){
        super.setBars();

        if(activationTime > 0){
            addBar("activationtimer", (ShockMineBuild b) ->
            new Bar(() ->
            (b.activationTimer > 0)? Core.bundle.format("bar.activationtimer", Mathf.ceil(b.activationTimer / 60f)) : Core.bundle.get("bar.activated"),
            () -> (b.activationTimer > 0)?  Pal.lightOrange : Pal.techBlue,
            () -> 1 - b.activationTimer / activationTime));
        }
    }

    @Override
    public void drawBase(Tile tile){
        if(tile.build instanceof ShockMineBuild b && !b.activated){
            Draw.alpha(blockAlpha * b.alpha);
            Draw.rect(region, b.x, b.y);
        }
        super.drawBase(tile);
    }

    public class ShockMineBuild extends Building{
        public float activationTimer = 0f;
        public float alpha = activationTime > 0 ? activationAlpha : 1f;
        public boolean activated = false;

        @Override
        public void updateTile(){
            if(activationTimer > 0){
                activationTimer -= Time.delta;
                alpha = Mathf.lerp(activationAlpha, 1f, 1f - activationTimer / activationTime);
            }else if(!activated){
                activated = true;
                alpha = 1f;
                Time.run(Mathf.random(effectDelay), () -> activationEffect.at(x, y, 1.5f, team.color));
            }
        }

        @Override
        public BlockStatus status(){
            return (activationTimer <= 0) ? super.status() : BlockStatus.inactive;
        }

        @Override
        public void placed(){
            super.placed();
            activationTimer = activationTime;
        }

        @Override
        public void drawTeam(){
            //no
        }

        @Override
        public void draw(){
            super.draw();
            Draw.color(team.color, teamAlpha * alpha);
            Draw.rect(teamRegion, x, y);
            Draw.color();
        }

        @Override
        public void drawCracks(){
            //no
        }

        @Override
        public void unitOn(Unit unit){
            if(enabled && unit.team != team && timer(timerDamage, cooldown / timeScale) && activationTimer <= 0){
                triggered();
                damage(tileDamage);
            }
        }

        public void triggered(){
            for(int i = 0; i < tendrils; i++){
                Lightning.create(Bullets.damageLightningGround, team, lightningColor, damage, x, y, Mathf.random(360f), length);
            }
            if(bullet != null){
                for(int i = 0; i < shots; i++){
                    bullet.create(this, x, y, (360f / shots) * i + Mathf.random(inaccuracy));
                }
            }
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(activated);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                activated = read.bool();
            }
        }
    }
}
