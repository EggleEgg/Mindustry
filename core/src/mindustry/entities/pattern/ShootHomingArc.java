package mindustry.entities.pattern;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.gen.*;

public class ShootHomingArc extends ShootPattern{
    /** Radius of the arc trajectory this bullet will follow upon finding a new target. */
    public float arcRadius = 60f * 3f;
    /** Angle of the arc trajectory this bullet will follow upon finding a new target. */
    public float arcAngleMin = 180f, arcAngleMax = 180f;
    public float arcDelay = -1f;
    public float homingMargin = 8f;

    public ShootHomingArc(){
    }

    @Override
    public void shoot(int totalShots, BulletHandler handler, @Nullable Runnable barrelIncrementer){
        for(int i = 0; i < shots; i++){
            handler.shoot(0, 0, 0, firstShotDelay + shotDelay * i, b -> {
                if(b.time < arcDelay) return;

                float arcAngle = Mathf.randomSeed(b.id, arcAngleMin, arcAngleMax);
                if(b.arcTarget == null) b.arcTarget = b.homingTarget(arcRadius * 2f + b.vel.len());
                if(b.arcTarget != null){
                    //prediction fails when a target is right next to the bullet
                    Vec2 predict = Predict.intercept(b, b.arcTarget, b.dst(b.arcTarget) > arcRadius + homingMargin, b.vel.len());
                    b.aimX = predict.x;
                    b.aimY = predict.y;

                    float side = Mathf.signs[b.id & 1], targetDst = b.dst(predict.x, predict.y);
                    float radius = Mathf.clamp(arcRadius, 0.001f, targetDst - homingMargin); //cap distance when up close
                    float rotSpeed = b.vel.len() * Time.delta / radius * Mathf.radDeg;

                    if(b.arcRemaining <= 0f && b.within(predict.x, predict.y, radius * (1f - Mathf.cosDeg(arcAngle)))){
                        b.arcRemaining = arcAngle;
                        //extend lifetime
                        if(b.type.speed > 0.001f) b.lifetime += Math.max(0f, radius * arcAngle * Mathf.degRad - targetDst) / b.type.speed;
                        //turn perpendicular
                        b.vel.setAngle(b.angleTo(predict.x, predict.y) - side * 90f + side * rotSpeed / 2f);
                    }
                    if(b.arcRemaining > 0f){
                        float step = Math.min(rotSpeed, b.arcRemaining);

                        b.vel.rotate(side * step);
                        b.arcRemaining -= step;
                        if(b.arcRemaining <= 0f){
                            //instant rotation
                            float angle = Angles.angle(b.x, b.y, predict.x, predict.y);
                            b.rotation(angle);
                            b.vel.setAngle(angle);
                        }
                    }
                }
            });
        }
    }
}
