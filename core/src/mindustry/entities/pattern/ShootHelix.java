package mindustry.entities.pattern;

import arc.math.*;
import arc.util.*;

public class ShootHelix extends ShootPattern{
    public float scl = 2f, mag = 1.5f, offset = Mathf.PI * 1.25f, time = -1f, offsetScaleRandMax = 1f, offsetScaleRandMin = 1f;
    /** Exponential smoothing rate at which this pattern will no longer take effect, if time > 0 */
    public float approachTime = 10f;

    public ShootHelix(float scl, float mag){
        this.scl = scl;
        this.mag = mag;
    }

    public ShootHelix(float scl, float mag, float offset){
        this.scl = scl;
        this.mag = mag;
        this.offset = offset;
    }

    public ShootHelix(){
    }

    @Override
    public void shoot(int totalShots, BulletHandler handler, @Nullable Runnable barrelIncrementer){
        for(int i = 0; i < shots; i++){
            float off = (offsetScaleRandMin != 1f || offsetScaleRandMax != 1f) ? offset * Mathf.random(offsetScaleRandMin, offsetScaleRandMax) : offset;
            for(int sign : Mathf.signs){
                handler.shoot(0, 0, 0, firstShotDelay + shotDelay * i,
                    b -> b.moveRelative(0f, Mathf.sin(b.time + off, scl, mag * sign) * ((b.time < time || time < 0) ? 1f : 
                    Mathf.pow((1f - b.time / (b.type.lifetime + b.time)), approachTime))));
            }
        }
    }
} 
